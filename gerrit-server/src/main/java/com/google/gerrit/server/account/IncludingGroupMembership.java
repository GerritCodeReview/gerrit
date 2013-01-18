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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.Collections;
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
  private final InternalGroupBackend internalBackend;
  private final IdentifiedUser user;
  private final List<AccountGroup.UUID> pending;
  private final Map<AccountGroup.UUID, Boolean> memberOf;

  @Inject
  IncludingGroupMembership(
      GroupIncludeCache includeCache,
      InternalGroupBackend internalBackend,
      @Assisted IdentifiedUser user) {
    this.includeCache = includeCache;
    this.internalBackend = internalBackend;
    this.user = user;

    Set<AccountGroup.UUID> groups = user.state().getInternalGroups();
    pending = Lists.newArrayList(groups);
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
    return search(Sets.newHashSet(id));
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> ids) {
    Set<AccountGroup.UUID> query = null;
    for (AccountGroup.UUID groupId : ids) {
      Boolean b = memberOf.get(groupId);
      if (b != null) {
        if (b) {
          return true;
        } else {
          continue;
        }
      } else if (query == null) {
        query = Sets.newHashSet();
      }
      query.add(groupId);
    }
    return query != null && search(query);
  }

  private boolean search(Set<AccountGroup.UUID> query) {
    // Given groups the user is known to be a direct member, visit groups
    // they are a member of. This identifies indirect memberships that are
    // stored in the internal group system.
    boolean found = false;
    while (!found && !pending.isEmpty()) {
      AccountGroup.UUID id = pending.remove(pending.size() - 1);
      for (AccountGroup.UUID groupId : includeCache.memberIn(id)) {
        if (memberOf.put(groupId, true) == null) {
          pending.add(groupId);
          found |= query.contains(groupId);
        }
      }
    }
    if (found) {
      return true;
    } else if (query.isEmpty()) {
      return false;
    }

    // Recursively expand the query groups, checking membership in any
    // external groups contained within a query group. The user may be
    // a member of an internal group through an external group.
    GroupMembership memberships = user.getEffectiveGroups();
    for (Map.Entry<AccountGroup.UUID, Collection<AccountGroup.UUID>> e
        : internalBackend.expand(query).asMap().entrySet()) {
      boolean b = memberships.contains(e.getKey());
      for (AccountGroup.UUID i : e.getValue()) {
        if (b) {
          memberOf.put(i, true);
        } else if (!memberOf.containsKey(i)) {
          memberOf.put(i, false);
        }
      }
      if (b) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    search(Collections.<AccountGroup.UUID> emptySet()); // find all

    Set<AccountGroup.UUID> r = Sets.newHashSetWithExpectedSize(memberOf.size());
    for (Map.Entry<AccountGroup.UUID, Boolean> e : memberOf.entrySet()) {
      if (e.getValue()) {
        r.add(e.getKey());
      }
    }
    return r;
  }
}
