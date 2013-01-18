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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a GroupMembership checker for the internal group system, which
 * starts with the seed groups and includes all child groups.
 */
public class IncludingGroupMembership implements GroupMembership {
  public interface Factory {
    IncludingGroupMembership create(IdentifiedUser user);
  }

  private final GroupIncludeCache includeCache;
  private final IdentifiedUser user;
  private final Map<AccountGroup.UUID, Boolean> memberOf;
  private Set<AccountGroup.UUID> knownGroups;

  @Inject
  IncludingGroupMembership(GroupIncludeCache includeCache,
      @Assisted IdentifiedUser user) {
    this.includeCache = includeCache;
    this.user = user;

    Set<AccountGroup.UUID> groups = user.state().getInternalGroups();
    memberOf = Maps.newHashMapWithExpectedSize(groups.size());
    for (AccountGroup.UUID g : groups) {
      memberOf.put(g, true);
    }
  }

  @Override
  public boolean contains(AccountGroup.UUID id) {
    if (id == null) {
      return false;
    }

    Boolean b = memberOf.get(id);
    if (b != null) {
      return b;
    }

    memberOf.put(id, false);
    if (search(includeCache.membersOf(id))) {
      memberOf.put(id, true);
      return true;
    }
    return false;
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> queryIds) {
    // Prefer lookup of a cached result over expanding includes.
    boolean tryExpanding = false;
    for (AccountGroup.UUID id : queryIds) {
      Boolean b = memberOf.get(id);
      if (b == null) {
        tryExpanding = true;
      } else if (b) {
        return true;
      }
    }

    if (tryExpanding) {
      for (AccountGroup.UUID id : queryIds) {
        if (memberOf.containsKey(id)) {
          // Membership was earlier proven to be false.
          continue;
        }

        memberOf.put(id, false);
        if (search(includeCache.membersOf(id))) {
          memberOf.put(id, true);
          return true;
        }
      }
    }

    return false;
  }

  private boolean search(Set<AccountGroup.UUID> ids) {
    if (ids.isEmpty()) {
      return false;
    }

    GroupMembership membership = user.getEffectiveGroups();
    if (ids.size() == 1) {
      return membership.contains(Iterables.getOnlyElement(ids));
    }
    return membership.containsAnyOf(ids);
  }

  private ImmutableSet<AccountGroup.UUID> computeKnownGroups() {
    Set<AccountGroup.UUID> direct = user.state().getInternalGroups();
    Set<AccountGroup.UUID> r = Sets.newHashSet(direct);
    List<AccountGroup.UUID> q = Lists.newArrayList(r);
    while (!q.isEmpty()) {
      AccountGroup.UUID id = q.remove(q.size() - 1);
      for (AccountGroup.UUID g : includeCache.memberIn(id)) {
        if (r.add(g)) {
          q.add(g);
          memberOf.put(g, true);
        }
      }
    }
    return ImmutableSet.copyOf(r);
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    if (knownGroups == null) {
      knownGroups = computeKnownGroups();
    }
    return knownGroups;
  }
}
