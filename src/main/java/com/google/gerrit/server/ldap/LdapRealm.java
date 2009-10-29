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
import com.google.gerrit.client.reviewdb.AuthType;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.ParamertizedString;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.ioutil.BlindSSLSocketFactory;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocketFactory;

@Singleton
class LdapRealm implements Realm {
  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);
  private static final String LDAP = "com.sun.jndi.ldap.LdapCtxFactory";
  private static final String USERNAME = "username";

  private final String server;
  private final String username;
  private final String password;
  private final LdapType type;
  private final boolean sslVerify;

  private final AuthConfig authConfig;
  private final SchemaFactory<ReviewDb> schema;
  private final EmailExpander emailExpander;
  private final ParamertizedString accountFullName;
  private final ParamertizedString accountEmailAddress;
  private final ParamertizedString accountSshUserName;
  private final String accountMemberField;
  private final List<LdapQuery> accountQueryList;
  private final SelfPopulatingCache<String, Account.Id> usernameCache;

  private final GroupCache groupCache;
  private boolean groupNeedsAccount;
  private final List<LdapQuery> groupMemberQueryList;
  private final SelfPopulatingCache<String, Set<AccountGroup.Id>> membershipCache;

  @Inject
  LdapRealm(
      final AuthConfig authConfig,
      final GroupCache groupCache,
      final EmailExpander emailExpander,
      final SchemaFactory<ReviewDb> schema,
      @Named(LdapModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.Id>> rawGroup,
      @Named(LdapModule.USERNAME_CACHE) final Cache<String, Account.Id> rawUsername,
      @GerritServerConfig final Config config) {
    this.authConfig = authConfig;
    this.groupCache = groupCache;
    this.emailExpander = emailExpander;
    this.schema = schema;

    this.server = required(config, "server");
    this.username = optional(config, "username");
    this.password = optional(config, "password");
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
    this.type = discoverLdapType();

    groupMemberQueryList = new ArrayList<LdapQuery>();
    accountQueryList = new ArrayList<LdapQuery>();

    final Set<String> groupAtts = new HashSet<String>();
    final Set<String> accountAtts = new HashSet<String>();

    // Group query
    //

    final SearchScope groupScope = scope(config, "groupScope");
    final String groupMemberPattern =
        optdef(config, "groupMemberPattern", type.groupMemberPattern());

    for (String groupBase : optionalList(config, "groupBase")) {
      if (groupMemberPattern != null) {
        final LdapQuery groupMemberQuery =
            new LdapQuery(groupBase, groupScope, groupMemberPattern, groupAtts);
        if (groupMemberQuery.getParameters().isEmpty()) {
          throw new IllegalArgumentException(
              "No variables in ldap.groupMemberPattern");
        }

        for (final String name : groupMemberQuery.getParameters()) {
          if (!USERNAME.equals(name)) {
            groupNeedsAccount = true;
            accountAtts.add(name);
          }
        }

        groupMemberQueryList.add(groupMemberQuery);
      }
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
    accountFullName = paramString(config, "accountFullName", type.accountFullName());
    if (accountFullName != null) {
      accountAtts.addAll(accountFullName.getParameterNames());
    }
    accountEmailAddress = paramString(config, "accountEmailAddress", type.accountEmailAddress());
    if (accountEmailAddress != null) {
      accountAtts.addAll(accountEmailAddress.getParameterNames());
    }
    accountSshUserName = paramString(config, "accountSshUserName", type.accountSshUserName());
    if (accountSshUserName != null) {
      accountAtts.addAll(accountSshUserName.getParameterNames());
    }
    accountMemberField = optdef(config, "accountMemberField", type.accountMemberField());
    if (accountMemberField != null) {
      accountAtts.add(accountMemberField);
    }

    final SearchScope accountScope = scope(config, "accountScope");
    final String accountPattern =
        reqdef(config, "accountPattern", type.accountPattern());

    for (String accountBase : requiredList(config, "accountBase")) {
      final LdapQuery accountQuery =
          new LdapQuery(accountBase, accountScope, accountPattern, accountAtts);
      if (accountQuery.getParameters().isEmpty()) {
        throw new IllegalArgumentException(
            "No variables in ldap.accountPattern");
      }
      accountQueryList.add(accountQuery);
    }

    usernameCache = new SelfPopulatingCache<String, Account.Id>(rawUsername) {
      @Override
      public Account.Id createEntry(final String username) throws Exception {
        return queryForUsername(username);
      }
    };
  }

  private static SearchScope scope(final Config c, final String setting) {
    return ConfigUtil.getEnum(c, "ldap", null, setting, SearchScope.SUBTREE);
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

  private static List<String> optionalList(final Config config,
      final String name) {
    String s[] = config.getStringList("ldap", null, name);
    return Arrays.asList(s);
  }

  private static List<String> requiredList(final Config config,
      final String name) {
    List<String> vlist = optionalList(config, name);

    if (vlist.isEmpty()) {
      throw new IllegalArgumentException("No ldap " + name + " configured");
    }

    return vlist;
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

  private static ParamertizedString paramString(Config c, String n, String d) {
    String expression = optdef(c, n, d);
    if (expression == null) {
      return null;
    } else if (expression.contains("${")) {
      return new ParamertizedString(expression);
    } else {
      return new ParamertizedString("${" + expression + "}");
    }
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

  private static String apply(ParamertizedString p, LdapQuery.Result m) {
    if (p == null) {
      return null;
    }
    String r = p.replace(m.map());
    return r.isEmpty() ? null : r;
  }

  public AuthRequest authenticate(final AuthRequest who)
      throws AccountException {
    final String username = who.getLocalUser();
    try {
      final DirContext ctx = open();
      try {
        final LdapQuery.Result m = findAccount(ctx, username);

        if (authConfig.getAuthType() == AuthType.LDAP) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          authenticate(m.getDN(), who.getPassword());
        }

        who.setDisplayName(apply(accountFullName, m));
        who.setSshUserName(apply(accountSshUserName, m));

        if (accountEmailAddress != null) {
          who.setEmailAddress(apply(accountEmailAddress, m));

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
      log.error("Cannot query LDAP to autenticate user", e);
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
    final Set<AccountGroup.Id> actual = new HashSet<AccountGroup.Id>();

    if (!groupMemberQueryList.isEmpty()) {
      final HashMap<String, String> params = new HashMap<String, String>();

      if (groupNeedsAccount) {
        if (account == null) {
          account = findAccount(ctx, username);
        }
        for (final String name : groupMemberQueryList.get(0).getParameters()) {
          params.put(name, account.get(name));
        }
      }

      params.put(USERNAME, username);

      for (LdapQuery groupMemberQuery : groupMemberQueryList) {
        for (LdapQuery.Result r : groupMemberQuery.query(ctx, params)) {
          memberOfGroup(actual, r.getDN());
        }
      }
    }

    if (accountMemberField != null) {
      if (account == null) {
        account = findAccount(ctx, username);
      }

      final Set<String> groupDNs = new HashSet<String>();
      NamingEnumeration<?> groups = account.getAll(accountMemberField).getAll();
      while (groups.hasMore()) {
        recursivelyExpandGroups(groupDNs, ctx, (String) groups.next());
      }
      for (String dn : groupDNs) {
        memberOfGroup(actual, dn);
      }
    }

    if (actual.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(actual);
    }
  }

  private void memberOfGroup(final Set<AccountGroup.Id> actual, final String dn) {
    final AccountGroup group;

    group = groupCache.get(new AccountGroup.ExternalNameKey(dn));
    if (group != null) {
      actual.add(group.getId());
    }
  }

  private void recursivelyExpandGroups(final Set<String> groupDNs,
      final DirContext ctx, final String groupDN) {
    if (groupDNs.add(groupDN)) {
      // Recursively identify the groups it is a member of.
      //
      try {
        final Attribute in = ctx.getAttributes(groupDN).get(accountMemberField);
        if (in != null) {
          final NamingEnumeration<?> groups = in.getAll();
          while (groups.hasMore()) {
            recursivelyExpandGroups(groupDNs, ctx, (String) groups.next());
          }
        }
      } catch (NamingException e) {
        log.warn("Could not find group " + groupDN, e);
      }
    }
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
        final String id = AccountExternalId.SCHEME_GERRIT + username;
        final AccountExternalId extId =
            db.accountExternalIds().get(new AccountExternalId.Key(id));
        return extId != null ? extId.getAccountId() : null;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      log.warn("Cannot query for username in database", e);
      return null;
    }
  }

  private Properties createContextProperties() {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (server.startsWith("ldaps:") && !sslVerify) {
      Class<? extends SSLSocketFactory> factory = BlindSSLSocketFactory.class;
      env.put("java.naming.ldap.factory.socket", factory.getName());
    }
    return env;
  }

  private DirContext open() throws NamingException {
    final Properties env = createContextProperties();
    if (username != null) {
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, username);
      env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
    }
    return new InitialDirContext(env);
  }

  private void authenticate(String dn, String password) throws AccountException {
    final Properties env = createContextProperties();
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, dn);
    env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
    try {
      new InitialDirContext(env).close();
    } catch (NamingException e) {
      throw new AccountException("Incorrect username or password", e);
    }
  }

  private LdapType discoverLdapType() {
    try {
      final DirContext ctx = open();
      try {
        return LdapType.guessType(ctx);
      } finally {
        ctx.close();
      }
    } catch (NamingException e) {
      log.warn("Cannot discover type of LDAP server at " + server
          + ", assuming the server is RFC 2307 compliant.", e);
      return LdapType.RFC_2307;
    }
  }

  private LdapQuery.Result findAccount(final DirContext ctx,
      final String username) throws NamingException, AccountException {
    final HashMap<String, String> params = new HashMap<String, String>();
    params.put(USERNAME, username);

    final List<LdapQuery.Result> res = new ArrayList<LdapQuery.Result>();
    for (LdapQuery accountQuery : accountQueryList) {
      res.addAll(accountQuery.query(ctx, params));
    }

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
