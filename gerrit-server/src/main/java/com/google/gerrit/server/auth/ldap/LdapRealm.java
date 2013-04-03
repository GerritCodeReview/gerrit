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

package com.google.gerrit.server.auth.ldap;

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_GERRIT;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.auth.AuthenticationUnavailableException;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.login.LoginException;

@Singleton
class LdapRealm implements Realm {
  static final Logger log = LoggerFactory.getLogger(LdapRealm.class);
  static final String LDAP = "com.sun.jndi.ldap.LdapCtxFactory";
  static final String USERNAME = "username";

  private final Helper helper;
  private final AuthConfig authConfig;
  private final EmailExpander emailExpander;
  private final LoadingCache<String, Optional<Account.Id>> usernameCache;
  private final Set<Account.FieldName> readOnlyAccountFields;
  private final Config config;

  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;

  @Inject
  LdapRealm(
      final Helper helper,
      final AuthConfig authConfig,
      final EmailExpander emailExpander,
      @Named(LdapModule.GROUP_CACHE) final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      @Named(LdapModule.USERNAME_CACHE) final LoadingCache<String, Optional<Account.Id>> usernameCache,
      @GerritServerConfig final Config config) {
    this.helper = helper;
    this.authConfig = authConfig;
    this.emailExpander = emailExpander;
    this.usernameCache = usernameCache;
    this.membershipCache = membershipCache;
    this.config = config;

    this.readOnlyAccountFields = new HashSet<Account.FieldName>();

    if (optdef(config, "accountFullName", "DEFAULT") != null) {
      readOnlyAccountFields.add(Account.FieldName.FULL_NAME);
    }
    if (optdef(config, "accountSshUserName", "DEFAULT") != null) {
      readOnlyAccountFields.add(Account.FieldName.USER_NAME);
    }
  }

  static SearchScope scope(final Config c, final String setting) {
    return ConfigUtil.getEnum(c, "ldap", null, setting, SearchScope.SUBTREE);
  }

  static String optional(final Config config, final String name) {
    return config.getString("ldap", null, name);
  }

  static String required(final Config config, final String name) {
    final String v = optional(config, name);
    if (v == null || "".equals(v)) {
      throw new IllegalArgumentException("No ldap." + name + " configured");
    }
    return v;
  }

  static List<String> optionalList(final Config config,
      final String name) {
    String s[] = config.getStringList("ldap", null, name);
    return Arrays.asList(s);
  }

  static List<String> requiredList(final Config config,
      final String name) {
    List<String> vlist = optionalList(config, name);

    if (vlist.isEmpty()) {
      throw new IllegalArgumentException("No ldap " + name + " configured");
    }

    return vlist;
  }

  static String optdef(final Config c, final String n, final String d) {
    final String[] v = c.getStringList("ldap", null, n);
    if (v == null || v.length == 0) {
      return d;

    } else if (v[0] == null || "".equals(v[0])) {
      return null;

    } else {
      return v[0];
    }
  }

  static String reqdef(final Config c, final String n, final String d) {
    final String v = optdef(c, n, d);
    if (v == null) {
      throw new IllegalArgumentException("No ldap." + n + " configured");
    }
    return v;
  }

  static ParameterizedString paramString(Config c, String n, String d) {
    String expression = optdef(c, n, d);
    if (expression == null) {
      return null;
    } else if (expression.contains("${")) {
      return new ParameterizedString(expression);
    } else {
      return new ParameterizedString("${" + expression + "}");
    }
  }

  @Override
  public boolean allowsEdit(final Account.FieldName field) {
    return !readOnlyAccountFields.contains(field);
  }

  static String apply(ParameterizedString p, LdapQuery.Result m)
      throws NamingException {
    if (p == null) {
      return null;
    }

    final Map<String, String> values = new HashMap<String, String>();
    for (final String name : m.attributes()) {
      values.put(name, m.get(name));
    }

    String r = p.replace(values);
    return r.isEmpty() ? null : r;
  }

  @Override
  public AuthRequest authenticate(final AuthRequest who)
      throws AccountException {
    if (config.getBoolean("ldap", "localUsernameToLowerCase", false)) {
      who.setLocalUser(who.getLocalUser().toLowerCase(Locale.US));
    }

    final String username = who.getLocalUser();
    try {
      final DirContext ctx;
      if (authConfig.getAuthType() == AuthType.LDAP_BIND) {
        ctx = helper.authenticate(username, who.getPassword());
      } else {
        ctx = helper.open();
      }
      try {
        final Helper.LdapSchema schema = helper.getSchema(ctx);
        final LdapQuery.Result m = helper.findAccount(schema, ctx, username);

        if (authConfig.getAuthType() == AuthType.LDAP && !who.isSkipAuthentication()) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          helper.authenticate(m.getDN(), who.getPassword());
        }

        who.setDisplayName(apply(schema.accountFullName, m));
        who.setUserName(apply(schema.accountSshUserName, m));

        if (schema.accountEmailAddress != null) {
          who.setEmailAddress(apply(schema.accountEmailAddress, m));

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
        membershipCache.put(username, helper.queryForGroups(ctx, username, m));
        return who;
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (NamingException e) {
      log.error("Cannot query LDAP to authenticate user", e);
      throw new AuthenticationUnavailableException("Cannot query LDAP for account", e);
    } catch (LoginException e) {
      log.error("Cannot authenticate server via JAAS", e);
      throw new AuthenticationUnavailableException("Cannot query LDAP for account", e);
    }
  }

  @Override
  public AuthRequest link(ReviewDb db, Account.Id to, AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest unlink(ReviewDb db, Account.Id from, AuthRequest who) {
    return who;
  }

  @Override
  public void onCreateAccount(final AuthRequest who, final Account account) {
    usernameCache.put(who.getLocalUser(), Optional.of(account.getId()));
  }

  @Override
  public Account.Id lookup(String accountName) {
    if (Strings.isNullOrEmpty(accountName)) {
      return null;
    }
    try {
      Optional<Account.Id> id = usernameCache.get(accountName);
      return id != null ? id.orNull() : null;
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup account %s in LDAP", accountName), e);
      return null;
    }
  }

  static class UserLoader extends CacheLoader<String, Optional<Account.Id>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    UserLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public Optional<Account.Id> load(String username) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId extId =
            db.accountExternalIds().get(
                new AccountExternalId.Key(SCHEME_GERRIT, username));
        if (extId != null) {
          return Optional.of(extId.getAccountId());
        }
        return Optional.absent();
      } finally {
        db.close();
      }
    }
  }

  static class MemberLoader extends CacheLoader<String, Set<AccountGroup.UUID>> {
    private final Helper helper;

    @Inject
    MemberLoader(final Helper helper) {
      this.helper = helper;
    }

    @Override
    public Set<AccountGroup.UUID> load(String username) throws Exception {
      final DirContext ctx = helper.open();
      try {
        return helper.queryForGroups(ctx, username, null);
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    }
  }

  static class ExistenceLoader extends CacheLoader<String, Boolean> {
    private final Helper helper;

    @Inject
    ExistenceLoader(final Helper helper) {
      this.helper = helper;
    }

    @Override
    public Boolean load(final String groupDn) throws Exception {
      final DirContext ctx = helper.open();
      try {
        Name compositeGroupName = new CompositeName().add(groupDn);
        try {
          ctx.getAttributes(compositeGroupName);
          return true;
        } catch (NamingException e) {
          return false;
        }
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    }
  }
}
