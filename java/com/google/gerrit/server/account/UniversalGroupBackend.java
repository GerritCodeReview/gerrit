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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.GroupBackends.GROUP_REF_NAME_COMPARATOR;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Universal implementation of the GroupBackend that works with the injected set of GroupBackends.
 */
@Singleton
public class UniversalGroupBackend implements GroupBackend {
  private static final Logger log = LoggerFactory.getLogger(UniversalGroupBackend.class);

  private final DynamicSet<GroupBackend> backends;

  @Inject
  UniversalGroupBackend(DynamicSet<GroupBackend> backends) {
    this.backends = backends;
  }

  @Nullable
  private GroupBackend backend(AccountGroup.UUID uuid) {
    if (uuid != null) {
      for (GroupBackend g : backends) {
        if (g.handles(uuid)) {
          return g;
        }
      }
    }
    return null;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return backend(uuid) != null;
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    if (uuid == null) {
      return null;
    }
    GroupBackend b = backend(uuid);
    if (b == null) {
      log.debug("Unknown GroupBackend for UUID: " + uuid);
      return null;
    }
    return b.get(uuid);
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    Set<GroupReference> groups = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    for (GroupBackend g : backends) {
      groups.addAll(g.suggest(name, project));
    }
    return groups;
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return new UniversalGroupMembership(user);
  }

  private class UniversalGroupMembership implements GroupMembership {
    private final Map<GroupBackend, GroupMembership> memberships;

    private UniversalGroupMembership(IdentifiedUser user) {
      ImmutableMap.Builder<GroupBackend, GroupMembership> builder = ImmutableMap.builder();
      for (GroupBackend g : backends) {
        builder.put(g, g.membershipsOf(user));
      }
      this.memberships = builder.build();
    }

    @Nullable
    private GroupMembership membership(AccountGroup.UUID uuid) {
      if (uuid != null) {
        for (Map.Entry<GroupBackend, GroupMembership> m : memberships.entrySet()) {
          if (m.getKey().handles(uuid)) {
            return m.getValue();
          }
        }
      }
      return null;
    }

    @Override
    public boolean contains(AccountGroup.UUID uuid) {
      if (uuid == null) {
        return false;
      }
      GroupMembership m = membership(uuid);
      if (m == null) {
        log.debug("Unknown GroupMembership for UUID: " + uuid);
        return false;
      }
      return m.contains(uuid);
    }

    @Override
    public boolean containsAnyOf(Iterable<AccountGroup.UUID> uuids) {
      ListMultimap<GroupMembership, AccountGroup.UUID> lookups =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (AccountGroup.UUID uuid : uuids) {
        if (uuid == null) {
          continue;
        }
        GroupMembership m = membership(uuid);
        if (m == null) {
          log.debug("Unknown GroupMembership for UUID: " + uuid);
          continue;
        }
        lookups.put(m, uuid);
      }
      for (Map.Entry<GroupMembership, Collection<AccountGroup.UUID>> entry :
          lookups.asMap().entrySet()) {
        GroupMembership m = entry.getKey();
        Collection<AccountGroup.UUID> ids = entry.getValue();
        if (ids.size() == 1) {
          if (m.contains(Iterables.getOnlyElement(ids))) {
            return true;
          }
        } else if (m.containsAnyOf(ids)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> uuids) {
      ListMultimap<GroupMembership, AccountGroup.UUID> lookups =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (AccountGroup.UUID uuid : uuids) {
        if (uuid == null) {
          continue;
        }
        GroupMembership m = membership(uuid);
        if (m == null) {
          log.debug("Unknown GroupMembership for UUID: " + uuid);
          continue;
        }
        lookups.put(m, uuid);
      }
      Set<AccountGroup.UUID> groups = new HashSet<>();
      for (Map.Entry<GroupMembership, Collection<AccountGroup.UUID>> entry :
          lookups.asMap().entrySet()) {
        groups.addAll(entry.getKey().intersection(entry.getValue()));
      }
      return groups;
    }

    @Override
    public Set<AccountGroup.UUID> getKnownGroups() {
      Set<AccountGroup.UUID> groups = new HashSet<>();
      for (GroupMembership m : memberships.values()) {
        groups.addAll(m.getKnownGroups());
      }
      return groups;
    }
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    for (GroupBackend g : backends) {
      if (g.handles(uuid)) {
        return g.isVisibleToAll(uuid);
      }
    }
    return false;
  }

  public static class ConfigCheck implements StartupCheck {
    private final Config cfg;
    private final UniversalGroupBackend universalGroupBackend;

    @Inject
    ConfigCheck(@GerritServerConfig Config cfg, UniversalGroupBackend groupBackend) {
      this.cfg = cfg;
      this.universalGroupBackend = groupBackend;
    }

    @Override
    public void check() throws StartupException {
      String invalid =
          cfg.getSubsections("groups")
              .stream()
              .filter(
                  sub -> {
                    AccountGroup.UUID uuid = new AccountGroup.UUID(sub);
                    GroupBackend groupBackend = universalGroupBackend.backend(uuid);
                    return groupBackend == null || groupBackend.get(uuid) == null;
                  })
              .map(u -> "'" + u + "'")
              .collect(joining(","));

      if (!invalid.isEmpty()) {
        throw new StartupException(
            String.format(
                "Subsections for 'groups' in gerrit.config must be valid group"
                    + " UUIDs. The following group UUIDs could not be resolved: "
                    + invalid
                    + " Please remove/fix these 'groups' subsections in"
                    + " gerrit.config."));
      }
    }
  }
}
