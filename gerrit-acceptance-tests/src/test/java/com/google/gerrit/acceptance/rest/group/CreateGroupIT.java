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

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class CreateGroupIT extends AbstractDaemonTest {
  @Test
  public void testCreateGroup() throws Exception {
    String newGroupName = name("newGroup");
    RestResponse r = adminSession.put("/groups/" + newGroupName);
    GroupInfo g = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertThat(g.name).isEqualTo(newGroupName);
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(newGroupName));
    assertThat(group).isNotNull();
    assertGroupInfo(group, g);
  }

  @Test
  public void testCreateGroupWithProperties() throws Exception {
    String newGroupName = name("newGroup");
    CreateGroup.Input in = new CreateGroup.Input();
    in.description = "Test description";
    in.visibleToAll = true;
    in.ownerId = groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID().get();
    RestResponse r = adminSession.put("/groups/" + newGroupName, in);
    GroupInfo g = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertThat(g.name).isEqualTo(newGroupName);
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(newGroupName));
    assertThat(group.getDescription()).isEqualTo(in.description);
    assertThat(group.isVisibleToAll()).isEqualTo(in.visibleToAll);
    assertThat(group.getOwnerGroupUUID().get()).isEqualTo(in.ownerId);
  }

  @Test
  public void testCreateGroupWithoutCapability_Forbidden() throws Exception {
    String newGroupName = name("newGroup");
    RestResponse r = (new RestSession(server, user)).put("/groups/" + newGroupName);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void testCreateGroupWhenGroupAlreadyExists_Conflict()
      throws Exception {
    RestResponse r = adminSession.put("/groups/Administrators");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
  }
}
