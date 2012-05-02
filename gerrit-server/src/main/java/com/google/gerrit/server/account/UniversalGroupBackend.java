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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ExtGroup;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Universal implementation of the GroupBackend that works with the injected
 * set of GroupBackends.
 */
@Singleton
public class UniversalGroupBackend implements GroupBackend {
  private final Set<GroupBackend> backends;

  @Inject
  UniversalGroupBackend(Set<GroupBackend> backends) {
    this.backends = backends;
  }

  @Nullable
  private GroupBackend backend(AccountGroup.UUID uuid) {
    for (GroupBackend g : backends) {
      if (g.handles(uuid)) {
        return g;
      }
    }
    return null;
  }

  private GroupBackend requireBackend(AccountGroup.UUID uuid) {
    GroupBackend g = backend(uuid);
    if (g == null) {
      throw new IllegalArgumentException("unhandled group UUID: " + uuid);
    }
    return g;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return backend(uuid) != null;
  }

  @Override
  public ExtGroup get(AccountGroup.UUID uuid) {
    return requireBackend(uuid).get(uuid);
  }

  @Override
  public Collection<GroupReference> suggest(String name) {
    Set<GroupReference> groups = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    for (GroupBackend g : backends) {
      groups.addAll(g.suggest(name));
    }
    return groups;
  }

  @Override
  public GroupMembership membershipsOf(AccountState user) {
    return new UniversalGroupMembership(user);
  }

  private class UniversalGroupMembership implements GroupMembership {
   private final Map<GroupBackend, GroupMembership> memberships;

   private UniversalGroupMembership(AccountState user) {
     ImmutableMap.Builder<GroupBackend, GroupMembership> builder =
         ImmutableMap.builder();
     for (GroupBackend g : backends) {
       builder.put(g, g.membershipsOf(user));
     }
     this.memberships = builder.build();
   }

   @Nullable
   private GroupMembership membership(AccountGroup.UUID uuid) {
     for (Map.Entry<GroupBackend, GroupMembership> m : memberships.entrySet()) {
       if (m.getKey().handles(uuid)) {
         return m.getValue();
       }
     }
     return null;
   }

   private GroupMembership requireMembership(AccountGroup.UUID uuid) {
     GroupMembership m = membership(uuid);
     if (m == null) {
       throw new IllegalArgumentException("unhandled group UUID: " + uuid);
     }
     return m;
   }

   @Override
   public boolean contains(AccountGroup.UUID uuid) {
     return requireMembership(uuid).contains(uuid);
   }

   @Override
   public boolean containsAnyOf(Iterable<AccountGroup.UUID> uuids) {
     Multimap<GroupMembership, AccountGroup.UUID> lookups =
         ArrayListMultimap.create();
     for (AccountGroup.UUID uuid : uuids) {
       lookups.put(requireMembership(uuid), uuid);
     }
     for (Map.Entry<GroupMembership, Collection<AccountGroup.UUID>> entry :
          lookups.asMap().entrySet()) {
       if (entry.getKey().containsAnyOf(entry.getValue())) {
         return true;
       }
     }
     return false;
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
