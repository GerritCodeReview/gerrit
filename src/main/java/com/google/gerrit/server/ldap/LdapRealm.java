// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.ldap;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

@Singleton
class LdapRealm implements Realm {
  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);
  private static final String LDAP = "com.sun.jndi.ldap.LdapCtxFactory";
  private static final String USERNAME = "username";

  private final String server;
  private final String username;
  private final String password;

  private final SchemaFactory<ReviewDb> schema;
  private final EmailExpander emailExpander;
  private final String accountFullName;
  private final String accountEmailAddress;
  private final String accountSshUserName;
  private final LdapQuery accountQuery;
  private final SelfPopulatingCache<String, Account.Id> usernameCache;

  private final GroupCache groupCache;
  private final String groupName;
  private boolean groupNeedsAccount;
  private final LdapQuery groupMemberQuery;
  private final SelfPopulatingCache<String, Set<AccountGroup.Id>> membershipCache;

  @Inject
  LdapRealm(
      final GroupCache groupCache,
      final EmailExpander emailExpander,
      final SchemaFactory<ReviewDb> schema,
      @Named(LdapModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.Id>> rawGroup,
      @Named(LdapModule.USERNAME_CACHE) final Cache<String, Account.Id> rawUsername,
      @GerritServerConfig final Config config) {
    this.groupCache = groupCache;
    this.emailExpander = emailExpander;
    this.schema = schema;

    this.server = required(config, "server");
    this.username = optional(config, "username");
    this.password = optional(config, "password");

    // Group query
    //
    final Set<String> groupAtts = new HashSet<String>();
    groupName = reqdef(config, "groupName", "cn");
    groupAtts.add(groupName);
    final String groupBase = required(config, "groupBase");
    final String groupMemberPattern =
        reqdef(config, "groupMemberPattern", "(memberUid=${username})");
    groupMemberQuery = new LdapQuery(groupBase, groupMemberPattern, groupAtts);
    if (groupMemberQuery.getParameters().length == 0) {
      throw new IllegalArgumentException(
          "No variables in ldap.groupMemberPattern");
    }

    membershipCache =
        new SelfPopulatingCache<String, Set<AccountGroup.Id>>(rawGroup) {
          @Override
          public Set<AccountGroup.Id> createEntry(final String username)
              throws Exception {
            return queryForGroups(username);
          }

          @Override
          protected Set<AccountGroup.Id> missing(final String key) {
            return Collections.emptySet();
          }
        };

    // Account query
    //
    final Set<String> accountAtts = new HashSet<String>();
    accountFullName = optdef(config, "accountFullName", "displayName");
    if (accountFullName != null) {
      accountAtts.add(accountFullName);
    }
    accountEmailAddress = optdef(config, "accountEmailAddress", "mail");
    if (accountEmailAddress != null) {
      accountAtts.add(accountEmailAddress);
    }
    accountSshUserName = optdef(config, "accountSshUserName", "uid");
    if (accountSshUserName != null) {
      accountAtts.add(accountSshUserName);
    }
    for (final String name : groupMemberQuery.getParameters()) {
      if (!USERNAME.equals(name)) {
        groupNeedsAccount = true;
        accountAtts.add(name);
      }
    }
    final String accountBase = required(config, "accountBase");
    final String accountPattern =
        reqdef(config, "accountPattern", "(uid=${username})");
    accountQuery = new LdapQuery(accountBase, accountPattern, accountAtts);
    if (accountQuery.getParameters().length == 0) {
      throw new IllegalArgumentException("No variables in ldap.accountPattern");
    }

    usernameCache = new SelfPopulatingCache<String, Account.Id>(rawUsername) {
      @Override
      public Account.Id createEntry(final String username) throws Exception {
        return queryForUsername(username);
      }
    };
  }

  private static String optional(final Config config, final String name) {
    return config.getString("ldap", null, name);
  }

  private static String required(final Config config, final String name) {
    final String v = optional(config, name);
    if (v == null || "".equals(v)) {
      throw new IllegalArgumentException("No ldap." + name + " configured");
    }
    return v;
  }

  private static String optdef(final Config c, final String n, final String d) {
    final String[] v = c.getStringList("ldap", null, n);
    if (v == null || v.length == 0) {
      return d;

    } else if (v[0] == null || "".equals(v[0])) {
      return null;

    } else {
      return v[0];
    }
  }

  private static String reqdef(final Config c, final String n, final String d) {
    final String v = optdef(c, n, d);
    if (v == null) {
      throw new IllegalArgumentException("No ldap." + n + " configured");
    }
    return v;
  }

  @Override
  public boolean allowsEdit(final Account.FieldName field) {
    switch (field) {
      case FULL_NAME:
        return accountFullName == null; // only if not obtained from LDAP

      case SSH_USER_NAME:
        return accountSshUserName == null; // only if not obtained from LDAP

      default:
        return true;
    }
  }

  public AuthRequest authenticate(final AuthRequest who)
      throws AccountException {
    final String username = who.getLocalUser();
    try {
      final DirContext ctx = open();
      try {
        final LdapQuery.Result m = findAccount(ctx, username);

        who.setDisplayName(m.get(accountFullName));
        who.setSshUserName(m.get(accountSshUserName));

        if (accountEmailAddress != null) {
          who.setEmailAddress(m.get(accountEmailAddress));

        } else if (emailExpander.canExpand(username)) {
          // If LDAP cannot give us a valid email address for this user
          // try expanding it through the older email expander code which
          // assumes a user name within a domain.
          //
          who.setEmailAddress(emailExpander.expand(username));
        }

        // Fill the cache with the user's current groups. We've already
        // spent the cost to open the LDAP connection, we might as well
        // do one more call to get their group membership. Since we are
        // in the middle of authenticating the user, its likely we will
        // need to know what access rights they have soon.
        //
        membershipCache.put(username, queryForGroups(ctx, username, m));
        return who;
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (NamingException e) {
      throw new AccountException("Cannot query LDAP for account", e);
    }
  }

  @Override
  public void onCreateAccount(final AuthRequest who, final Account account) {
    usernameCache.put(who.getLocalUser(), account.getId());
  }

  @Override
  public Set<AccountGroup.Id> groups(final AccountState who) {
    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>();
    r.addAll(membershipCache.get(findId(who.getExternalIds())));
    r.addAll(who.getInternalGroups());
    return r;
  }

  private Set<AccountGroup.Id> queryForGroups(final String username)
      throws NamingException, AccountException {
    final DirContext ctx = open();
    try {
      return queryForGroups(ctx, username, null);
    } finally {
      try {
        ctx.close();
      } catch (NamingException e) {
        log.warn("Cannot close LDAP query handle", e);
      }
    }
  }

  private Set<AccountGroup.Id> queryForGroups(final DirContext ctx,
      final String username, LdapQuery.Result account) throws NamingException,
      AccountException {
    final HashMap<String, String> params = new HashMap<String, String>();
    params.put(USERNAME, username);
    if (groupNeedsAccount) {
      if (account == null) {
        account = findAccount(ctx, username);
      }
      for (final String name : groupMemberQuery.getParameters()) {
        params.put(name, account.get(name));
      }
    }

    final Set<AccountGroup.Id> actual = new HashSet<AccountGroup.Id>();
    for (LdapQuery.Result r : groupMemberQuery.query(ctx, params)) {
      final String name = r.get(groupName);
      final AccountGroup group = groupCache.lookup(name);
      if (group != null && isLdapGroup(group)) {
        actual.add(group.getId());
      }
    }
    if (actual.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(actual);
    }
  }

  private boolean isLdapGroup(final AccountGroup group) {
    return group.isAutomaticMembership();
  }

  private static String findId(final Collection<AccountExternalId> ids) {
    for (final AccountExternalId i : ids) {
      if (i.isScheme(AccountExternalId.SCHEME_GERRIT)) {
        return i.getSchemeRest(AccountExternalId.SCHEME_GERRIT);
      }
    }
    return null;
  }

  @Override
  public Account.Id lookup(final String accountName) {
    return usernameCache.get(accountName);
  }

  private Account.Id queryForUsername(final String username) {
    try {
      final ReviewDb db = schema.open();
      try {
        final List<AccountExternalId> candidates =
            db.accountExternalIds().byExternal(
                AccountExternalId.SCHEME_GERRIT + username).toList();
        if (candidates.size() == 1) {
          return candidates.get(0).getAccountId();
        }
        return null;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      log.warn("Cannot query for username in database", e);
      return null;
    }
  }

  private DirContext open() throws NamingException {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (username != null) {
      env.put(Context.SECURITY_PRINCIPAL, username);
      env.put(Context.SECURITY_CREDENTIALS, password);
    }
    return new InitialDirContext(env);
  }

  private LdapQuery.Result findAccount(final DirContext ctx,
      final String username) throws NamingException, AccountException {
    final HashMap<String, String> params = new HashMap<String, String>();
    params.put(USERNAME, username);

    final List<LdapQuery.Result> res = accountQuery.query(ctx, params);
    switch (res.size()) {
      case 0:
        throw new AccountException("No such user:" + username);

      case 1:
        return res.get(0);

      default:
        throw new AccountException("Duplicate users: " + username);
    }
  }
}
