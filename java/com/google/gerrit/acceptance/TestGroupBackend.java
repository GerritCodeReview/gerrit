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

package com.google.gerrit.acceptance;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Singleton;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of GroupBackend for the test group system. */
@Singleton
public class TestGroupBackend implements GroupBackend {
  private static final String PREFIX = "testgroup:";

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    if (uuid != null) {
      String id = uuid.get();
      return id != null && id.startsWith(PREFIX) && ObjectId.isId(id.substring(PREFIX.length()));
    }
    return false;
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    return null;
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
