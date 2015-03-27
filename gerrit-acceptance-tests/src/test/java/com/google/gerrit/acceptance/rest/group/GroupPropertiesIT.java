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
import static com.google.gerrit.acceptance.rest.group.GroupAssert.toBoolean;

import com.google.common.base.Strings;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.group.GroupOptionsInfo;
import com.google.gerrit.server.group.PutDescription;
import com.google.gerrit.server.group.PutName;
import com.google.gerrit.server.group.PutOptions;
import com.google.gerrit.server.group.PutOwner;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GroupPropertiesIT extends AbstractDaemonTest {
  private GroupInfo group;
  private String baseUrl;

  @Before
  public void setUp() throws Exception {
    group = createGroup("group");
    baseUrl = "/groups/" + group.id;
  }

  @Test
  public void testGroupName() throws Exception {
    String url = baseUrl + "/name";

    // get name
    RestResponse r = adminSession.get(url);
    String name = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(name).isEqualTo(group.name);
    r.consume();

    // set name with name conflict
    GroupInfo newGroup = createGroup("newGroup");
    PutName.Input in = new PutName.Input();
    in.name = newGroup.name;
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
    r.consume();

    // set name to same name
    in = new PutName.Input();
    in.name = group.name;
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();

    // rename
    in = new PutName.Input();
    in.name = name("newName");
    r = adminSession.put(url, in);
    String newName = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(groupCache.get(new AccountGroup.NameKey(in.name))).isNotNull();
    assertThat(groupCache.get(new AccountGroup.NameKey(group.name))).isNull();
    assertThat(newName).isEqualTo(in.name);
    r.consume();
  }

  @Test
  public void testGroupDescription() throws Exception {
    String url = baseUrl + "/description";

    // get description
    RestResponse r = adminSession.get(url);
    String description = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(Strings.emptyToNull(description)).isEqualTo(group.description);
    r.consume();

    // set description
    PutDescription.Input in = new PutDescription.Input();
    in.description = "New description for the group.";
    r = adminSession.put(url, in);
    String newDescription = newGson().fromJson(r.getReader(), String.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(newDescription).isEqualTo(in.description);
    group = getGroup(group.name);
    assertThat(group.description).isEqualTo(in.description);
    r.consume();

    // delete description
    r = adminSession.delete(url);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    group = getGroup(group.name);
    assertThat(group.description).isNull();

    // set description to empty string
    in = new PutDescription.Input();
    in.description = "";
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    group = getGroup(group.name);
    assertThat(group.description).isNull();
  }

  @Test
  public void testGroupOptions() throws Exception {
    String url = baseUrl + "/options";
    // get options
    RestResponse r = adminSession.get(url);
    GroupOptionsInfo options = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(options.visibleToAll).isEqualTo(group.options.visibleToAll);
    r.consume();

    // set options
    PutOptions.Input in = new PutOptions.Input();
    in.visibleToAll = !toBoolean(group.options.visibleToAll);
    r = adminSession.put(url, in);
    GroupOptionsInfo newOptions = newGson().fromJson(r.getReader(), GroupOptionsInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(newOptions.visibleToAll).isEqualTo(in.visibleToAll);
    group = getGroup(group.name);
    assertThat(group.options.visibleToAll).isEqualTo(in.visibleToAll);
    r.consume();
  }

  @Test
  public void testGroupOwner() throws Exception {
    String url = baseUrl + "/owner";
    String adminGroupUUID = groupCache.get(
        new AccountGroup.NameKey("Administrators")).getGroupUUID().get();

    // get owner
    RestResponse r = adminSession.get(url);
    GroupInfo options = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(options.ownerId).isEqualTo(adminGroupUUID);
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
    in.owner = adminGroupUUID;
    r = adminSession.put(url, in);
    newOwner = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    assertThat(newOwner.id).isEqualTo(in.owner);
    r.consume();

    // set non existing owner
    in = new PutOwner.Input();
    in.owner = "Non-Existing Group";
    r = adminSession.put(url, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    r.consume();
  }

  private GroupInfo createGroup(String name) throws IOException {
    name = name(name);
    CreateGroup.Input in = new CreateGroup.Input();
    in.ownerId = "Administrators";
    RestResponse r = adminSession.put("/groups/" + name, in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    return parseGroup(r);
  }

  private GroupInfo getGroup(String name) throws IOException {
    RestResponse r = adminSession.get("/groups/" + name);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return parseGroup(r);
  }

  private GroupInfo parseGroup(RestResponse r) throws IOException {
    return newGson().fromJson(r.getReader(),
        new TypeToken<GroupInfo>() {}.getType());
  }
}
