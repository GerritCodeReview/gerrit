// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.group.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.project.ProjectState;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Implementation of GroupBackend for tests. */
public class TestGroupBackend implements GroupBackend {
  @VisibleForTesting public static final String PREFIX = "testbackend:";

  private final Map<AccountGroup.UUID, GroupDescription.Basic> groups = new HashMap<>();

  public GroupDescription.Basic add(String name) {
    checkState(name.startsWith(PREFIX), "test group must have prefix '" + PREFIX + "'");
    AccountGroup.UUID uuid = new AccountGroup.UUID(name);
    if (groups.containsKey(uuid)) {
      return groups.get(uuid);
    }
    GroupDescription.Basic group =
        new GroupDescription.Basic() {
          @Override
          public AccountGroup.UUID getGroupUUID() {
            return uuid;
          }

          @Override
          public String getName() {
            return uuid.get().substring(PREFIX.length());
          }

          @Override
          public String getEmailAddress() {
            return null;
          }

          @Override
          public String getUrl() {
            return null;
          }
        };
    groups.put(uuid, group);
    return group;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    if (uuid != null) {
      String id = uuid.get();
      return id != null && id.startsWith(PREFIX);
    }
    return false;
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    return uuid == null ? null : groups.get(uuid);
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    return ImmutableList.of();
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return GroupMembership.EMPTY;
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    return false;
  }
}
