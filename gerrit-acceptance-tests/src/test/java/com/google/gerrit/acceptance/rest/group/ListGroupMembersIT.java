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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ListGroupMembersIT extends AbstractDaemonTest {

  @Test
  public void listNonExistingGroupMembers_NotFound() throws Exception {
    assertThat(adminSession.get(
          "/groups/" + name("non-existing") + "/members/").getStatusCode())
        .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listEmptyGroupMembers() throws Exception {
    String group = createGroup("empty");
    assertThat(GET("/groups/" + group + "/members/")).isEmpty();
  }

  @Test
  public void listNonEmptyGroupMembers() throws Exception {
    String group = createGroup("group");
    String user1 = createAccount("user1", group);
    String user2 = createAccount("user2", group);
    assertMembers(GET("/groups/" + group + "/members/"), user1, user2);
  }

  @Test
  public void listOneGroupMember() throws Exception {
    String group = createGroup("group");
    String user = createAccount("user1", group);
    assertMembers(GET("/groups/" + group + "/members/"), user);
    assertThat(GET_ONE("/groups/" + group + "/members/" + user).name)
        .isEqualTo(user);
  }

  @Test
  public void listGroupMembersRecursively() throws Exception {
    String gx = createGroup("gx");
    String ux = createAccount("ux", gx);

    String gy = createGroup("gy");
    String uy = createAccount("uy", gy);

    String gz = createGroup("gz");
    String uz = createAccount("uz", gz);

    PUT("/groups/" + gx + "/groups/" + gy);
    PUT("/groups/" + gy + "/groups/" + gz);
    assertMembers(GET("/groups/" + gx + "/members/?recursive"), ux, uy, uz);
  }

  private List<AccountInfo> GET(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return newGson().fromJson(r.getReader(),
        new TypeToken<List<AccountInfo>>() {}.getType());
  }

  private AccountInfo GET_ONE(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return newGson().fromJson(r.getReader(), AccountInfo.class);
  }

  private void PUT(String endpoint) throws IOException {
    adminSession.put(endpoint).consume();
  }

  private String createGroup(String name) throws IOException {
    name = name(name);
    CreateGroup.Input in = new CreateGroup.Input();
    in.ownerId = "Administrators";
    adminSession.put("/groups/" + name, in).consume();
    return name;

  }

  private String createAccount(String name, String group) throws Exception {
    name = name(name);
    accounts.create(name, group);
    return name;
  }

  private void assertMembers(List<AccountInfo> members, String name,
      String... names) {
    Collection<String> memberNames = Collections2.transform(members,
        new Function<AccountInfo, String>() {
          @Override
          public String apply(@Nullable AccountInfo info) {
            return info.name;
          }
        });

    assertThat((Iterable<?>)memberNames).contains(name);
    for (String n : names) {
      assertThat((Iterable<?>)memberNames).contains(n);
    }
    assertThat(members).hasSize(names.length + 1);
  }
}
