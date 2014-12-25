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
import static com.google.gerrit.acceptance.rest.group.GroupAssert.toBoolean;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.group.GroupOptionsInfo;
import com.google.gerrit.server.group.PutDescription;
import com.google.gerrit.server.group.PutName;
import com.google.gerrit.server.group.PutOptions;
import com.google.gerrit.server.group.PutOwner;
import com.google.gerrit.server.group.SystemGroupBackend;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class GroupPropertiesIT extends AbstractDaemonTest {
  @Test
  public void testGroupName() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    String url = "/groups/" + groupCache.get(adminGroupName).getGroupUUID().get() + "/name";

    // get name
    RestResponse r = adminSession.get(url);
    String name = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(name).isEqualTo("Administrators");
    r.consume();

    // set name with name conflict
    String newGroupName = "newGroup";
    r = adminSession.put("/groups/" + newGroupName);
    r.consume();
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    PutName.Input in = new PutName.Input();
    in.name = newGroupName;
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();

    // set name to same name
    in = new PutName.Input();
    in.name = "Administrators";
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();

    // rename
    in = new PutName.Input();
    in.name = "Admins";
    r = adminSession.put(url, in);
    String newName = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(groupCache.get(new AccountGroup.NameKey(in.name))).isNotNull();
    assertThat(groupCache.get(adminGroupName)).isNull();
    assertThat(newName).isEqualTo(in.name);
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
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(description).isEqualTo(adminGroup.getDescription());
    r.consume();

    // set description
    PutDescription.Input in = new PutDescription.Input();
    in.description = "All users that can administrate the Gerrit Server.";
    r = adminSession.put(url, in);
    String newDescription = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(newDescription).isEqualTo(in.description);
    adminGroup = groupCache.get(adminGroupName);
    assertThat(adminGroup.getDescription()).isEqualTo(in.description);
    r.consume();

    // delete description
    r = adminSession.delete(url);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    adminGroup = groupCache.get(adminGroupName);
    assertThat(adminGroup.getDescription()).isNull();

    // set description to empty string
    in = new PutDescription.Input();
    in.description = "";
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    adminGroup = groupCache.get(adminGroupName);
    assertThat(adminGroup.getDescription()).isNull();
  }

  @Test
  public void testGroupOptions() throws Exception {
    AccountGroup.NameKey adminGroupName = new AccountGroup.NameKey("Administrators");
    AccountGroup adminGroup = groupCache.get(adminGroupName);
    String url = "/groups/" + adminGroup.getGroupUUID().get() + "/options";

    // get options
    RestResponse r = adminSession.get(url);
    GroupOptionsInfo options = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(toBoolean(options.visibleToAll)).isEqualTo(adminGroup.isVisibleToAll());
    r.consume();

    // set options
    PutOptions.Input in = new PutOptions.Input();
    in.visibleToAll = !adminGroup.isVisibleToAll();
    r = adminSession.put(url, in);
    GroupOptionsInfo newOptions = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(toBoolean(newOptions.visibleToAll)).isEqualTo(in.visibleToAll);
    adminGroup = groupCache.get(adminGroupName);
    assertThat(adminGroup.isVisibleToAll()).isEqualTo(in.visibleToAll);
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
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertGroupInfo(groupCache.get(adminGroup.getOwnerGroupUUID()), options);
    r.consume();

    // set owner by name
    PutOwner.Input in = new PutOwner.Input();
    in.owner = "Registered Users";
    r = adminSession.put(url, in);
    GroupInfo newOwner = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(newOwner.name).isEqualTo(in.owner);
    assertThat(newOwner.name).isEqualTo(
        SystemGroupBackend.getGroup(SystemGroupBackend.REGISTERED_USERS).getName());
    assertThat(SystemGroupBackend.REGISTERED_USERS.get())
      .isEqualTo(Url.decode(newOwner.id));
    r.consume();

    // set owner by UUID
    in = new PutOwner.Input();
    in.owner = adminGroup.getGroupUUID().get();
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    adminGroup = groupCache.get(adminGroupName);
    assertThat(groupCache.get(adminGroup.getOwnerGroupUUID()).getGroupUUID().get())
      .isEqualTo(in.owner);
    r.consume();

    // set non existing owner
    in = new PutOwner.Input();
    in.owner = "Non-Existing Group";
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    r.consume();
  }
}
