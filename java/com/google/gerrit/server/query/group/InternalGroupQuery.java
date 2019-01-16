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
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;

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

  public Optional<InternalGroup> byName(AccountGroup.NameKey groupName) throws StorageException {
    return getOnlyGroup(GroupPredicates.name(groupName.get()), "group name '" + groupName + "'");
  }

  public Optional<InternalGroup> byId(AccountGroup.Id groupId) throws StorageException {
    return getOnlyGroup(GroupPredicates.id(groupId), "group id '" + groupId + "'");
  }

  public List<InternalGroup> byMember(Account.Id memberId) throws StorageException {
    return query(GroupPredicates.member(memberId));
  }

  public List<InternalGroup> bySubgroup(AccountGroup.UUID subgroupId) throws StorageException {
    return query(GroupPredicates.subgroup(subgroupId));
  }

  private Optional<InternalGroup> getOnlyGroup(
      Predicate<InternalGroup> predicate, String groupDescription) throws StorageException {
    List<InternalGroup> groups = query(predicate);
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
