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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;

/**
 * Implementation of GroupBackend for the internal group system.
 */
@Singleton
public class InternalGroupBackend implements GroupBackend {
  private static final Function<AccountGroup, GroupReference> ACT_GROUP_TO_GROUP_REF =
      new Function<AccountGroup, GroupReference>() {
        @Override
        public GroupReference apply(AccountGroup group) {
          return GroupReference.forGroup(group);
        }
      };

  private final GroupCache groupCache;
  private final IncludingGroupMembership.Factory groupMembershipFactory;

  @Inject
  InternalGroupBackend(GroupCache groupCache,
      IncludingGroupMembership.Factory groupMembershipFactory) {
    this.groupCache = groupCache;
    this.groupMembershipFactory = groupMembershipFactory;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return uuid.get().startsWith("global:") || uuid.get().indexOf(':') < 0;
  }

  @Override
  public GroupDescription.Internal get(AccountGroup.UUID uuid) {
    checkArgument(handles(uuid),
        "internal groups system does not handle UUID: %s", uuid);
    AccountGroup g = groupCache.get(uuid);
    if (g == null) {
      return null;
    }
    return GroupDescriptions.forAccountGroup(g);
  }

  @Override
  public Collection<GroupReference> suggest(final String name) {
    Iterable<AccountGroup> filtered = Iterables.filter(groupCache.all(),
        new Predicate<AccountGroup>() {
          @Override
          public boolean apply(AccountGroup group) {
            return group.getName().startsWith(name);
          }
        });
    return Lists.newArrayList(Iterables.transform(filtered, ACT_GROUP_TO_GROUP_REF));
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return groupMembershipFactory.create(user.state().getInternalGroups());
  }
}
