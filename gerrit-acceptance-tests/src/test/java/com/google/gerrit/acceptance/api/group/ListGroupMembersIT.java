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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

@NoHttpd
public class ListGroupMembersIT extends AbstractDaemonTest {

  @Test
  public void listNonExistingGroupMembers_NotFound() throws Exception {
    try {
      gApi.groups().id("non-existing").members();
    } catch (ResourceNotFoundException expected) {
      // Expected.
    }
  }

  @Test
  public void listEmptyGroupMembers() throws Exception {
    String group = createGroup("empty");
    assertThat(gApi.groups().id(group).members()).isEmpty();
  }

  @Test
  public void listNonEmptyGroupMembers() throws Exception {
    String group = createGroup("group");
    String user1 = createAccount("user1", group);
    String user2 = createAccount("user2", group);
    assertMembers(gApi.groups().id(group).members(), user1, user2);
  }

  @Test
  public void listOneGroupMember() throws Exception {
    String group = createGroup("group");
    String user = createAccount("user1", group);
    assertMembers(gApi.groups().id(group).members(), user);
  }

  @Test
  public void listGroupMembersRecursively() throws Exception {
    String gx = createGroup("gx");
    String ux = createAccount("ux", gx);

    String gy = createGroup("gy");
    String uy = createAccount("uy", gy);

    String gz = createGroup("gz");
    String uz = createAccount("uz", gz);

    gApi.groups().id(gx).addGroups(gy);
    gApi.groups().id(gy).addGroups(gz);
    assertMembers(gApi.groups().id(gx).members(), ux);
    assertMembers(gApi.groups().id(gx).members(true), ux, uy, uz);
  }

  private String createGroup(String name) throws Exception {
    name = name(name);
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = "Administrators";
    gApi.groups().create(in);
    return name;

  }

  private String createAccount(String name, String group) throws Exception {
    name = name(name);
    accounts.create(name, group);
    return name;
  }

  private void assertMembers(List<AccountInfo> members, String... names) {
    Iterable<String> memberNames = Iterables.transform(members,
        new Function<AccountInfo, String>() {
          @Override
          public String apply(@Nullable AccountInfo info) {
            return info.name;
          }
        });
    assertThat(memberNames)
        .containsExactlyElementsIn(Arrays.asList(names)).inOrder();
  }
}
