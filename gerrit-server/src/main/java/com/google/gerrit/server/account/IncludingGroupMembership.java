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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Group membership checker for the internal group system.
 *
 * <p>Groups the user is directly a member of are pulled from the in-memory AccountCache by way of
 * the IdentifiedUser. Transitive group memberhips are resolved on demand starting from the
 * requested group and looking for a path to a group the user is a member of. Other group backends
 * are supported by recursively invoking the universal GroupMembership.
 */
public class IncludingGroupMembership implements GroupMembership {
  public interface Factory {
    IncludingGroupMembership create(IdentifiedUser user);
  }

  private static final Logger log = LoggerFactory.getLogger(IncludingGroupMembership.class);

  private final Provider<ReviewDb> db;
  private final GroupCache groupCache;
  private final GroupIncludeCache includeCache;
  private final Provider<GroupIndex> groupIndexProvider;
  private final Provider<InternalGroupQuery> groupQueryProvider;
  private final IdentifiedUser user;
  private final Map<AccountGroup.UUID, Boolean> memberOf;
  private Set<AccountGroup.UUID> knownGroups;

  @Inject
  IncludingGroupMembership(
      Provider<ReviewDb> db,
      GroupCache groupCache,
      GroupIncludeCache includeCache,
      GroupIndexCollection groupIndexCollection,
      Provider<InternalGroupQuery> groupQueryProvider,
      @Assisted IdentifiedUser user) {
    this.db = db;
    this.groupCache = groupCache;
    this.includeCache = includeCache;
    groupIndexProvider = groupIndexCollection::getSearchIndex;
    this.groupQueryProvider = groupQueryProvider;
    this.user = user;
    memberOf = new ConcurrentHashMap<>();
  }

  @Override
  public boolean contains(AccountGroup.UUID id) {
    if (id == null) {
      return false;
    }

    Boolean b = memberOf.get(id);
    return b != null ? b : containsAnyOf(ImmutableSet.of(id));
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
        Optional<InternalGroup> group = groupCache.get(id);
        if (!group.isPresent()) {
          continue;
        }
        if (group.get().getMembers().contains(user.getAccountId())) {
          memberOf.put(id, true);
          return true;
        }
        if (search(group.get().getSubgroups())) {
          memberOf.put(id, true);
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds) {
    Set<AccountGroup.UUID> r = new HashSet<>();
    for (AccountGroup.UUID id : groupIds) {
      if (contains(id)) {
        r.add(id);
      }
    }
    return r;
  }

  private boolean search(Iterable<AccountGroup.UUID> ids) {
    return user.getEffectiveGroups().containsAnyOf(ids);
  }

  private ImmutableSet<AccountGroup.UUID> computeKnownGroups() {
    GroupMembership membership = user.getEffectiveGroups();
    Set<AccountGroup.UUID> direct;
    try {
      direct = getGroupsWithMember(db.get(), user.getAccountId());
      direct.forEach(groupUuid -> memberOf.put(groupUuid, true));
    } catch (OrmException e) {
      log.warn(
          String.format("Cannot load groups containing %d as member", user.getAccountId().get()));
      direct = ImmutableSet.of();
    }
    Set<AccountGroup.UUID> r = Sets.newHashSet(direct);
    r.remove(null);

    List<AccountGroup.UUID> q = Lists.newArrayList(r);
    for (AccountGroup.UUID g : membership.intersection(includeCache.allExternalMembers())) {
      if (g != null && r.add(g)) {
        q.add(g);
      }
    }

    while (!q.isEmpty()) {
      AccountGroup.UUID id = q.remove(q.size() - 1);
      for (AccountGroup.UUID g : includeCache.parentGroupsOf(id)) {
        if (g != null && r.add(g)) {
          q.add(g);
          memberOf.put(g, true);
        }
      }
    }
    return ImmutableSet.copyOf(r);
  }

  private ImmutableSet<AccountGroup.UUID> getGroupsWithMember(ReviewDb db, Account.Id memberId)
      throws OrmException {
    Stream<InternalGroup> internalGroupStream;
    GroupIndex groupIndex = groupIndexProvider.get();
    if (groupIndex != null && groupIndex.getSchema().hasField(GroupField.MEMBER)) {
      internalGroupStream = groupQueryProvider.get().byMember(memberId).stream();
    } else {
      internalGroupStream =
          Groups.getGroupsWithMemberFromReviewDb(db, memberId)
              .map(groupCache::get)
              .flatMap(Streams::stream);
    }

    return internalGroupStream.map(InternalGroup::getGroupUUID).collect(toImmutableSet());
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    if (knownGroups == null) {
      knownGroups = computeKnownGroups();
    }
    return knownGroups;
  }
}
