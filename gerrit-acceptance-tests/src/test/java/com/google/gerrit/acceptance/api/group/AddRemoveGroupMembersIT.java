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
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfos;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@NoHttpd
public class AddRemoveGroupMembersIT extends AbstractDaemonTest {
  @Test
  public void addToNonExistingGroup_NotFound() throws Exception {
    try {
      gApi.groups().id("non-existing").addMembers("admin");
    } catch (ResourceNotFoundException expected) {
      // Expected.
    }
  }

  @Test
  public void removeFromNonExistingGroup_NotFound() throws Exception {
    try {
      gApi.groups().id("non-existing").removeMembers("admin");
    } catch (ResourceNotFoundException expected) {
      // Expected.
    }
  }

  @Test
  public void addRemoveMember() throws Exception {
    String g = group("users");
    gApi.groups().id(g).addMembers("user");
    assertMembers(g, admin, user);

    gApi.groups().id(g).removeMembers("user");
    assertMembers(g, admin);
  }

  @Test
  public void addExistingMember_OK() throws Exception {
    String g = "Administrators";
    assertMembers(g, admin);
    gApi.groups().id("Administrators").addMembers("admin");
    assertMembers(g, admin);
  }

  @Test
  public void addMultipleMembers() throws Exception {
    String g = group("users");
    TestAccount u1 = accounts.create("u1", "u1@example.com", "Full Name 1");
    TestAccount u2 = accounts.create("u2", "u2@example.com", "Full Name 2");
    gApi.groups().id(g).addMembers(u1.username, u2.username);
    assertMembers(g, admin, u1, u2);
  }

  @Test
  public void includeRemoveGroup() throws Exception {
    String p = group("parent");
    String g = group("newGroup");
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);

    gApi.groups().id(p).removeGroups(g);
    assertNoIncludes(p);
  }

  @Test
  public void includeExistingGroup_OK() throws Exception {
    String p = group("parent");
    String g = group("newGroup");
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);
  }

  @Test
  public void addMultipleIncludes() throws Exception {
    String p = group("parent");
    String g1 = group("newGroup1");
    String g2 = group("newGroup2");
    List<String> groups = Lists.newLinkedList();
    groups.add(g1);
    groups.add(g2);
    gApi.groups().id(p).addGroups(g1, g2);
    assertIncludes(p, g1, g2);
  }

  private String group(String name) throws IOException {
    name = name(name);
    GroupInput in = new GroupInput();
    adminSession.put("/groups/" + name, in).consume();
    return name;
  }

  private void assertMembers(String group, TestAccount... members)
      throws Exception {
    assertAccountInfos(
        Arrays.asList(members),
        gApi.groups().id(group).members());
  }

  private void assertIncludes(String group, String... includes)
      throws Exception {
    Iterable<String> actualNames = Iterables.transform(
        gApi.groups().id(group).includedGroups(),
        new Function<GroupInfo, String>() {
          @Override
          public String apply(GroupInfo in) {
            return in.name;
          }
        });
    assertThat(actualNames).containsExactlyElementsIn(Arrays.asList(includes))
        .inOrder();
  }

  private void assertNoIncludes(String group) throws Exception {
    assertThat(gApi.groups().id(group).includedGroups().isEmpty());
  }
}
