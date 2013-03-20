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

import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;
import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.rest.account.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddRemoveGroupMembersIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GroupCache groupCache;

  private RestSession session;
  private TestAccount admin;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "Administrators");
    session = new RestSession(admin);
    db = reviewDbProvider.open();
  }

  @After
  public void tearDown() {
    db.close();
  }

  @Test
  public void addToNonExistingGroup_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        PUT("/groups/non-existing/members/admin").getStatusCode());
  }

  @Test
  public void removeFromNonExistingGroup_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        DELETE("/groups/non-existing/members/admin"));
  }

  @Test
  public void addRemoveMember() throws Exception {
    TestAccount u = accounts.create("user", "user@example.com", "Full Name");
    RestResponse r = PUT("/groups/Administrators/members/user");
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    AccountInfo ai = (new Gson()).fromJson(r.getReader(),
        new TypeToken<AccountInfo>() {}.getType());
    assertAccountInfo(u, ai);
    assertMembers("Administrators", admin, u);
    r.consume();

    assertEquals(HttpStatus.SC_NO_CONTENT,
        DELETE("/groups/Administrators/members/user"));
    assertMembers("Administrators", admin);
  }

  @Test
  public void addExistingMember_OK() throws IOException {
    assertEquals(HttpStatus.SC_OK,
        PUT("/groups/Administrators/members/admin").getStatusCode());
  }

  @Test
  public void addMultipleMembers() throws Exception {
    group("users");
    TestAccount u1 = accounts.create("u1", "u1@example.com", "Full Name 1");
    TestAccount u2 = accounts.create("u2", "u2@example.com", "Full Name 2");
    MembersInput input = new MembersInput();
    input.members = Lists.newLinkedList();
    input.members.add(u1.username);
    input.members.add(u2.username);
    RestResponse r = POST("/groups/users/members", input);
    List<AccountInfo> ai = (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<AccountInfo>>() {}.getType());
    assertMembers(ai, u1, u2);
  }

  @Test
  public void includeRemoveGroup() throws Exception {
    group("newGroup");
    RestResponse r = PUT("/groups/Administrators/groups/newGroup");
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    GroupInfo i = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupInfo>() {}.getType());
    r.consume();
    assertGroupInfo(groupCache.get(new AccountGroup.NameKey("newGroup")), i);
    assertIncludes("Administrators", "newGroup");

    assertEquals(HttpStatus.SC_NO_CONTENT,
        DELETE("/groups/Administrators/groups/newGroup"));
    assertNoIncludes("Administrators");
  }

  @Test
  public void includeExistingGroup_OK() throws Exception {
    group("newGroup");
    PUT("/groups/Administrators/groups/newGroup").consume();
    assertEquals(HttpStatus.SC_OK,
        PUT("/groups/Administrators/groups/newGroup").getStatusCode());
  }

  @Test
  public void addMultipleIncludes() throws Exception {
    group("newGroup1");
    group("newGroup2");
    GroupsInput input = new GroupsInput();
    input.groups = Lists.newLinkedList();
    input.groups.add("newGroup1");
    input.groups.add("newGroup2");
    RestResponse r = POST("/groups/Administrators/groups", input);
    List<GroupInfo> gi = (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<GroupInfo>>() {}.getType());
    assertIncludes(gi, "newGroup1", "newGroup2");
  }

  private RestResponse PUT(String endpoint) throws IOException {
    return session.put(endpoint);
  }

  private int DELETE(String endpoint) throws IOException {
    RestResponse r = session.delete(endpoint);
    r.consume();
    return r.getStatusCode();
  }

  private RestResponse POST(String endPoint, MembersInput mi) throws IOException {
    return session.post(endPoint, mi);
  }

  private RestResponse POST(String endPoint, GroupsInput gi) throws IOException {
    return session.post(endPoint, gi);
  }

  private void group(String name) throws IOException {
    GroupInput in = new GroupInput();
    session.put("/groups/" + name, in).consume();
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
    assertTrue(ids.size() == members.length);
    for (TestAccount a : members) {
      assertTrue(ids.contains(a.id));
    }
  }

  private void assertMembers(List<AccountInfo> ai, TestAccount... members) {
    Map<Integer, AccountInfo> infoById = Maps.newHashMap();
    for (AccountInfo i : ai) {
      infoById.put(i._account_id, i);
    }

    for (TestAccount a : members) {
      AccountInfo i = infoById.get(a.id.get());
      assertNotNull(i);
      assertAccountInfo(a, i);
    }
    assertEquals(ai.size(), members.length);
  }

  private void assertIncludes(String group, String... includes)
      throws OrmException {
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(group));
    Set<AccountGroup.UUID> ids = Sets.newHashSet();
    ResultSet<AccountGroupIncludeByUuid> all =
        db.accountGroupIncludesByUuid().byGroup(g.getId());
    for (AccountGroupIncludeByUuid m : all) {
      ids.add(m.getIncludeUUID());
    }
    assertTrue(ids.size() == includes.length);
    for (String i : includes) {
      AccountGroup.UUID id = groupCache.get(
          new AccountGroup.NameKey(i)).getGroupUUID();
      assertTrue(ids.contains(id));
    }
  }

  private void assertIncludes(List<GroupInfo> gi, String... includes) {
    Map<String, GroupInfo> groupsByName = Maps.newHashMap();
    for (GroupInfo i : gi) {
      groupsByName.put(i.name, i);
    }

    for (String name : includes) {
      GroupInfo i = groupsByName.get(name);
      assertNotNull(i);
      assertGroupInfo(groupCache.get(new AccountGroup.NameKey(name)), i);
    }
    assertEquals(gi.size(), includes.length);
  }

  private void assertNoIncludes(String group) throws OrmException {
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(group));
    Iterator<AccountGroupIncludeByUuid> it =
        db.accountGroupIncludesByUuid().byGroup(g.getId()).iterator();
    assertFalse(it.hasNext());
  }
}
