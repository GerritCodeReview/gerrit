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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_GERRIT;

import com.google.gerrit.common.data.ParamertizedString;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.reviewdb.ReviewDb;
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
import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
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
import java.util.Map;
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
  private static final String GROUPNAME = "groupname";

  private final Config config;
  private final String server;
  private final String username;
  private final String password;
  private final boolean sslVerify;

  private final AuthConfig authConfig;
  private final SchemaFactory<ReviewDb> schema;
  private final EmailExpander emailExpander;
  private final SelfPopulatingCache<String, Account.Id> usernameCache;
  private final Set<Account.FieldName> readOnlyAccountFields;

  private final GroupCache groupCache;
  private final SelfPopulatingCache<String, Set<AccountGroup.Id>> membershipCache;

  private volatile LdapSchema ldapSchema;

  @Inject
  LdapRealm(
      final AuthConfig authConfig,
      final GroupCache groupCache,
      final EmailExpander emailExpander,
      final SchemaFactory<ReviewDb> schema,
      @Named(LdapModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.Id>> rawGroup,
      @Named(LdapModule.USERNAME_CACHE) final Cache<String, Account.Id> rawUsername,
      @GerritServerConfig final Config config) {
    this.config = config;
    this.authConfig = authConfig;
    this.groupCache = groupCache;
    this.emailExpander = emailExpander;
    this.schema = schema;

    this.server = required(config, "server");
    this.username = optional(config, "username");
    this.password = optional(config, "password");
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
    this.readOnlyAccountFields = new HashSet<Account.FieldName>();

    if (optdef(config, "accountFullName", "DEFAULT") != null) {
      readOnlyAccountFields.add(Account.FieldName.FULL_NAME);
    }
    if (optdef(config, "accountSshUserName", "DEFAULT") != null) {
      readOnlyAccountFields.add(Account.FieldName.USER_NAME);
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
    return !readOnlyAccountFields.contains(field);
  }

  private static String apply(ParamertizedString p, LdapQuery.Result m)
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

  public AuthRequest authenticate(final AuthRequest who)
      throws AccountException {
    final String username = who.getLocalUser();
    try {
      final DirContext ctx;
      if (authConfig.getAuthType() == AuthType.LDAP_BIND) {
        ctx = authenticate(username, who.getPassword());
      } else {
        ctx = open();
      }
      try {
        final LdapSchema schema = getSchema(ctx);
        final LdapQuery.Result m = findAccount(schema, ctx, username);

        if (authConfig.getAuthType() == AuthType.LDAP) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          authenticate(m.getDN(), who.getPassword());
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
    final LdapSchema schema = getSchema(ctx);
    final Set<String> groupDNs = new HashSet<String>();

    if (!schema.groupMemberQueryList.isEmpty()) {
      final HashMap<String, String> params = new HashMap<String, String>();

      if (schema.groupNeedsAccount) {
        if (account == null) {
          account = findAccount(schema, ctx, username);
        }
        for (String name : schema.groupMemberQueryList.get(0).getParameters()) {
          params.put(name, account.get(name));
        }
      }

      params.put(USERNAME, username);

      for (LdapQuery groupMemberQuery : schema.groupMemberQueryList) {
        for (LdapQuery.Result r : groupMemberQuery.query(ctx, params)) {
          recursivelyExpandGroups(groupDNs, schema, ctx, r.getDN());
        }
      }
    }

    if (schema.accountMemberField != null) {
      if (account == null) {
        account = findAccount(schema, ctx, username);
      }

      final Attribute groupAtt = account.getAll(schema.accountMemberField);
      if (groupAtt != null) {
        final NamingEnumeration<?> groups = groupAtt.getAll();
        while (groups.hasMore()) {
          final String nextDN = (String) groups.next();
          recursivelyExpandGroups(groupDNs, schema, ctx, nextDN);
        }
      }
    }

    final Set<AccountGroup.Id> actual = new HashSet<AccountGroup.Id>();
    for (String dn : groupDNs) {
      for (AccountGroup group : groupCache
          .get(new AccountGroup.ExternalNameKey(dn))) {
        if (group.getType() == AccountGroup.Type.LDAP) {
          actual.add(group.getId());
        }
      }
    }

    if (actual.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(actual);
    }
  }

  private void recursivelyExpandGroups(final Set<String> groupDNs,
      final LdapSchema schema, final DirContext ctx, final String groupDN) {
    if (groupDNs.add(groupDN)) {
      // Recursively identify the groups it is a member of.
      //
      try {
        final Attribute in =
            ctx.getAttributes(groupDN).get(schema.accountMemberField);
        if (in != null) {
          final NamingEnumeration<?> groups = in.getAll();
          while (groups.hasMore()) {
            final String nextDN = (String) groups.next();
            recursivelyExpandGroups(groupDNs, schema, ctx, nextDN);
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
        return i.getSchemeRest();
      }
    }
    return null;
  }

  @Override
  public Account.Id lookup(final String accountName) {
    return usernameCache.get(accountName);
  }

  @Override
  public Set<AccountGroup.ExternalNameKey> lookupGroups(String name) {
    final Set<AccountGroup.ExternalNameKey> out;
    final Map<String, String> params = Collections.<String, String> emptyMap();

    out = new HashSet<AccountGroup.ExternalNameKey>();
    try {
      final DirContext ctx = open();
      try {
        final LdapSchema schema = getSchema(ctx);
        final ParamertizedString filter =
            ParamertizedString.asis(schema.groupPattern
                .replace(GROUPNAME, name).toString());
        for (String groupBase : schema.groupBases) {
          final LdapQuery query =
              new LdapQuery(groupBase, schema.groupScope, filter, Collections
                  .<String> emptySet());
          for (LdapQuery.Result res : query.query(ctx, params)) {
            out.add(new AccountGroup.ExternalNameKey(res.getDN()));
          }
        }
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (NamingException e) {
      log.warn("Cannot query LDAP for groups matching requested name", e);
    }
    return out;
  }

  private Account.Id queryForUsername(final String username) {
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId extId =
            db.accountExternalIds().get(
                new AccountExternalId.Key(SCHEME_GERRIT, username));
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

  private DirContext authenticate(String dn, String password)
      throws AccountException {
    final Properties env = createContextProperties();
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, dn);
    env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
    try {
      return new InitialDirContext(env);
    } catch (NamingException e) {
      throw new AccountException("Incorrect username or password", e);
    }
  }

  private LdapQuery.Result findAccount(final LdapSchema schema,
      final DirContext ctx, final String username) throws NamingException,
      AccountException {
    final HashMap<String, String> params = new HashMap<String, String>();
    params.put(USERNAME, username);

    final List<LdapQuery.Result> res = new ArrayList<LdapQuery.Result>();
    for (LdapQuery accountQuery : schema.accountQueryList) {
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

  private LdapSchema getSchema(DirContext ctx) {
    if (ldapSchema == null) {
      synchronized (this) {
        if (ldapSchema == null) {
          ldapSchema = new LdapSchema(ctx);
        }
      }
    }
    return ldapSchema;
  }

  private class LdapSchema {
    final LdapType type;

    final ParamertizedString accountFullName;
    final ParamertizedString accountEmailAddress;
    final ParamertizedString accountSshUserName;
    final String accountMemberField;
    final List<LdapQuery> accountQueryList;

    boolean groupNeedsAccount;
    final List<String> groupBases;
    final SearchScope groupScope;
    final ParamertizedString groupPattern;
    final List<LdapQuery> groupMemberQueryList;

    LdapSchema(final DirContext ctx) {
      type = discoverLdapType(ctx);
      groupMemberQueryList = new ArrayList<LdapQuery>();
      accountQueryList = new ArrayList<LdapQuery>();

      final Set<String> accountAtts = new HashSet<String>();

      // Group query
      //

      groupBases = optionalList(config, "groupBase");
      groupScope = scope(config, "groupScope");
      groupPattern = paramString(config, "groupPattern", type.groupPattern());
      final String groupMemberPattern =
          optdef(config, "groupMemberPattern", type.groupMemberPattern());

      for (String groupBase : groupBases) {
        if (groupMemberPattern != null) {
          final LdapQuery groupMemberQuery =
              new LdapQuery(groupBase, groupScope, new ParamertizedString(
                  groupMemberPattern), Collections.<String> emptySet());
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

      // Account query
      //
      accountFullName =
          paramString(config, "accountFullName", type.accountFullName());
      if (accountFullName != null) {
        accountAtts.addAll(accountFullName.getParameterNames());
      }
      accountEmailAddress =
          paramString(config, "accountEmailAddress", type.accountEmailAddress());
      if (accountEmailAddress != null) {
        accountAtts.addAll(accountEmailAddress.getParameterNames());
      }
      accountSshUserName =
          paramString(config, "accountSshUserName", type.accountSshUserName());
      if (accountSshUserName != null) {
        accountAtts.addAll(accountSshUserName.getParameterNames());
      }
      accountMemberField =
          optdef(config, "accountMemberField", type.accountMemberField());
      if (accountMemberField != null) {
        accountAtts.add(accountMemberField);
      }

      final SearchScope accountScope = scope(config, "accountScope");
      final String accountPattern =
          reqdef(config, "accountPattern", type.accountPattern());

      for (String accountBase : requiredList(config, "accountBase")) {
        final LdapQuery accountQuery =
            new LdapQuery(accountBase, accountScope, new ParamertizedString(
                accountPattern), accountAtts);
        if (accountQuery.getParameters().isEmpty()) {
          throw new IllegalArgumentException(
              "No variables in ldap.accountPattern");
        }
        accountQueryList.add(accountQuery);
      }
    }

    LdapType discoverLdapType(DirContext ctx) {
      try {
        return LdapType.guessType(ctx);
      } catch (NamingException e) {
        log.warn("Cannot discover type of LDAP server at " + server
            + ", assuming the server is RFC 2307 compliant.", e);
        return LdapType.RFC_2307;
      }
    }
  }
}
