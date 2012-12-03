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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Universal implementation of the GroupBackend that works with the injected
 * set of GroupBackends.
 */
@Singleton
public class UniversalGroupBackend implements GroupBackend {
  private static final Logger log =
      LoggerFactory.getLogger(UniversalGroupBackend.class);

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
    GroupBackend b = backend(uuid);
    if (b == null) {
      log.warn("Unknown GroupBackend for UUID: " + uuid);
      return null;
    }
    return b.get(uuid);
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectControl project) {
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
     ImmutableMap.Builder<GroupBackend, GroupMembership> builder =
         ImmutableMap.builder();
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
     GroupMembership m = membership(uuid);
     if (m == null) {
       log.warn("Unknown GroupMembership for UUID: " + uuid);
       return false;
     }
     return m.contains(uuid);
   }

    @Override
    public boolean containsAnyOf(Iterable<AccountGroup.UUID> uuids) {
      Multimap<GroupMembership, AccountGroup.UUID> lookups =
          ArrayListMultimap.create();
      for (AccountGroup.UUID uuid : uuids) {
        GroupMembership m = membership(uuid);
        if (m == null) {
          log.warn("Unknown GroupMembership for UUID: " + uuid);
          continue;
        }
        lookups.put(m, uuid);
      }
      for (Map.Entry<GroupMembership, Collection<AccountGroup.UUID>> entry
          : lookups .asMap().entrySet()) {
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
      Multimap<GroupMembership, AccountGroup.UUID> lookups =
          ArrayListMultimap.create();
      for (AccountGroup.UUID uuid : uuids) {
        GroupMembership m = membership(uuid);
        if (m == null) {
          log.warn("Unknown GroupMembership for UUID: " + uuid);
          continue;
        }
        lookups.put(m, uuid);
      }
      Set<AccountGroup.UUID> groups = Sets.newHashSet();
      for (Map.Entry<GroupMembership, Collection<AccountGroup.UUID>> entry
          : lookups.asMap().entrySet()) {
        groups.addAll(entry.getKey().intersection(entry.getValue()));
      }
      return groups;
    }

    @Override
    public Set<AccountGroup.UUID> getKnownGroups() {
      Set<AccountGroup.UUID> groups = Sets.newHashSet();
      for (GroupMembership m : memberships.values()) {
        groups.addAll(m.getKnownGroups());
      }
      return groups;
    }
  }
}
