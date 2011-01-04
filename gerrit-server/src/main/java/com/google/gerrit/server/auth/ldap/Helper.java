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

import com.google.gerrit.common.data.ParamertizedString;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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

@Singleton class Helper {
  private final GroupCache groupCache;
  private final Config config;
  private final String server;
  private final String username;
  private final String password;
  private final String referral;
  private final boolean sslVerify;
  private volatile LdapSchema ldapSchema;

  @Inject
  Helper(@GerritServerConfig final Config config, final GroupCache groupCache) {
    this.groupCache = groupCache;
    this.config = config;
    this.server = LdapRealm.required(config, "server");
    this.username = LdapRealm.optional(config, "username");
    this.password = LdapRealm.optional(config, "password");
    this.referral = LdapRealm.optional(config, "referral");
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
  }

  private Properties createContextProperties() {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LdapRealm.LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (server.startsWith("ldaps:") && !sslVerify) {
      Class<? extends SSLSocketFactory> factory = BlindSSLSocketFactory.class;
      env.put("java.naming.ldap.factory.socket", factory.getName());
    }
    return env;
  }

  DirContext open() throws NamingException {
    final Properties env = createContextProperties();
    if (username != null) {
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, username);
      env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
      env.put(Context.REFERRAL, referral != null ? referral : "ignore");
    }
    return new InitialDirContext(env);
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
    final HashMap<String, String> params = new HashMap<String, String>();
    params.put(LdapRealm.USERNAME, username);

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

  Set<AccountGroup.Id> queryForGroups(final DirContext ctx,
      final String username, LdapQuery.Result account)
      throws NamingException, AccountException {
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

      params.put(LdapRealm.USERNAME, username);

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
        try {
          while (groups.hasMore()) {
            final String nextDN = (String) groups.next();
            recursivelyExpandGroups(groupDNs, schema, ctx, nextDN);
          }
        } catch (PartialResultException e) {}
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
    if (groupDNs.add(groupDN) && schema.accountMemberField != null) {
      // Recursively identify the groups it is a member of.
      //
      try {
        final Name compositeGroupName = new CompositeName().add(groupDN);
        final Attribute in =
            ctx.getAttributes(compositeGroupName).get(schema.accountMemberField);
        if (in != null) {
          final NamingEnumeration<?> groups = in.getAll();
          try {
            while (groups.hasMore()) {
              final String nextDN = (String) groups.next();
              recursivelyExpandGroups(groupDNs, schema, ctx, nextDN);
            }
          } catch (PartialResultException e) {}
        }
      } catch (NamingException e) {
        LdapRealm.log.warn("Could not find group " + groupDN, e);
      }
    }
  }

  class LdapSchema {
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

      groupBases = LdapRealm.optionalList(config, "groupBase");
      groupScope = LdapRealm.scope(config, "groupScope");
      groupPattern = LdapRealm.paramString(config, "groupPattern", type.groupPattern());
      final String groupMemberPattern =
          LdapRealm.optdef(config, "groupMemberPattern", type.groupMemberPattern());

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
            if (!LdapRealm.USERNAME.equals(name)) {
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
        LdapRealm.log.warn("Cannot discover type of LDAP server at " + server
            + ", assuming the server is RFC 2307 compliant.", e);
        return LdapType.RFC_2307;
      }
    }
  }
}
