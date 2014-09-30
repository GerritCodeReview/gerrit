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

import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class CreateGroupIT extends AbstractDaemonTest {

  @Inject
  private GroupCache groupCache;

  @Test
  public void testCreateGroup() throws IOException {
    final String newGroupName = "newGroup";
    RestResponse r = adminSession.put("/groups/" + newGroupName);
    GroupInfo g = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertEquals(newGroupName, g.name);
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(newGroupName));
    assertNotNull(group);
    assertGroupInfo(group, g);
  }

  @Test
  public void testCreateGroupWithProperties() throws IOException {
    final String newGroupName = "newGroup";
    CreateGroup.Input in = new CreateGroup.Input();
    in.description = "Test description";
    in.visibleToAll = true;
    in.ownerId = groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID().get();
    RestResponse r = adminSession.put("/groups/" + newGroupName, in);
    GroupInfo g = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertEquals(newGroupName, g.name);
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(newGroupName));
    assertEquals(in.description, group.getDescription());
    assertEquals(in.visibleToAll, group.isVisibleToAll());
    assertEquals(in.ownerId, group.getOwnerGroupUUID().get());
  }

  @Test
  public void testCreateGroupWithoutCapability_Forbidden() throws OrmException,
      JSchException, IOException {
    RestResponse r = (new RestSession(server, user)).put("/groups/newGroup");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void testCreateGroupWhenGroupAlreadyExists_Conflict()
      throws OrmException, JSchException, IOException {
    RestResponse r = adminSession.put("/groups/Administrators");
    assertEquals(HttpStatus.SC_CONFLICT, r.getStatusCode());
  }
}
