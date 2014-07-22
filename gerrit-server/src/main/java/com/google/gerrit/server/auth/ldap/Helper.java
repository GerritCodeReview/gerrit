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

import static com.google.gerrit.server.auth.ldap.LdapGroupBackend.LDAP_UUID;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;

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
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

@Singleton class Helper {
  public static String get(final String attName, final Attributes entry)
      throws NamingException {
    final Attribute att = entry.get(attName);
    return att != null && 0 < att.size() ? String.valueOf(att.get(0)) : null;
  }

  public static List<String> valuesOf(Attribute a) {
    if (a == null) {
      return null;
    }
    List<String> vs = Lists.newArrayList();
    try {
      NamingEnumeration<?> values = a.getAll();
      while (values.hasMore()) {
        vs.add(String.valueOf(values.next()));
      }
    } catch (NamingException e) {
      LdapRealm.log.warn(
          String.format("Could not find values of %s", a.getID()), e);
      return null;
    }
    return vs;
  }

  private final Cache<String, ImmutableSet<String>> groupsByInclude;
  private final Cache<String, ImmutableSet<String>> groupsByIncluded;
  private final Cache<String, ImmutableSet<String>> usersByIncluded;
  private final Config config;
  private final String server;
  private final String username;
  private final String password;
  private final String referral;
  private final boolean sslVerify;
  private final String authentication;
  private volatile LdapSchema ldapSchema;
  private final String readTimeOutMillis;

  @Inject
  Helper(@GerritServerConfig final Config config,
      @Named(LdapModule.GROUPS_BYINCLUDED_CACHE)
      Cache<String, ImmutableSet<String>> groupsByIncluded,
      @Named(LdapModule.USERS_BYINCLUDED_CACHE)
      Cache<String, ImmutableSet<String>> usersByIncluded,
      @Named(LdapModule.GROUPS_BYINCLUDE_CACHE)
      Cache<String, ImmutableSet<String>> groupsByInclude) {
    this.config = config;
    this.server = LdapRealm.optional(config, "server");
    this.username = LdapRealm.optional(config, "username");
    this.password = LdapRealm.optional(config, "password");
    this.referral = LdapRealm.optional(config, "referral");
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
    this.authentication = LdapRealm.optional(config, "authentication");
    String timeout = LdapRealm.optional(config, "readTimeout");
    if (timeout != null) {
      readTimeOutMillis =
          Long.toString(ConfigUtil.getTimeUnit(timeout, 0,
              TimeUnit.MILLISECONDS));
    } else {
      readTimeOutMillis = null;
    }
    this.groupsByInclude = groupsByInclude;
    this.groupsByIncluded = groupsByIncluded;
    this.usersByIncluded = usersByIncluded;
  }

  private Properties createContextProperties() {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LdapRealm.LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (server.startsWith("ldaps:") && !sslVerify) {
      Class<? extends SSLSocketFactory> factory = BlindSSLSocketFactory.class;
      env.put("java.naming.ldap.factory.socket", factory.getName());
    }
    if (readTimeOutMillis != null) {
      env.put("com.sun.jndi.ldap.read.timeout", readTimeOutMillis);
    }
    return env;
  }

  DirContext open() throws NamingException, LoginException {
    final Properties env = createContextProperties();
    env.put(Context.SECURITY_AUTHENTICATION, authentication != null ? authentication : "simple");
    env.put(Context.REFERRAL, referral != null ? referral : "ignore");
    if ("GSSAPI".equals(authentication)) {
      return kerberosOpen(env);
    } else {
      if (username != null) {
        env.put(Context.SECURITY_PRINCIPAL, username);
        env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
      }
      return new InitialDirContext(env);
    }
  }

  private DirContext kerberosOpen(final Properties env) throws LoginException,
      NamingException {
    LoginContext ctx = new LoginContext("KerberosLogin");
    ctx.login();
    Subject subject = ctx.getSubject();
    try {
      return Subject.doAs(subject, new PrivilegedExceptionAction<DirContext>() {
          @Override
          public DirContext run() throws NamingException {
            return new InitialDirContext(env);
          }
        });
    } catch (PrivilegedActionException e) {
      Throwables.propagateIfPossible(e.getException(), NamingException.class);
      Throwables.propagateIfPossible(e.getException(), RuntimeException.class);
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
    env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
    env.put(Context.REFERRAL, referral != null ? referral : "ignore");
    try {
      return new InitialDirContext(env);
    } catch (NamingException e) {
      throw new AccountException("Incorrect username or password", e);
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

  LdapQuery.Result findAccount(final Helper.LdapSchema schema,
      final DirContext ctx, final String username) throws NamingException,
      AccountException {
    final HashMap<String, String> params = new HashMap<>();
    params.put(LdapRealm.USERNAME, username);

    final List<LdapQuery.Result> res = new ArrayList<>();
    for (LdapQuery accountQuery : schema.accountQueryList) {
      res.addAll(accountQuery.query(ctx, params));
    }

    switch (res.size()) {
      case 0:
        throw new NoSuchUserException(username);

      case 1:
        return res.get(0);

      default:
        throw new AccountException("Duplicate users: " + username);
    }
  }

  List<LdapQuery.Result> findGroup(final Helper.LdapSchema schema,
      final DirContext ctx, final String groupName) throws NamingException {
    final List<LdapQuery.Result> res = new ArrayList<>();
    String filter = schema.groupQueryList.get(0).replacePatternParameter(
            LdapRealm.GROUPNAME, groupName);
    for (LdapQuery query : schema.groupQueryList) {
      res.addAll(query.query(ctx, filter));
    }
    return res;
  }

  Set<AccountGroup.UUID> queryForGroups(final DirContext ctx,
      final String username, LdapQuery.Result account)
      throws NamingException, AccountException {
    final LdapSchema schema = getSchema(ctx);
    final Set<String> groupDNs = new HashSet<>();

    if (!schema.groupMemberQueryList.isEmpty()) {
      final HashMap<String, String> params = new HashMap<>();

      if (account == null) {
        try {
          account = findAccount(schema, ctx, username);
        } catch (AccountException e) {
          LdapRealm.log.warn("Account " + username +
              " not found, assuming empty group membership");
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
      if (account == null) {
        try {
          account = findAccount(schema, ctx, username);
        } catch (AccountException e) {
          LdapRealm.log.warn("Account " + username +
              " not found, assuming empty group membership");
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
        }
      }
    }

    final Set<AccountGroup.UUID> actual = new HashSet<>();
    for (String dn : groupDNs) {
      actual.add(new AccountGroup.UUID(LDAP_UUID + dn));
    }

    if (actual.isEmpty()) {
      return Collections.emptySet();
    } else {
      return ImmutableSet.copyOf(actual);
    }
  }

  Set<String> usersOf(final String groupDN, final Helper.LdapSchema schema,
      final DirContext ctx) {
    return of(groupDN, usersByIncluded, schema, ctx);
  }

  Set<String> subgroupOf(final String groupDN, final Helper.LdapSchema schema,
      final DirContext ctx) {
    if (!allowNestedGroup(schema)) {
      return Collections.emptySet();
    }
    return of(groupDN, groupsByIncluded, schema, ctx);
  }

  private void recursivelyExpandGroups(final Set<String> groupDNs,
      final LdapSchema schema, final DirContext ctx, final String groupDN) {
    if (groupDNs.add(groupDN) && schema.accountMemberField != null) {
      ImmutableSet<String> cachedGroupDNs = groupsByInclude.getIfPresent(groupDN);
      if (cachedGroupDNs == null) {
        // Recursively identify the groups it is a member of.
        ImmutableSet.Builder<String> dns = ImmutableSet.builder();
        try {
          final Name compositeGroupName = new CompositeName().add(groupDN);
          final Attribute in =
              ctx.getAttributes(compositeGroupName).get(schema.accountMemberField);
          if (in != null) {
            final NamingEnumeration<?> groups = in.getAll();
            try {
              while (groups.hasMore()) {
                dns.add((String) groups.next());
              }
            } catch (PartialResultException e) {
            }
          }
        } catch (NamingException e) {
          LdapRealm.log.warn("Could not find group " + groupDN, e);
        }
        cachedGroupDNs = dns.build();
        groupsByInclude.put(groupDN, cachedGroupDNs);
      }
      for (String dn : cachedGroupDNs) {
        recursivelyExpandGroups(groupDNs, schema, ctx, dn);
      }
    }
  }

  private Set<String> of(final String groupDN,
      final Cache<String, ImmutableSet<String>> cache,
      final Helper.LdapSchema schema, final DirContext ctx) {
    Set<String> r = cache.getIfPresent(groupDN);
    if (r == null || r.isEmpty()) {
      cacheMembersOf(groupDN, schema, ctx);
    }
    return cache.getIfPresent(groupDN);
  }

  private void cacheMembersOf(final String groupDN,
      final Helper.LdapSchema schema, final DirContext ctx) {
    Attribute member = null;
    try {
      member =
          ctx.getAttributes(groupDN, new String[] {schema.memberField}).get(
              schema.memberField);
    } catch (NamingException e) {
      LdapRealm.log
          .warn(String.format("Could not find %s of %s", schema.memberField,
              groupDN), e);
    }
    List<String> v = valuesOf(member);
    if (v == null || v.isEmpty()) {
      return;
    }

    if (!allowNestedGroup(schema)) {
      usersByIncluded.put(groupDN, ImmutableSet.copyOf(v));
      return;
    }

    Set<String> users = Sets.newHashSet();
    Set<String> subgrups = Sets.newHashSet();
    for (String dn : v) {
      Attributes entry;
      try {
        entry = ctx.getAttributes(dn, getReturnAttrIds(schema));
      } catch (NamingException e) {
        LdapRealm.log.warn(
            String.format("Could not find required attributes for %s", dn), e);
        return;
      }
      if (schema.userFilter.accept(entry)) {
        String username = null;
        try {
          username = get(schema.accountLoginName, entry);

        } catch (NamingException e) {
          LdapRealm.log.warn(String.format(
              "Could not find the value of attribute %s from %s",
              schema.accountLoginName, dn), e);
        }
        if (username != null) {
          users.add(username);
        }
        continue;
      }
      if (schema.subgroupFilter.accept(entry)) {
        subgrups.add(dn);
      }
    }

    if (!users.isEmpty()) {
      usersByIncluded.put(groupDN, ImmutableSet.copyOf(users));
    }
    if (!subgrups.isEmpty()) {
      groupsByIncluded.put(groupDN, ImmutableSet.copyOf(subgrups));
    }
  }

  private String[] getReturnAttrIds(final Helper.LdapSchema schema) {
    String[] returnAttrIds =
        new String[schema.userFilter.attrIds().size()
            + schema.subgroupFilter.attrIds().size() + 2];
    Set<String> ids = Sets.newHashSet();
    ids.addAll(schema.userFilter.attrIds());
    ids.addAll(schema.subgroupFilter.attrIds());
    ids.add(schema.accountLoginName);
    ids.toArray(returnAttrIds);
    return returnAttrIds;
  }

  private boolean allowNestedGroup(final Helper.LdapSchema schema) {
    // RFC 2307 does not allow nested group.
    // Active Directory allow nested group.
    return schema.subgroupFilter != null;
  }

  class LdapSchema {
    final LdapType type;

    final ParameterizedString accountFullName;
    final ParameterizedString accountEmailAddress;
    final ParameterizedString accountSshUserName;
    final String accountMemberField;
    final List<LdapQuery> accountQueryList;

    final ParameterizedString groupName;
    final List<LdapQuery> groupMemberQueryList;
    final List<LdapQuery> groupQueryList;

    final Filter userFilter;
    final Filter subgroupFilter;
    final String accountLoginName ;
    final String memberField;

    LdapSchema(final DirContext ctx) {
      type = discoverLdapType(ctx);
      groupMemberQueryList = new ArrayList<>();
      groupQueryList = new ArrayList<>();
      accountQueryList = new ArrayList<>();

      Filter f = LdapRealm.optionalUserFiler(config);
      userFilter = (f != null) ? f : type.userFilter();
      f = LdapRealm.optionalGroupFiler(config);
      subgroupFilter = (f != null) ? f : type.groupFilter();

      accountLoginName =
          LdapRealm.optdef(config, "accountLoginName", type.accountLoginName());
      memberField=LdapRealm.optdef(config, "memberField", type.memberField());

      final Set<String> accountAtts = new HashSet<>();

      final SearchScope groupScope = LdapRealm.scope(config, "groupScope");

      groupName = LdapRealm.paramString(config, "groupName", type.groupName());
      final String groupMemberPattern =
          LdapRealm.optdef(config, "groupMemberPattern", type.groupMemberPattern());
      // Group member query
      for (String groupBase : LdapRealm.optionalList(config, "groupBase")) {
        if (groupMemberPattern != null) {
          final LdapQuery groupMemberQuery =
              new LdapQuery(groupBase, groupScope, new ParameterizedString(
                  groupMemberPattern), Collections.<String> emptySet());
          if (groupMemberQuery.getParameters().isEmpty()) {
            throw new IllegalArgumentException(
                "No variables in ldap.groupMemberPattern");
          }

          for (final String name : groupMemberQuery.getParameters()) {
            accountAtts.add(name);
          }

          groupMemberQueryList.add(groupMemberQuery);
        }
        // Group query
        final LdapQuery groupQuery =
            new LdapQuery(groupBase, groupScope,
                LdapRealm.paramString(config, "groupPattern", type.groupPattern()),
                new HashSet<>(groupName.getParameterNames()));
        if (groupQuery.getParameters().isEmpty()) {
          throw new IllegalArgumentException(
              "No variables in ldap.groupPattern");
        }
        groupQueryList.add(groupQuery);
      }

      // Account query
      accountFullName =
          LdapRealm.paramString(config, "accountFullName", type.accountFullName());
      if (accountFullName != null) {
        accountAtts.addAll(accountFullName.getParameterNames());
      }
      accountEmailAddress =
          LdapRealm.paramString(config, "accountEmailAddress", type
              .accountEmailAddress());
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
        accountAtts.add(accountMemberField);
      }

      final SearchScope accountScope = LdapRealm.scope(config, "accountScope");
      final String accountPattern =
          LdapRealm.reqdef(config, "accountPattern", type.accountPattern());

      for (String accountBase : LdapRealm.requiredList(config, "accountBase")) {
        final LdapQuery accountQuery =
            new LdapQuery(accountBase, accountScope, new ParameterizedString(
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
        LdapRealm.log.warn("Cannot discover type of LDAP server at " + server
            + ", assuming the server is RFC 2307 compliant.", e);
        return LdapType.RFC_2307;
      }
    }
  }
}
