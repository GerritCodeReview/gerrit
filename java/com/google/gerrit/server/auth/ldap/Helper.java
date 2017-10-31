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

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AuthenticationFailedException;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import org.eclipse.jgit.lib.Config;

@Singleton
class Helper {
  static final String LDAP_UUID = "ldap:";

  private final Cache<String, ImmutableSet<String>> parentGroups;
  private final Config config;
  private final String server;
  private final String username;
  private final String password;
  private final String referral;
  private final boolean sslVerify;
  private final String authentication;
  private volatile LdapSchema ldapSchema;
  private final String readTimeoutMillis;
  private final String connectTimeoutMillis;
  private final boolean useConnectionPooling;
  private final boolean groupsVisibleToAll;

  @Inject
  Helper(
      @GerritServerConfig Config config,
      @Named(LdapModule.PARENT_GROUPS_CACHE) Cache<String, ImmutableSet<String>> parentGroups) {
    this.config = config;
    this.server = LdapRealm.optional(config, "server");
    this.username = LdapRealm.optional(config, "username");
    this.password = LdapRealm.optional(config, "password", "");
    this.referral = LdapRealm.optional(config, "referral", "ignore");
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
    this.groupsVisibleToAll = config.getBoolean("ldap", "groupsVisibleToAll", false);
    this.authentication = LdapRealm.optional(config, "authentication", "simple");
    String readTimeout = LdapRealm.optional(config, "readTimeout");
    if (readTimeout != null) {
      readTimeoutMillis =
          Long.toString(ConfigUtil.getTimeUnit(readTimeout, 0, TimeUnit.MILLISECONDS));
    } else {
      readTimeoutMillis = null;
    }
    String connectTimeout = LdapRealm.optional(config, "connectTimeout");
    if (connectTimeout != null) {
      connectTimeoutMillis =
          Long.toString(ConfigUtil.getTimeUnit(connectTimeout, 0, TimeUnit.MILLISECONDS));
    } else {
      connectTimeoutMillis = null;
    }
    this.parentGroups = parentGroups;
    this.useConnectionPooling = LdapRealm.optional(config, "useConnectionPooling", false);
  }

  private Properties createContextProperties() {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LdapRealm.LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (server.startsWith("ldaps:") && !sslVerify) {
      Class<? extends SSLSocketFactory> factory = BlindSSLSocketFactory.class;
      env.put("java.naming.ldap.factory.socket", factory.getName());
    }
    if (readTimeoutMillis != null) {
      env.put("com.sun.jndi.ldap.read.timeout", readTimeoutMillis);
    }
    if (connectTimeoutMillis != null) {
      env.put("com.sun.jndi.ldap.connect.timeout", connectTimeoutMillis);
    }
    if (useConnectionPooling) {
      env.put("com.sun.jndi.ldap.connect.pool", "true");
    }
    return env;
  }

  DirContext open() throws NamingException, LoginException {
    final Properties env = createContextProperties();
    env.put(Context.SECURITY_AUTHENTICATION, authentication);
    env.put(Context.REFERRAL, referral);
    if ("GSSAPI".equals(authentication)) {
      return kerberosOpen(env);
    }
    if (username != null) {
      env.put(Context.SECURITY_PRINCIPAL, username);
      env.put(Context.SECURITY_CREDENTIALS, password);
    }
    return new InitialDirContext(env);
  }

  private DirContext kerberosOpen(Properties env) throws LoginException, NamingException {
    LoginContext ctx = new LoginContext("KerberosLogin");
    ctx.login();
    Subject subject = ctx.getSubject();
    try {
      return Subject.doAs(
          subject,
          new PrivilegedExceptionAction<DirContext>() {
            @Override
            public DirContext run() throws NamingException {
              return new InitialDirContext(env);
            }
          });
    } catch (PrivilegedActionException e) {
      Throwables.throwIfInstanceOf(e.getException(), NamingException.class);
      Throwables.throwIfInstanceOf(e.getException(), RuntimeException.class);
      LdapRealm.log.warn("Internal error", e.getException());
      return null;
    } finally {
      ctx.logout();
    }
  }

  DirContext authenticate(String dn, String password) throws AccountException {
    final Properties env = createContextProperties();
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, dn);
    env.put(Context.SECURITY_CREDENTIALS, password);
    env.put(Context.REFERRAL, referral);
    try {
      return new InitialDirContext(env);
    } catch (NamingException e) {
      throw new AuthenticationFailedException("Incorrect username or password", e);
    }
  }

  LdapSchema getSchema(DirContext ctx) {
    if (ldapSchema == null) {
      synchronized (this) {
        if (ldapSchema == null) {
          ldapSchema = new LdapSchema(ctx);
        }
      }
    }
    return ldapSchema;
  }

  LdapQuery.Result findAccount(
      Helper.LdapSchema schema, DirContext ctx, String username, boolean fetchMemberOf)
      throws NamingException, AccountException {
    final HashMap<String, String> params = new HashMap<>();
    params.put(LdapRealm.USERNAME, username);

    List<LdapQuery> accountQueryList;
    if (fetchMemberOf && schema.type.accountMemberField() != null) {
      accountQueryList = schema.accountWithMemberOfQueryList;
    } else {
      accountQueryList = schema.accountQueryList;
    }

    for (LdapQuery accountQuery : accountQueryList) {
      List<LdapQuery.Result> res = accountQuery.query(ctx, params);
      if (res.size() == 1) {
        return res.get(0);
      } else if (res.size() > 1) {
        throw new AccountException("Duplicate users: " + username);
      }
    }
    throw new NoSuchUserException(username);
  }

  Set<AccountGroup.UUID> queryForGroups(
      final DirContext ctx, String username, LdapQuery.Result account) throws NamingException {
    final LdapSchema schema = getSchema(ctx);
    final Set<String> groupDNs = new HashSet<>();

    if (!schema.groupMemberQueryList.isEmpty()) {
      final HashMap<String, String> params = new HashMap<>();

      if (account == null) {
        try {
          account = findAccount(schema, ctx, username, false);
        } catch (AccountException e) {
          return Collections.emptySet();
        }
      }
      for (String name : schema.groupMemberQueryList.get(0).getParameters()) {
        params.put(name, account.get(name));
      }

      params.put(LdapRealm.USERNAME, username);

      for (LdapQuery groupMemberQuery : schema.groupMemberQueryList) {
        for (LdapQuery.Result r : groupMemberQuery.query(ctx, params)) {
          recursivelyExpandGroups(groupDNs, schema, ctx, r.getDN());
        }
      }
    }

    if (schema.accountMemberField != null) {
      if (account == null || account.getAll(schema.accountMemberField) == null) {
        try {
          account = findAccount(schema, ctx, username, true);
        } catch (AccountException e) {
          return Collections.emptySet();
        }
      }

      final Attribute groupAtt = account.getAll(schema.accountMemberField);
      if (groupAtt != null) {
        final NamingEnumeration<?> groups = groupAtt.getAll();
        try {
          while (groups.hasMore()) {
            final String nextDN = (String) groups.next();
            recursivelyExpandGroups(groupDNs, schema, ctx, nextDN);
          }
        } catch (PartialResultException e) {
          // Ignored
        }
      }
    }

    final Set<AccountGroup.UUID> actual = new HashSet<>();
    for (String dn : groupDNs) {
      actual.add(new AccountGroup.UUID(LDAP_UUID + dn));
    }

    if (actual.isEmpty()) {
      return Collections.emptySet();
    }
    return ImmutableSet.copyOf(actual);
  }

  private void recursivelyExpandGroups(
      final Set<String> groupDNs,
      final LdapSchema schema,
      final DirContext ctx,
      final String groupDN) {
    if (groupDNs.add(groupDN)
        && schema.accountMemberField != null
        && schema.accountMemberExpandGroups) {
      ImmutableSet<String> cachedParentsDNs = parentGroups.getIfPresent(groupDN);
      if (cachedParentsDNs == null) {
        // Recursively identify the groups it is a member of.
        ImmutableSet.Builder<String> dns = ImmutableSet.builder();
        try {
          final Name compositeGroupName = new CompositeName().add(groupDN);
          final Attribute in =
              ctx.getAttributes(compositeGroupName, schema.accountMemberFieldArray)
                  .get(schema.accountMemberField);
          if (in != null) {
            final NamingEnumeration<?> groups = in.getAll();
            try {
              while (groups.hasMore()) {
                dns.add((String) groups.next());
              }
            } catch (PartialResultException e) {
              // Ignored
            }
          }
        } catch (NamingException e) {
          LdapRealm.log.warn("Could not find group " + groupDN, e);
        }
        cachedParentsDNs = dns.build();
        parentGroups.put(groupDN, cachedParentsDNs);
      }
      for (String dn : cachedParentsDNs) {
        recursivelyExpandGroups(groupDNs, schema, ctx, dn);
      }
    }
  }

  public boolean groupsVisibleToAll() {
    return this.groupsVisibleToAll;
  }

  class LdapSchema {
    final LdapType type;

    final ParameterizedString accountFullName;
    final ParameterizedString accountEmailAddress;
    final ParameterizedString accountSshUserName;
    final String accountMemberField;
    final boolean accountMemberExpandGroups;
    final String[] accountMemberFieldArray;
    final List<LdapQuery> accountQueryList;
    final List<LdapQuery> accountWithMemberOfQueryList;

    final List<String> groupBases;
    final SearchScope groupScope;
    final ParameterizedString groupPattern;
    final ParameterizedString groupName;
    final List<LdapQuery> groupMemberQueryList;

    LdapSchema(DirContext ctx) {
      type = discoverLdapType(ctx);
      groupMemberQueryList = new ArrayList<>();
      accountQueryList = new ArrayList<>();
      accountWithMemberOfQueryList = new ArrayList<>();

      final Set<String> accountAtts = new HashSet<>();

      // Group query
      //

      groupBases = LdapRealm.optionalList(config, "groupBase");
      groupScope = LdapRealm.scope(config, "groupScope");
      groupPattern = LdapRealm.paramString(config, "groupPattern", type.groupPattern());
      groupName = LdapRealm.paramString(config, "groupName", type.groupName());
      final String groupMemberPattern =
          LdapRealm.optdef(config, "groupMemberPattern", type.groupMemberPattern());

      for (String groupBase : groupBases) {
        if (groupMemberPattern != null) {
          final LdapQuery groupMemberQuery =
              new LdapQuery(
                  groupBase,
                  groupScope,
                  new ParameterizedString(groupMemberPattern),
                  Collections.<String>emptySet());
          if (groupMemberQuery.getParameters().isEmpty()) {
            throw new IllegalArgumentException("No variables in ldap.groupMemberPattern");
          }

          for (String name : groupMemberQuery.getParameters()) {
            accountAtts.add(name);
          }

          groupMemberQueryList.add(groupMemberQuery);
        }
      }

      // Account query
      //
      accountFullName = LdapRealm.paramString(config, "accountFullName", type.accountFullName());
      if (accountFullName != null) {
        accountAtts.addAll(accountFullName.getParameterNames());
      }
      accountEmailAddress =
          LdapRealm.paramString(config, "accountEmailAddress", type.accountEmailAddress());
      if (accountEmailAddress != null) {
        accountAtts.addAll(accountEmailAddress.getParameterNames());
      }
      accountSshUserName =
          LdapRealm.paramString(config, "accountSshUserName", type.accountSshUserName());
      if (accountSshUserName != null) {
        accountAtts.addAll(accountSshUserName.getParameterNames());
      }
      accountMemberField =
          LdapRealm.optdef(config, "accountMemberField", type.accountMemberField());
      if (accountMemberField != null) {
        accountMemberFieldArray = new String[] {accountMemberField};
      } else {
        accountMemberFieldArray = null;
      }
      accountMemberExpandGroups =
          LdapRealm.optional(config, "accountMemberExpandGroups", type.accountMemberExpandGroups());

      final SearchScope accountScope = LdapRealm.scope(config, "accountScope");
      final String accountPattern =
          LdapRealm.reqdef(config, "accountPattern", type.accountPattern());

      Set<String> accountWithMemberOfAtts;
      if (accountMemberField != null) {
        accountWithMemberOfAtts = new HashSet<>(accountAtts);
        accountWithMemberOfAtts.add(accountMemberField);
      } else {
        accountWithMemberOfAtts = null;
      }
      for (String accountBase : LdapRealm.requiredList(config, "accountBase")) {
        LdapQuery accountQuery =
            new LdapQuery(
                accountBase, accountScope, new ParameterizedString(accountPattern), accountAtts);
        if (accountQuery.getParameters().isEmpty()) {
          throw new IllegalArgumentException("No variables in ldap.accountPattern");
        }
        accountQueryList.add(accountQuery);

        if (accountWithMemberOfAtts != null) {
          LdapQuery accountWithMemberOfQuery =
              new LdapQuery(
                  accountBase,
                  accountScope,
                  new ParameterizedString(accountPattern),
                  accountWithMemberOfAtts);
          accountWithMemberOfQueryList.add(accountWithMemberOfQuery);
        }
      }
    }

    LdapType discoverLdapType(DirContext ctx) {
      try {
        return LdapType.guessType(ctx);
      } catch (NamingException e) {
        LdapRealm.log.warn(
            "Cannot discover type of LDAP server at "
                + server
                + ", assuming the server is RFC 2307 compliant.",
            e);
        return LdapType.RFC_2307;
      }
    }
  }
}
