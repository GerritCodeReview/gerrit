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
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;
import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.group.AddIncludedGroups;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddRemoveGroupMembersIT extends AbstractDaemonTest {
  @Test
  public void addToNonExistingGroup_NotFound() throws Exception {
    assertThat(PUT("/groups/non-existing/members/admin").getStatusCode())
      .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void removeFromNonExistingGroup_NotFound() throws Exception {
    assertThat(DELETE("/groups/non-existing/members/admin"))
      .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void addRemoveMember() throws Exception {
    String g = group("users");
    RestResponse r = PUT("/groups/" + g + "/members/user");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    AccountInfo ai = newGson().fromJson(r.getReader(), AccountInfo.class);
    assertAccountInfo(user, ai);
    assertMembers(g, admin, user);
    r.consume();

    assertThat(DELETE("/groups/" + g + "/members/user"))
      .isEqualTo(HttpStatus.SC_NO_CONTENT);
    assertMembers(g, admin);
  }

  @Test
  public void addExistingMember_OK() throws Exception {
    assertThat(PUT("/groups/Administrators/members/admin").getStatusCode())
      .isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void addMultipleMembers() throws Exception {
    String g = group("users");
    TestAccount u1 = accounts.create("u1", "u1@example.com", "Full Name 1");
    TestAccount u2 = accounts.create("u2", "u2@example.com", "Full Name 2");
    List<String> members = Lists.newLinkedList();
    members.add(u1.username);
    members.add(u2.username);
    AddMembers.Input input = AddMembers.Input.fromMembers(members);
    RestResponse r = POST("/groups/" + g + "/members", input);
    List<AccountInfo> ai = newGson().fromJson(r.getReader(),
        new TypeToken<List<AccountInfo>>() {}.getType());
    assertMembers(ai, u1, u2);
  }

  @Test
  public void includeRemoveGroup() throws Exception {
    String p = group("parent");
    String g = group("newGroup");
    RestResponse r = PUT("/groups/" + p + "/groups/" + g);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    GroupInfo i = newGson().fromJson(r.getReader(), GroupInfo.class);
    r.consume();
    assertGroupInfo(groupCache.get(new AccountGroup.NameKey(g)), i);
    assertIncludes(p, g);

    assertThat(DELETE("/groups/" + p + "/groups/" + g))
      .isEqualTo(HttpStatus.SC_NO_CONTENT);
    assertNoIncludes(p);
  }

  @Test
  public void includeExistingGroup_OK() throws Exception {
    String p = group("parent");
    String g = group("newGroup");
    PUT("/groups/" + p + "/groups/" + g).consume();
    assertThat(PUT("/groups/" + p + "/groups/" + g).getStatusCode())
      .isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void addMultipleIncludes() throws Exception {
    String p = group("parent");
    String g1 = group("newGroup1");
    String g2 = group("newGroup2");
    List<String> groups = Lists.newLinkedList();
    groups.add(g1);
    groups.add(g2);
    AddIncludedGroups.Input input = AddIncludedGroups.Input.fromGroups(groups);
    RestResponse r = POST("/groups/" + p + "/groups", input);
    List<GroupInfo> gi = newGson().fromJson(r.getReader(),
        new TypeToken<List<GroupInfo>>() {}.getType());
    assertIncludes(gi, g1, g2);
  }

  private RestResponse PUT(String endpoint) throws IOException {
    return adminSession.put(endpoint);
  }

  private int DELETE(String endpoint) throws IOException {
    RestResponse r = adminSession.delete(endpoint);
    r.consume();
    return r.getStatusCode();
  }

  private RestResponse POST(String endPoint, AddMembers.Input mi)
      throws IOException {
    return adminSession.post(endPoint, mi);
  }

  private RestResponse POST(String endPoint, AddIncludedGroups.Input gi)
      throws IOException {
    return adminSession.post(endPoint, gi);
  }

  private String group(String name) throws IOException {
    name = name(name);
    CreateGroup.Input in = new CreateGroup.Input();
    adminSession.put("/groups/" + name, in).consume();
    return name;
  }

  private void assertMembers(String group, TestAccount... members)
      throws OrmException {
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(group));
    Set<Account.Id> ids = Sets.newHashSet();
    ResultSet<AccountGroupMember> all =
        db.accountGroupMembers().byGroup(g.getId());
    for (AccountGroupMember m : all) {
      ids.add(m.getAccountId());
    }
    assertThat((Iterable<?>)ids).hasSize(members.length);
    for (TestAccount a : members) {
      assertThat((Iterable<?>)ids).contains(a.id);
    }
  }

  private void assertMembers(List<AccountInfo> ai, TestAccount... members) {
    Map<Integer, AccountInfo> infoById = Maps.newHashMap();
    for (AccountInfo i : ai) {
      infoById.put(i._accountId, i);
    }

    for (TestAccount a : members) {
      AccountInfo i = infoById.get(a.id.get());
      assertThat(i).isNotNull();
      assertAccountInfo(a, i);
    }
    assertThat(ai).hasSize(members.length);
  }

  private void assertIncludes(String group, String... includes)
      throws OrmException {
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(group));
    Set<AccountGroup.UUID> ids = Sets.newHashSet();
    ResultSet<AccountGroupById> all =
        db.accountGroupById().byGroup(g.getId());
    for (AccountGroupById m : all) {
      ids.add(m.getIncludeUUID());
    }
    assertThat((Iterable<?>)ids).hasSize(includes.length);
    for (String i : includes) {
      AccountGroup.UUID id = groupCache.get(
          new AccountGroup.NameKey(i)).getGroupUUID();
      assertThat((Iterable<?>)ids).contains(id);
    }
  }

  private void assertIncludes(List<GroupInfo> gi, String... includes) {
    Map<String, GroupInfo> groupsByName = Maps.newHashMap();
    for (GroupInfo i : gi) {
      groupsByName.put(i.name, i);
    }

    for (String name : includes) {
      GroupInfo i = groupsByName.get(name);
      assertThat(i).isNotNull();
      assertGroupInfo(groupCache.get(new AccountGroup.NameKey(name)), i);
    }
    assertThat(gi).hasSize(includes.length);
  }

  private void assertNoIncludes(String group) throws OrmException {
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(group));
    Iterator<AccountGroupById> it =
        db.accountGroupById().byGroup(g.getId()).iterator();
    assertThat(it.hasNext()).isFalse();
  }
}
