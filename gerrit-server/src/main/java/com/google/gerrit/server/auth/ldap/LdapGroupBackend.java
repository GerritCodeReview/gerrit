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
import static com.google.gerrit.server.account.GroupBackends.getComparator;
import static com.google.gerrit.server.auth.ldap.Helper.LDAP_UUID;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_EXIST_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.USERNAME_CACHE;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupInfoCacheFactory;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.auth.ldap.Helper.LdapSchema;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.login.LoginException;

/**
 * Implementation of GroupBackend for the LDAP group system.
 */
@Singleton
public class LdapGroupBackend implements GroupBackend {
  private static final Logger log = LoggerFactory.getLogger(LdapGroupBackend.class);

  public static final String LDAP_NAME = "ldap/";

  private final Helper helper;
  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;
  private final LoadingCache<String, Optional<Account.Id>> usernameCache;
  private final LoadingCache<String, Boolean> existsCache;
  private final ProjectCache projectCache;
  private final Provider<CurrentUser> userProvider;
  private final AccountInfoCacheFactory aic;
  private final GroupInfoCacheFactory gic;
  private final GroupControl.Factory groupControlFactory;

  @Inject
  LdapGroupBackend(
      Helper helper,
      AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      GroupInfoCacheFactory.Factory groupInfoCacheFactory,
      GroupControl.Factory groupControlFactory,
      @Named(GROUP_CACHE) LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      @Named(GROUP_EXIST_CACHE) LoadingCache<String, Boolean> existsCache,
      @Named(USERNAME_CACHE) final LoadingCache<String, Optional<Account.Id>> usernameCache,
      ProjectCache projectCache,
      Provider<CurrentUser> userProvider) {
    this.helper = helper;
    this.membershipCache = membershipCache;
    this.usernameCache = usernameCache;
    this.projectCache = projectCache;
    this.existsCache = existsCache;
    this.userProvider = userProvider;
    this.aic = accountInfoCacheFactory.create();
    this.gic = groupInfoCacheFactory.create(this);
    this.groupControlFactory = groupControlFactory;
  }

  private boolean isLdapUUID(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(LDAP_UUID);
  }

  private static GroupReference groupReference(ParameterizedString p,
      LdapQuery.Result res) throws NamingException {
    return new GroupReference(
        new AccountGroup.UUID(LDAP_UUID + res.getDN()),
        LDAP_NAME + LdapRealm.apply(p, res));
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
  public GroupDescription.Basic get(final AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null;
    }

    String groupDn = getDn(uuid);
    CurrentUser user = userProvider.get();
    if (!(user.isIdentifiedUser())
        || !membershipsOf((IdentifiedUser) user).contains(uuid)) {
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

    try {
      final Set<AccountGroup.UUID> groups = membershipCache.get(id);
      return new ListGroupMembership(groups) {
        @Override
        public Set<AccountGroup.UUID> getKnownGroups() {
          Set<AccountGroup.UUID> g = Sets.newHashSet(groups);
          g.retainAll(projectCache.guessRelevantGroupUUIDs());
          return g;
        }
      };
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup membershipsOf %s in LDAP", id), e);
      return GroupMembership.EMPTY;
    }
  }

  @Override
  public List<Account.Id> loadMembers(AccountGroup.UUID uuid, final boolean sort) {
    try {
      DirContext ctx = helper.open();
      LdapSchema schema = helper.getSchema(ctx);
      try {
        groupControlFactory.validateFor(uuid);
        Set<String> usernames = helper.usersOf(getDn(uuid), schema, ctx);
        if (usernames == null || usernames.isEmpty()) {
          return null;
        }
        Set<Account.Id> ids =
            Sets.newHashSet(Iterables.transform(usernameCache.getAll(usernames)
                .values(), new Function<Optional<Account.Id>, Account.Id>() {
              @Override
              public Account.Id apply(Optional<Account.Id> input) {
                if (input != null) {
                  Account.Id id = input.orNull();
                  if (sort) {
                    aic.want(id);
                  }
                  return id;
                }
                return null;
              }
            }));
        ids.remove(null);
        if (ids.isEmpty()) {
          return null;
        }
        List<Account.Id> r = Lists.newArrayList(ids);
        if (sort) {
          Collections.sort(r, getComparator(aic));
        }
        return r;
      } catch (NoSuchGroupException e) {
        log.warn(String.format("Cannot lookup members of %s", uuid), e);
        return null;
      } catch (ExecutionException e) {
        log.warn("Cannot lookup account in LDAP", e);
        return null;
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (LoginException | NamingException e) {
      log.warn("Cannot open LDAP query handle", e);
      return null;
    }
  }

  @Override
  public List<AccountGroup.UUID> loadIncludes(AccountGroup.UUID uuid,
      NameKey project, final boolean sort) {
    try {
      DirContext ctx = helper.open();
      LdapSchema schema = helper.getSchema(ctx);
      try {
        groupControlFactory.validateFor(uuid);
        Set<String> subgDNs = helper.subgroupOf(getDn(uuid), schema, ctx);
        if (subgDNs == null || subgDNs.isEmpty()) {
          return null;
        }
        List<AccountGroup.UUID> r =
            Lists.transform(Lists.newArrayList(subgDNs),
                new Function<String, AccountGroup.UUID>() {
                  @Override
                  public AccountGroup.UUID apply(String input) {
                    AccountGroup.UUID id =
                        new AccountGroup.UUID(LDAP_UUID + input);
                    if (sort) {
                      gic.want(id);
                    }
                    return id;
                  }
                });
        r.remove(null);
        if (r.isEmpty()) {
          return null;
        }
        if (sort) {
          Collections.sort(r, getComparator(gic));
        }
        return r;
      } catch (NoSuchGroupException e) {
        log.warn(String.format("Cannot lookup members of %s", uuid), e);
        return null;
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (LoginException | NamingException e) {
      log.warn("Cannot open LDAP query handle", e);
      return null;
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

  private Set<GroupReference> suggestLdap(String groupName) {
    if (groupName.isEmpty()) {
      return Collections.emptySet();
    }

    Set<GroupReference> out = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    // Do exact lookups until there are at least 3 characters.
    groupName =
        Rdn.escapeValue(groupName) + ((groupName.length() >= 3) ? "*" : "");
    try {
      DirContext ctx = helper.open();
      LdapSchema schema = helper.getSchema(ctx);
      try {
        for (LdapQuery.Result res : helper.findGroup(schema, ctx, groupName)) {
          out.add(groupReference(schema.groupName, res));
        }
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (LoginException | NamingException e) {
      log.warn("Cannot query LDAP for groups matching requested name", e);
    }
    return out;
  }

  private String getDn(AccountGroup.UUID uuid) {
    return uuid.get().substring(LDAP_UUID.length());
  }
}
