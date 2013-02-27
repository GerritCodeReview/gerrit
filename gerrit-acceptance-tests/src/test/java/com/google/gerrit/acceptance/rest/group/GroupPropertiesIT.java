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

import static com.google.gerrit.acceptance.rest.group.GroupAssert.toBoolean;
import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.reflect.TypeToken;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gson.Gson;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GroupPropertiesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private GroupCache groupCache;

  private TestAccount admin;
  private RestSession session;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(admin);
  }

  @Test
  public void testGroupName() throws IOException {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    String url = "/groups/" + groupCache.get(adminGroupName).getGroupUUID().get() + "/name";

    // get name
    RestResponse r = session.get(url);
    @SuppressWarnings("serial")
    String name = (new Gson()).fromJson(r.getReader(), new TypeToken<String>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals("Administrators", name);
    r.consume();

    // set name with name conflict
    GroupNameInput in = new GroupNameInput();
    in.name = "Registered Users";
    r = session.put(url, in);
    assertEquals(HttpStatus.SC_CONFLICT, r.getStatusCode());
    r.consume();

    // set name to same name
    in = new GroupNameInput();
    in.name = "Administrators";
    r = session.put(url, in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    // rename
    in = new GroupNameInput();
    in.name = "Admins";
    r = session.put(url, in);
    @SuppressWarnings("serial")
    String newName = (new Gson()).fromJson(r.getReader(), new TypeToken<String>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertNotNull(groupCache.get(new AccountGroup.NameKey(in.name)));
    assertNull(groupCache.get(adminGroupName));
    assertEquals(in.name, newName);
    r.consume();
  }

  @Test
  public void testGroupDescription() throws IOException {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/description";

    // get description
    RestResponse r = session.get(url);
    @SuppressWarnings("serial")
    String description = (new Gson()).fromJson(r.getReader(), new TypeToken<String>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(adminGroup.getDescription(), description);
    r.consume();

    // set description
    GroupDescriptionInput in = new GroupDescriptionInput();
    in.description = "All users that can administrate the Gerrit Server.";
    r = session.put(url, in);
    @SuppressWarnings("serial")
    String newDescription = (new Gson()).fromJson(r.getReader(), new TypeToken<String>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.description, newDescription);
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.description, adminGroup.getDescription());
    r.consume();

    // delete description
    r = session.delete(url);
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    adminGroup = groupCache.get(adminGroupName);
    assertNull(adminGroup.getDescription());
  }

  @Test
  public void testGroupOptions() throws IOException {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/options";

    // get options
    RestResponse r = session.get(url);
    @SuppressWarnings("serial")
    GroupOptionsInfo options = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupOptionsInfo>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(adminGroup.isVisibleToAll(), toBoolean(options.visible_to_all));
    r.consume();

    // set options
    GroupOptionsInput in = new GroupOptionsInput();
    in.visible_to_all = !adminGroup.isVisibleToAll();
    r = session.put(url, in);
    @SuppressWarnings("serial")
    GroupOptionsInfo newOptions = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupOptionsInfo>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.visible_to_all, toBoolean(newOptions.visible_to_all));
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.visible_to_all, adminGroup.isVisibleToAll());
    r.consume();
  }

  @Test
  public void testGroupOwner() throws IOException {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/owner";

    // get owner
    RestResponse r = session.get(url);
    @SuppressWarnings("serial")
    GroupInfo options = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupInfo>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertGroupInfo(groupCache.get(adminGroup.getOwnerGroupUUID()), options);
    r.consume();

    // set owner by name
    GroupOwnerInput in = new GroupOwnerInput();
    in.owner = "Registered Users";
    r = session.put(url, in);
    @SuppressWarnings("serial")
    GroupInfo newOwner = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupInfo>() {}.getType());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(in.owner, newOwner.name);
    adminGroup = groupCache.get(adminGroupName);
    assertGroupInfo(groupCache.get(adminGroup.getOwnerGroupUUID()), newOwner);
    r.consume();

    // set owner by UUID
    in = new GroupOwnerInput();
    in.owner = adminGroup.getGroupUUID().get();
    r = session.put(url, in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    adminGroup = groupCache.get(adminGroupName);
    assertEquals(in.owner, groupCache.get(adminGroup.getOwnerGroupUUID()).getGroupUUID().get());
    r.consume();

    // set non existing owner
    in = new GroupOwnerInput();
    in.owner = "Non-Existing Group";
    r = session.put(url, in);
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
    r.consume();
  }

  private static class GroupNameInput {
    String name;
  }

  private static class GroupDescriptionInput {
    String description;
  }

  private static class GroupOptionsInput {
    Boolean visible_to_all;
  }

  private static class GroupOwnerInput {
    String owner;
  }
}
