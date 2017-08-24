// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.server.account.GroupBackends.GROUP_REF_NAME_COMPARATOR;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.auth.ldap.Helper.LDAP_UUID;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_EXIST_CACHE;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.auth.ldap.Helper.LdapSchema;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.login.LoginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of GroupBackend for the LDAP group system. */
public class LdapGroupBackend implements GroupBackend {
  static final Logger log = LoggerFactory.getLogger(LdapGroupBackend.class);

  private static final String LDAP_NAME = "ldap/";
  private static final String GROUPNAME = "groupname";

  private final Helper helper;
  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;
  private final LoadingCache<String, Boolean> existsCache;
  private final ProjectCache projectCache;
  private final Provider<CurrentUser> userProvider;

  @Inject
  LdapGroupBackend(
      Helper helper,
      @Named(GROUP_CACHE) LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      @Named(GROUP_EXIST_CACHE) LoadingCache<String, Boolean> existsCache,
      ProjectCache projectCache,
      Provider<CurrentUser> userProvider) {
    this.helper = helper;
    this.membershipCache = membershipCache;
    this.projectCache = projectCache;
    this.existsCache = existsCache;
    this.userProvider = userProvider;
  }

  private boolean isLdapUUID(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(LDAP_UUID);
  }

  private static GroupReference groupReference(ParameterizedString p, LdapQuery.Result res)
      throws NamingException {
    return new GroupReference(
        new AccountGroup.UUID(LDAP_UUID + res.getDN()), LDAP_NAME + LdapRealm.apply(p, res));
  }

  private static String cnFor(String dn) {
    try {
      LdapName name = new LdapName(dn);
      if (!name.isEmpty()) {
        String cn = name.get(name.size() - 1);
        int index = cn.indexOf('=');
        if (index >= 0) {
          cn = cn.substring(index + 1);
        }
        return cn;
      }
    } catch (InvalidNameException e) {
      log.warn("Cannot parse LDAP dn for cn", e);
    }
    return dn;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return isLdapUUID(uuid);
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null;
    }

    String groupDn = uuid.get().substring(LDAP_UUID.length());
    CurrentUser user = userProvider.get();
    if (!(user.isIdentifiedUser()) || !membershipsOf(user.asIdentifiedUser()).contains(uuid)) {
      try {
        if (!existsCache.get(groupDn)) {
          return null;
        }
      } catch (ExecutionException e) {
        log.warn(String.format("Cannot lookup group %s in LDAP", groupDn), e);
        return null;
      }
    }

    final String name = LDAP_NAME + cnFor(groupDn);
    return new GroupDescription.Basic() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      @Nullable
      public String getEmailAddress() {
        return null;
      }

      @Override
      @Nullable
      public String getUrl() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectControl project) {
    AccountGroup.UUID uuid = new AccountGroup.UUID(name);
    if (isLdapUUID(uuid)) {
      GroupDescription.Basic g = get(uuid);
      if (g == null) {
        return Collections.emptySet();
      }
      return Collections.singleton(GroupReference.forGroup(g));
    } else if (name.startsWith(LDAP_NAME)) {
      return suggestLdap(name.substring(LDAP_NAME.length()));
    }
    return Collections.emptySet();
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    String id = findId(user.state().getExternalIds());
    if (id == null) {
      return GroupMembership.EMPTY;
    }
    return new LdapGroupMembership(membershipCache, projectCache, id);
  }

  private static String findId(Collection<ExternalId> extIds) {
    for (ExternalId extId : extIds) {
      if (extId.isScheme(SCHEME_GERRIT)) {
        return extId.key().id();
      }
    }
    return null;
  }

  private Set<GroupReference> suggestLdap(String name) {
    if (name.isEmpty()) {
      return Collections.emptySet();
    }

    Set<GroupReference> out = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    try {
      DirContext ctx = helper.open();
      try {
        // Do exact lookups until there are at least 3 characters.
        name = Rdn.escapeValue(name) + ((name.length() >= 3) ? "*" : "");
        LdapSchema schema = helper.getSchema(ctx);
        ParameterizedString filter =
            ParameterizedString.asis(schema.groupPattern.replace(GROUPNAME, name).toString());
        Set<String> returnAttrs = new HashSet<>(schema.groupName.getParameterNames());
        Map<String, String> params = Collections.emptyMap();
        for (String groupBase : schema.groupBases) {
          LdapQuery query = new LdapQuery(groupBase, schema.groupScope, filter, returnAttrs);
          for (LdapQuery.Result res : query.query(ctx, params)) {
            out.add(groupReference(schema.groupName, res));
          }
        }
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (NamingException | LoginException e) {
      log.warn("Cannot query LDAP for groups matching requested name", e);
    }
    return out;
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    return handles(uuid) && helper.groupsVisibleToAll();
  }
}
