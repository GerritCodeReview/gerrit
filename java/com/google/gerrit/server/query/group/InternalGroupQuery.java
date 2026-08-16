// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.group;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Query wrapper for the group index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalGroupQuery extends InternalQuery<InternalGroup, InternalGroupQuery> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  InternalGroupQuery(
      GroupQueryProcessor queryProcessor, GroupIndexCollection indexes, IndexConfig indexConfig) {
    super(queryProcessor, indexes, indexConfig);
  }

  public Optional<InternalGroup> byName(AccountGroup.NameKey groupName) {
    return getOnlyGroup(GroupPredicates.name(groupName.get()), "group name '" + groupName + "'");
  }

  public Optional<InternalGroup> byId(AccountGroup.Id groupId) {
    return getOnlyGroup(GroupPredicates.id(groupId), "group id '" + groupId + "'");
  }

  public Optional<InternalGroup> byUUID(AccountGroup.UUID uuid) {
    return getOnlyGroup(GroupPredicates.uuid(uuid), "group UUID '" + uuid + "'");
  }

  public ImmutableList<InternalGroup> byUUIDs(Collection<AccountGroup.UUID> uuids) {
    if (uuids.isEmpty()) {
      return ImmutableList.of();
    }
    if (uuids.size() == 1) {
      return query(GroupPredicates.uuid(uuids.iterator().next()));
    }
    int batchSize = Math.max(1, indexConfig.maxTerms() - 1);
    if (uuids.size() <= batchSize) {
      List<Predicate<InternalGroup>> predicates = new ArrayList<>(uuids.size());
      for (AccountGroup.UUID uuid : uuids) {
        predicates.add(GroupPredicates.uuid(uuid));
      }
      return query(Predicate.or(predicates));
    }
    List<Predicate<InternalGroup>> batchPredicates = new ArrayList<>();
    for (List<AccountGroup.UUID> partition : Iterables.partition(uuids, batchSize)) {
      if (partition.size() == 1) {
        batchPredicates.add(GroupPredicates.uuid(partition.get(0)));
      } else {
        List<Predicate<InternalGroup>> predicates = new ArrayList<>(partition.size());
        for (AccountGroup.UUID uuid : partition) {
          predicates.add(GroupPredicates.uuid(uuid));
        }
        batchPredicates.add(Predicate.or(predicates));
      }
    }
    ImmutableList.Builder<InternalGroup> result = ImmutableList.builder();
    Set<AccountGroup.UUID> seen = new HashSet<>();
    for (List<InternalGroup> batchResult : query(batchPredicates)) {
      for (InternalGroup group : batchResult) {
        if (seen.add(group.getGroupUUID())) {
          result.add(group);
        }
      }
    }
    return result.build();
  }

  public ImmutableList<InternalGroup> byMember(Account.Id memberId) {
    return query(GroupPredicates.member(memberId));
  }

  public ImmutableList<InternalGroup> byMembers(Collection<Account.Id> memberIds) {
    if (memberIds.isEmpty()) {
      return ImmutableList.of();
    }
    if (memberIds.size() == 1) {
      return byMember(memberIds.iterator().next());
    }
    int batchSize = Math.max(1, indexConfig.maxTerms() - 1);
    if (memberIds.size() <= batchSize) {
      List<Predicate<InternalGroup>> predicates = new ArrayList<>(memberIds.size());
      for (Account.Id id : memberIds) {
        predicates.add(GroupPredicates.member(id));
      }
      return query(Predicate.or(predicates));
    }
    List<Predicate<InternalGroup>> batchPredicates = new ArrayList<>();
    for (List<Account.Id> partition : Iterables.partition(memberIds, batchSize)) {
      if (partition.size() == 1) {
        batchPredicates.add(GroupPredicates.member(partition.get(0)));
      } else {
        List<Predicate<InternalGroup>> predicates = new ArrayList<>(partition.size());
        for (Account.Id id : partition) {
          predicates.add(GroupPredicates.member(id));
        }
        batchPredicates.add(Predicate.or(predicates));
      }
    }
    ImmutableList.Builder<InternalGroup> result = ImmutableList.builder();
    Set<AccountGroup.UUID> seen = new HashSet<>();
    for (List<InternalGroup> batchResult : query(batchPredicates)) {
      for (InternalGroup group : batchResult) {
        if (seen.add(group.getGroupUUID())) {
          result.add(group);
        }
      }
    }
    return result.build();
  }

  /**
   * Get all immediate parents of the provided {@code subgroupIds}.
   *
   * @return map pointing from children to list of its immediate parents
   */
  public ImmutableMap<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> bySubgroups(
      ImmutableSet<AccountGroup.UUID> subgroupIds) {
    if (subgroupIds.isEmpty()) {
      return ImmutableMap.of();
    }

    ImmutableList<InternalGroup> groups;
    int batchSize = Math.max(1, indexConfig.maxTerms() - 1);
    if (subgroupIds.size() == 1) {
      groups = query(GroupPredicates.subgroup(subgroupIds.iterator().next()));
    } else if (subgroupIds.size() <= batchSize) {
      List<Predicate<InternalGroup>> predicates = new ArrayList<>(subgroupIds.size());
      for (AccountGroup.UUID e : subgroupIds) {
        predicates.add(GroupPredicates.subgroup(e));
      }
      groups = query(Predicate.or(predicates));
    } else {
      List<Predicate<InternalGroup>> batchPredicates = new ArrayList<>();
      for (List<AccountGroup.UUID> partition : Iterables.partition(subgroupIds, batchSize)) {
        if (partition.size() == 1) {
          batchPredicates.add(GroupPredicates.subgroup(partition.get(0)));
        } else {
          List<Predicate<InternalGroup>> predicates = new ArrayList<>(partition.size());
          for (AccountGroup.UUID e : partition) {
            predicates.add(GroupPredicates.subgroup(e));
          }
          batchPredicates.add(Predicate.or(predicates));
        }
      }
      ImmutableList.Builder<InternalGroup> result = ImmutableList.builder();
      Set<AccountGroup.UUID> seen = new HashSet<>();
      for (List<InternalGroup> batchResult : query(batchPredicates)) {
        for (InternalGroup g : batchResult) {
          if (seen.add(g.getGroupUUID())) {
            result.add(g);
          }
        }
      }
      groups = result.build();
    }

    Map<AccountGroup.UUID, Set<AccountGroup.UUID>> parentsByChild =
        Maps.newHashMapWithExpectedSize(subgroupIds.size());
    for (AccountGroup.UUID c : subgroupIds) {
      parentsByChild.put(c, new HashSet<>());
    }
    for (InternalGroup parent : groups) {
      for (AccountGroup.UUID child : parent.getSubgroups()) {
        Set<AccountGroup.UUID> parents = parentsByChild.get(child);
        if (parents != null) {
          parents.add(parent.getGroupUUID());
        }
      }
    }
    ImmutableMap.Builder<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> result =
        ImmutableMap.builderWithExpectedSize(subgroupIds.size());
    for (Map.Entry<AccountGroup.UUID, Set<AccountGroup.UUID>> entry : parentsByChild.entrySet()) {
      result.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    return result.build();
  }

  private Optional<InternalGroup> getOnlyGroup(
      Predicate<InternalGroup> predicate, String groupDescription) {
    ImmutableList<InternalGroup> groups = setLimit(2).query(predicate);
    if (groups.isEmpty()) {
      return Optional.empty();
    }

    if (groups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(groups));
    }

    ImmutableList<AccountGroup.UUID> groupUuids =
        groups.stream().map(InternalGroup::getGroupUUID).collect(toImmutableList());
    logger.atWarning().log("Ambiguous %s for groups %s.", groupDescription, groupUuids);
    return Optional.empty();
  }
}
