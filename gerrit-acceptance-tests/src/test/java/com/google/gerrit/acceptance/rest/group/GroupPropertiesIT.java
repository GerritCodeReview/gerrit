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
import static com.google.gerrit.acceptance.rest.group.GroupAssert.toBoolean;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.group.GroupOptionsInfo;
import com.google.gerrit.server.group.PutDescription;
import com.google.gerrit.server.group.PutName;
import com.google.gerrit.server.group.PutOptions;
import com.google.gerrit.server.group.PutOwner;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class GroupPropertiesIT extends AbstractDaemonTest {

  @Inject
  private GroupCache groupCache;

  @Test
  public void testGroupName() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    String url = "/groups/" + groupCache.get(adminGroupName).getGroupUUID().get() + "/name";

    // get name
    RestResponse r = adminSession.get(url);
    String name = newGson().fromJson(r.getReader(), String.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals("Administrators", name);
    r.consume();

    // set name with name conflict
    String newGroupName = "newGroup";
    r = adminSession.put("/groups/" + newGroupName);
    r.consume();
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    PutName.Input in = new PutName.Input();
    in.name = newGroupName;
    r = adminSession.put(url, in);
    assertEquals(HttpStatus.SC_CONFLICT, r.getStatusCode());
    r.consume();

    // set name to same name
    in = new PutName.Input();
    in.name = "Administrators";
    r = adminSession.put(url, in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    // rename
    in = new PutName.Input();
    in.name = "Admins";
    r = adminSession.put(url, in);
    String newName = newGson().fromJson(r.getReader(), String.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertNotNull(groupCache.get(new AccountGroup.NameKey(in.name)));
    assertNull(groupCache.get(adminGroupName));
    assertEquals(in.name, newName);
    r.consume();
  }

  @Test
  public void testGroupDescription() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/description";

    // get description
    RestResponse r = adminSession.get(url);
    String description = newGson().fromJson(r.getReader(), String.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(adminGroup.getDescription(), description);
    r.consume();

    // set description
    PutDescription.Input in = new PutDescription.Input();
    in.description = "All users that can administrate the Gerrit Server.";
    r = adminSession.put(url, in);
    String newDescription = newGson().fromJson(r.getReader(), String.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.description, newDescription);
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.description, adminGroup.getDescription());
    r.consume();

    // delete description
    r = adminSession.delete(url);
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    adminGroup = groupCache.get(adminGroupName);
    assertNull(adminGroup.getDescription());

    // set description to empty string
    in = new PutDescription.Input();
    in.description = "";
    r = adminSession.put(url, in);
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    adminGroup = groupCache.get(adminGroupName);
    assertNull(adminGroup.getDescription());
  }

  @Test
  public void testGroupOptions() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/options";

    // get options
    RestResponse r = adminSession.get(url);
    GroupOptionsInfo options = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(adminGroup.isVisibleToAll(), toBoolean(options.visibleToAll));
    r.consume();

    // set options
    PutOptions.Input in = new PutOptions.Input();
    in.visibleToAll = !adminGroup.isVisibleToAll();
    r = adminSession.put(url, in);
    GroupOptionsInfo newOptions = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.visibleToAll, toBoolean(newOptions.visibleToAll));
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.visibleToAll, adminGroup.isVisibleToAll());
    r.consume();
  }

  @Test
  public void testGroupOwner() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/owner";

    // get owner
    RestResponse r = adminSession.get(url);
    GroupInfo options = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertGroupInfo(groupCache.get(adminGroup.getOwnerGroupUUID()), options);
    r.consume();

    // set owner by name
    PutOwner.Input in = new PutOwner.Input();
    in.owner = "Registered Users";
    r = adminSession.put(url, in);
    GroupInfo newOwner = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.owner, newOwner.name);
    assertEquals(
        SystemGroupBackend.getGroup(SystemGroupBackend.REGISTERED_USERS).getName(),
        newOwner.name);
    assertEquals(
        SystemGroupBackend.REGISTERED_USERS.get(),
        Url.decode(newOwner.id));
    r.consume();

    // set owner by UUID
    in = new PutOwner.Input();
    in.owner = adminGroup.getGroupUUID().get();
    r = adminSession.put(url, in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.owner, groupCache.get(adminGroup.getOwnerGroupUUID()).getGroupUUID().get());
    r.consume();

    // set non existing owner
    in = new PutOwner.Input();
    in.owner = "Non-Existing Group";
    r = adminSession.put(url, in);
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, r.getStatusCode());
    r.consume();
  }
}
