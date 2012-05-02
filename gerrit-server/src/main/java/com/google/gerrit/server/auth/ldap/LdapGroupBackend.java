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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ExtGroup;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.auth.ldap.Helper.LdapSchema;
import com.google.gerrit.server.cache.Cache;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

/**
 * Implementation of GroupBackend for the LDAP group system.
 */
public class LdapGroupBackend implements GroupBackend {
  private static final Logger log = LoggerFactory.getLogger(LdapGroupBackend.class);

  private static final String LDAP_NAME = "ldapgroup/";

  private static final String GROUPNAME = "groupname";

  private final Helper helper;
  private final Cache<String, Set<AccountGroup.UUID>> membershipCache;

  @Inject
  LdapGroupBackend(
      Helper helper,
      @Named(LdapModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.UUID>> membershipCache) {
    this.helper = helper;
    this.membershipCache = membershipCache;
  }

  private static boolean isLdapUUID(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(Helper.LDAP_UUID);
  }

  private GroupReference groupReference(LdapQuery.Result res) throws NamingException {
    String name = Objects.firstNonNull(res.get("cn"), res.getDN());
    return new GroupReference(
        new AccountGroup.UUID(Helper.LDAP_UUID + res.getDN()), LDAP_NAME + name);
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return isLdapUUID(uuid);
  }

  @Override
  public ExtGroup get(final AccountGroup.UUID uuid) {
    Preconditions.checkArgument(handles(uuid));
    // TODO(cranger): lookup the AccountState
    AccountState user = null;
    if (!membershipsOf(user).contains(uuid)) {
      return null;
    }

    final String name = LDAP_NAME + uuid.get().substring(Helper.LDAP_UUID.length());
    return new ExtGroup() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isVisibleToAll() {
        return false;
      }

      @Override
      public boolean hasOwnerGroupId() {
        return false;
      }

      @Override
      public AccountGroup.Id getOwnerGroupId() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name) {
    AccountGroup.UUID uuid = new AccountGroup.UUID(name);
    if (isLdapUUID(uuid)) {
      ExtGroup g = get(uuid);
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
  public GroupMembership membershipsOf(AccountState user) {
    String id = findId(user.getExternalIds());
    if (id == null) {
      return GroupMembership.EMPTY;
    }

    return new ListGroupMembership(membershipCache.get(id));
  }

  private static String findId(final Collection<AccountExternalId> ids) {
    for (final AccountExternalId i : ids) {
      if (i.isScheme(AccountExternalId.SCHEME_GERRIT)) {
        return i.getSchemeRest();
      }
    }
    return null;
  }

  private Set<GroupReference> suggestLdap(String name) {
    Set<GroupReference> out = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    try {
      final DirContext ctx = helper.open();
      try {
        final LdapSchema schema = helper.getSchema(ctx);
        final ParameterizedString filter = ParameterizedString.asis(
            schema.groupPattern.replace(GROUPNAME, name).toString());
        final Set<String> returnAttrs = Collections.<String>emptySet();
        final Map<String, String> params = Collections.emptyMap();
        for (String groupBase : schema.groupBases) {
          LdapQuery query = new LdapQuery(
              groupBase, schema.groupScope, filter, returnAttrs);
          for (LdapQuery.Result res : query.query(ctx, params)) {
            out.add(groupReference(res));
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
}
