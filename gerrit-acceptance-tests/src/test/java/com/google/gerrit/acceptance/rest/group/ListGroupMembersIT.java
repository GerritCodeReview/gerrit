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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.AccountInfo;
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
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/groups/non-existing/members/").getStatusCode());
  }

  @Test
  public void listEmptyGroupMembers() throws Exception {
    group("empty", "Administrators");
    assertTrue(GET("/groups/empty/members/").isEmpty());
  }

  @Test
  public void listNonEmptyGroupMembers() throws Exception {
    assertMembers(GET("/groups/Administrators/members/"), admin.fullName);

    accounts.create("admin2", "Administrators");
    assertMembers(GET("/groups/Administrators/members/"),
        admin.fullName, "admin2");
  }

  @Test
  public void listOneGroupMember() throws Exception {
    assertEquals(GET_ONE("/groups/Administrators/members/admin").name,
        admin.fullName);
  }

  @Test
  public void listGroupMembersRecursively() throws Exception {
    group("gx", "Administrators");
    accounts.create("ux", "gx");

    group("gy", "Administrators");
    accounts.create("uy", "gy");

    PUT("/groups/Administrators/groups/gx");
    PUT("/groups/gx/groups/gy");
    assertMembers(GET("/groups/Administrators/members/?recursive"),
        admin.fullName, "ux", "uy");
  }

  private List<AccountInfo> GET(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(),
        new TypeToken<List<AccountInfo>>() {}.getType());
  }

  private AccountInfo GET_ONE(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), AccountInfo.class);
  }

  private void PUT(String endpoint) throws IOException {
    adminSession.put(endpoint).consume();
  }

  private void group(String name, String ownerGroup)
      throws IOException {
    CreateGroup.Input in = new CreateGroup.Input();
    in.ownerId = ownerGroup;
    adminSession.put("/groups/" + name, in).consume();
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

    assertTrue(memberNames.contains(name));
    for (String n : names) {
      assertTrue(memberNames.contains(n));
    }
    assertEquals(members.size(), names.length + 1);
  }
}
