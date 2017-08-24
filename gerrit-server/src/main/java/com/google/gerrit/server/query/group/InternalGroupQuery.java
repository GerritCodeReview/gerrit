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
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query wrapper for the group index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalGroupQuery extends InternalQuery<InternalGroup> {
  private static final Logger log = LoggerFactory.getLogger(InternalGroupQuery.class);

  @Inject
  InternalGroupQuery(
      GroupQueryProcessor queryProcessor, GroupIndexCollection indexes, IndexConfig indexConfig) {
    super(queryProcessor, indexes, indexConfig);
  }

  public Optional<InternalGroup> byName(AccountGroup.NameKey groupName) throws OrmException {
    return getOnlyGroup(GroupPredicates.name(groupName.get()), "group name '" + groupName + "'");
  }

  public Optional<InternalGroup> byId(AccountGroup.Id groupId) throws OrmException {
    return getOnlyGroup(GroupPredicates.id(groupId), "group id '" + groupId + "'");
  }

  private Optional<InternalGroup> getOnlyGroup(
      Predicate<InternalGroup> predicate, String groupDescription) throws OrmException {
    List<InternalGroup> groups = query(predicate);
    if (groups.isEmpty()) {
      return Optional.empty();
    }

    if (groups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(groups));
    }

    ImmutableList<AccountGroup.UUID> groupUuids =
        groups.stream().map(InternalGroup::getGroupUUID).collect(toImmutableList());
    log.warn(String.format("Ambiguous %s for groups %s.", groupDescription, groupUuids));
    return Optional.empty();
  }
}
