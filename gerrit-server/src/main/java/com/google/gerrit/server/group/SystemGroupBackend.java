// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AbstractGroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SystemGroupBackend extends AbstractGroupBackend {
  public static final String SYSTEM_GROUP_SCHEME = "global:";

  /** Common UUID assigned to the "Anonymous Users" group. */
  public static final AccountGroup.UUID ANONYMOUS_USERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Anonymous-Users");

  /** Common UUID assigned to the "Registered Users" group. */
  public static final AccountGroup.UUID REGISTERED_USERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Registered-Users");

  /** Common UUID assigned to the "Project Owners" placeholder group. */
  public static final AccountGroup.UUID PROJECT_OWNERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Project-Owners");

  /** Common UUID assigned to the "Change Owner" placeholder group. */
  public static final AccountGroup.UUID CHANGE_OWNER =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Change-Owner");

  private static final AccountGroup.UUID[] all = {
    ANONYMOUS_USERS, REGISTERED_USERS, PROJECT_OWNERS, CHANGE_OWNER,
  };

  public static boolean isSystemGroup(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(SYSTEM_GROUP_SCHEME);
  }

  public static boolean isAnonymousOrRegistered(GroupReference ref) {
    return isAnonymousOrRegistered(ref.getUUID());
  }

  public static boolean isAnonymousOrRegistered(AccountGroup.UUID uuid) {
    return ANONYMOUS_USERS.equals(uuid) || REGISTERED_USERS.equals(uuid);
  }

  private final ImmutableSet<String> reservedNames;
  private final SortedMap<String, GroupReference> names;
  private final ImmutableMap<AccountGroup.UUID, GroupReference> uuids;

  @Inject
  @VisibleForTesting
  public SystemGroupBackend(@GerritServerConfig Config cfg) {
    SortedMap<String, GroupReference> n = new TreeMap<>();
    ImmutableMap.Builder<AccountGroup.UUID, GroupReference> u = ImmutableMap.builder();

    ImmutableSet.Builder<String> reservedNamesBuilder = ImmutableSet.builder();
    for (AccountGroup.UUID uuid : all) {
      int c = uuid.get().indexOf(':');
      String defaultName = uuid.get().substring(c + 1).replace('-', ' ');
      reservedNamesBuilder.add(defaultName);
      String configuredName = cfg.getString("groups", uuid.get(), "name");
      GroupReference ref =
          new GroupReference(uuid, MoreObjects.firstNonNull(configuredName, defaultName));
      n.put(ref.getName().toLowerCase(Locale.US), ref);
      u.put(ref.getUUID(), ref);
    }
    reservedNames = reservedNamesBuilder.build();
    names = Collections.unmodifiableSortedMap(n);
    uuids = u.build();
  }

  public GroupReference getGroup(AccountGroup.UUID uuid) {
    return checkNotNull(uuids.get(uuid), "group %s not found", uuid.get());
  }

  public Set<String> getNames() {
    return names.values().stream().map(r -> r.getName()).collect(toSet());
  }

  public Set<String> getReservedNames() {
    return reservedNames;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return isSystemGroup(uuid);
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    final GroupReference ref = uuids.get(uuid);
    if (ref == null) {
      return null;
    }
    return new GroupDescription.Basic() {
      @Override
      public String getName() {
        return ref.getName();
      }

      @Override
      public AccountGroup.UUID getGroupUUID() {
        return ref.getUUID();
      }

      @Override
      public String getUrl() {
        return null;
      }

      @Override
      public String getEmailAddress() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectControl project) {
    String nameLC = name.toLowerCase(Locale.US);
    SortedMap<String, GroupReference> matches = names.tailMap(nameLC);
    if (matches.isEmpty()) {
      return Collections.emptyList();
    }

    List<GroupReference> r = new ArrayList<>(matches.size());
    for (Map.Entry<String, GroupReference> e : matches.entrySet()) {
      if (e.getKey().startsWith(nameLC)) {
        r.add(e.getValue());
      } else {
        break;
      }
    }
    return r;
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return new ListGroupMembership(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS));
  }

  public static class NameCheck implements StartupCheck {
    private final Config cfg;
    private final GroupCache groupCache;

    @Inject
    NameCheck(@GerritServerConfig Config cfg, GroupCache groupCache) {
      this.cfg = cfg;
      this.groupCache = groupCache;
    }

    @Override
    public void check() throws StartupException {
      Map<AccountGroup.UUID, String> configuredNames = new HashMap<>();
      Map<String, AccountGroup.UUID> byLowerCaseConfiguredName = new HashMap<>();
      for (AccountGroup.UUID uuid : all) {
        String configuredName = cfg.getString("groups", uuid.get(), "name");
        if (configuredName != null) {
          configuredNames.put(uuid, configuredName);
          byLowerCaseConfiguredName.put(configuredName.toLowerCase(Locale.US), uuid);
        }
      }
      if (configuredNames.isEmpty()) {
        return;
      }
      for (AccountGroup g : groupCache.all()) {
        String name = g.getName().toLowerCase(Locale.US);
        if (byLowerCaseConfiguredName.keySet().contains(name)) {
          AccountGroup.UUID uuidSystemGroup = byLowerCaseConfiguredName.get(name);
          throw new StartupException(
              String.format(
                  "The configured name '%s' for system group '%s' is ambiguous"
                      + " with the name '%s' of existing group '%s'."
                      + " Please remove/change the value for groups.%s.name in"
                      + " gerrit.config.",
                  configuredNames.get(uuidSystemGroup),
                  uuidSystemGroup.get(),
                  g.getName(),
                  g.getGroupUUID().get(),
                  uuidSystemGroup.get()));
        }
      }
    }
  }
}
