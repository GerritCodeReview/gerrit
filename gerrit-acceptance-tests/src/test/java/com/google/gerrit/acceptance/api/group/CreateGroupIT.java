// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.api.group.GroupAssert.assertGroupInfo;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.AccountGroup;

import org.junit.Test;

@NoHttpd
public class CreateGroupIT extends AbstractDaemonTest {
  @Test
  public void testCreateGroup() throws Exception {
    String newGroupName = name("newGroup");
    GroupInfo g = gApi.groups().create(newGroupName).get();
    assertGroupInfo(getFromCache(newGroupName), g);
  }

  @Test
  public void testCreateGroupWithProperties() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("newGroup");
    in.description = "Test description";
    in.visibleToAll = true;
    in.ownerId = getFromCache("Administrators").getGroupUUID().get();
    GroupInfo g = gApi.groups().create(in).detail();
    assertThat(g.description).isEqualTo(in.description);
    assertThat(g.options.visibleToAll).isEqualTo(in.visibleToAll);
    assertThat(g.ownerId).isEqualTo(in.ownerId);
  }

  @Test
  public void testCreateGroupWithoutCapability_Forbidden() throws Exception {
    setApiUser(user);
    try {
      gApi.groups().create(name("newGroup"));
    } catch (AuthException expected) {
      // Expected.
    }
  }

  @Test
  public void testCreateGroupWhenGroupAlreadyExists_Conflict()
      throws Exception {
    try {
      gApi.groups().create("Administrators");
    } catch (ResourceConflictException expected) {
      // Expected.
    }
  }

  private AccountGroup getFromCache(String name) throws Exception {
    return groupCache.get(new AccountGroup.NameKey(name));
  }
}
