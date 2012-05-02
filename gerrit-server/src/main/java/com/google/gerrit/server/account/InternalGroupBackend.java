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

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ExtGroup;
import com.google.gerrit.common.data.ExtGroups;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

/**
 * Implementation of GroupBackend for the internal group system.
 */
@Singleton
public class InternalGroupBackend implements GroupBackend {
  private static final String MAX_SUFFIX = "\u9fa5";

  private final GroupCache groupCache;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final IncludingGroupMembership.Factory groupMembershipFactory;

  @Inject
  InternalGroupBackend(GroupCache groupCache,
      SchemaFactory<ReviewDb> schemaFactory,
      IncludingGroupMembership.Factory groupMembershipFactory) {
    this.groupCache = groupCache;
    this.schemaFactory = schemaFactory;
    this.groupMembershipFactory = groupMembershipFactory;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return uuid.get().startsWith("global:") || uuid.get().indexOf(':') < 0;
  }

  @Override
  public ExtGroup get(AccountGroup.UUID uuid) {
    checkArgument(handles(uuid),
        "internal groups system does not handle UUID: %s", uuid);
    AccountGroup g = groupCache.get(uuid);
    if (g == null) {
      return null;
    }
    return ExtGroups.forAccountGroup(g);
  }

  @Override
  public Set<GroupReference> suggest(String name) {
    try {
      ReviewDb db = schemaFactory.open();
      try {
        final String a = name;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        Set<GroupReference> r = Sets.newHashSetWithExpectedSize(max);
        for (AccountGroupName group : db.accountGroupNames().suggestByName(a, b, max)) {
          AccountGroup g = groupCache.get(group.getId());
          if ((g != null) && (g.getGroupUUID() != null)) {
            r.add(GroupReference.forGroup(g));
          }
        }
        return r;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new OrmRuntimeException(e);
    }
  }

  @Override
  public GroupMembership membershipsOf(AccountState user) {
    return groupMembershipFactory.create(user.getInternalGroups());
  }
}
