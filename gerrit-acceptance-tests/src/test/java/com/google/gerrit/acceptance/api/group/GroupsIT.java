// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.acceptance.api.group.GroupAssert.assertGroupInfo;
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfos;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.GroupMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.Type;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.UserMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.SystemGroupBackend;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@NoHttpd
public class GroupsIT extends AbstractDaemonTest {
  @Test
  public void addToNonExistingGroup_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id("non-existing").addMembers("admin");
  }

  @Test
  public void removeFromNonExistingGroup_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id("non-existing").removeMembers("admin");
  }

  @Test
  public void addRemoveMember() throws Exception {
    String g = createGroup("users");
    gApi.groups().id(g).addMembers("user");
    assertMembers(g, user);

    gApi.groups().id(g).removeMembers("user");
    assertNoMembers(g);
  }

  @Test
  public void addExistingMember_OK() throws Exception {
    String g = "Administrators";
    assertMembers(g, admin);
    gApi.groups().id("Administrators").addMembers("admin");
    assertMembers(g, admin);
  }

  @Test
  public void addNonExistingMember_UnprocessableEntity() throws Exception {
    exception.expect(UnprocessableEntityException.class);
    gApi.groups().id("Administrators").addMembers("non-existing");
  }

  @Test
  public void addMultipleMembers() throws Exception {
    String g = createGroup("users");
    TestAccount u1 = accounts.create("u1", "u1@example.com", "Full Name 1");
    TestAccount u2 = accounts.create("u2", "u2@example.com", "Full Name 2");
    gApi.groups().id(g).addMembers(u1.username, u2.username);
    assertMembers(g, u1, u2);
  }

  @Test
  public void includeRemoveGroup() throws Exception {
    String p = createGroup("parent");
    String g = createGroup("newGroup");
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);

    gApi.groups().id(p).removeGroups(g);
    assertNoIncludes(p);
  }

  @Test
  public void includeExistingGroup_OK() throws Exception {
    String p = createGroup("parent");
    String g = createGroup("newGroup");
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);
    gApi.groups().id(p).addGroups(g);
    assertIncludes(p, g);
  }

  @Test
  public void addMultipleIncludes() throws Exception {
    String p = createGroup("parent");
    String g1 = createGroup("newGroup1");
    String g2 = createGroup("newGroup2");
    List<String> groups = new LinkedList<>();
    groups.add(g1);
    groups.add(g2);
    gApi.groups().id(p).addGroups(g1, g2);
    assertIncludes(p, g1, g2);
  }

  @Test
  public void testCreateGroup() throws Exception {
    String newGroupName = name("newGroup");
    GroupInfo g = gApi.groups().create(newGroupName).get();
    assertGroupInfo(getFromCache(newGroupName), g);
  }

  @Test
  public void testCreateDuplicateInternalGroupCaseSensitiveName_Conflict() throws Exception {
    String dupGroupName = name("dupGroup");
    gApi.groups().create(dupGroupName);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group '" + dupGroupName + "' already exists");
    gApi.groups().create(dupGroupName);
  }

  @Test
  public void testCreateDuplicateInternalGroupCaseInsensitiveName() throws Exception {
    String dupGroupName = name("dupGroupA");
    String dupGroupNameLowerCase = name("dupGroupA").toLowerCase();
    gApi.groups().create(dupGroupName);
    gApi.groups().create(dupGroupNameLowerCase);
    assertThat(gApi.groups().list().getAsMap().keySet()).contains(dupGroupName);
    assertThat(gApi.groups().list().getAsMap().keySet()).contains(dupGroupNameLowerCase);
  }

  @Test
  public void testCreateDuplicateSystemGroupCaseSensitiveName_Conflict() throws Exception {
    String newGroupName = "Registered Users";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group 'Registered Users' already exists");
    gApi.groups().create(newGroupName);
  }

  @Test
  public void testCreateDuplicateSystemGroupCaseInsensitiveName_Conflict() throws Exception {
    String newGroupName = "registered users";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group 'Registered Users' already exists");
    gApi.groups().create(newGroupName);
  }

  @Test
  public void testCreateGroupWithProperties() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("newGroup");
    in.description = "Test description";
    in.visibleToAll = true;
    in.ownerId = getFromCache("Administrators").getGroupUUID().get();
    GroupInfo g = gApi.groups().create(in).detail();
    assertThat(g.description).isEqualTo(in.description);
    assertThat(g.options.visibleToAll).isEqualTo(in.visibleToAll);
    assertThat(g.ownerId).isEqualTo(in.ownerId);
  }

  @Test
  public void testCreateGroupWithoutCapability_Forbidden() throws Exception {
    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.groups().create(name("newGroup"));
  }

  @Test
  public void testGetGroup() throws Exception {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    testGetGroup(adminGroup.getGroupUUID().get(), adminGroup);
    testGetGroup(adminGroup.getName(), adminGroup);
    testGetGroup(adminGroup.getId().get(), adminGroup);
  }

  private void testGetGroup(Object id, AccountGroup expectedGroup) throws Exception {
    GroupInfo group = gApi.groups().id(id.toString()).get();
    assertGroupInfo(expectedGroup, group);
  }

  @Test
  public void testGroupName() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get name
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // set name to same name
    gApi.groups().id(name).name(name);
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // set name with name conflict
    String other = name("other");
    gApi.groups().create(other);
    exception.expect(ResourceConflictException.class);
    gApi.groups().id(name).name(other);
  }

  @Test
  public void testGroupRename() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    String newName = name("newName");
    gApi.groups().id(name).name(newName);
    assertThat(getFromCache(newName)).isNotNull();
    assertThat(gApi.groups().id(newName).name()).isEqualTo(newName);

    assertThat(getFromCache(name)).isNull();
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id(name).get();
  }

  @Test
  public void testGroupDescription() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get description
    assertThat(gApi.groups().id(name).description()).isEmpty();

    // set description
    String desc = "New description for the group.";
    gApi.groups().id(name).description(desc);
    assertThat(gApi.groups().id(name).description()).isEqualTo(desc);

    // set description to null
    gApi.groups().id(name).description(null);
    assertThat(gApi.groups().id(name).description()).isEmpty();

    // set description to empty string
    gApi.groups().id(name).description("");
    assertThat(gApi.groups().id(name).description()).isEmpty();
  }

  @Test
  public void testGroupOptions() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get options
    assertThat(gApi.groups().id(name).options().visibleToAll).isNull();

    // set options
    GroupOptionsInfo options = new GroupOptionsInfo();
    options.visibleToAll = true;
    gApi.groups().id(name).options(options);
    assertThat(gApi.groups().id(name).options().visibleToAll).isTrue();
  }

  @Test
  public void testGroupOwner() throws Exception {
    String name = name("group");
    GroupInfo info = gApi.groups().create(name).get();
    String adminUUID = getFromCache("Administrators").getGroupUUID().get();
    String registeredUUID = SystemGroupBackend.REGISTERED_USERS.get();

    // get owner
    assertThat(Url.decode(gApi.groups().id(name).owner().id)).isEqualTo(info.id);

    // set owner by name
    gApi.groups().id(name).owner("Registered Users");
    assertThat(Url.decode(gApi.groups().id(name).owner().id)).isEqualTo(registeredUUID);

    // set owner by UUID
    gApi.groups().id(name).owner(adminUUID);
    assertThat(Url.decode(gApi.groups().id(name).owner().id)).isEqualTo(adminUUID);

    // set non existing owner
    exception.expect(UnprocessableEntityException.class);
    gApi.groups().id(name).owner("Non-Existing Group");
  }

  @Test
  public void listNonExistingGroupIncludes_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id("non-existing").includedGroups();
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    String gx = createGroup("gx");
    assertThat(gApi.groups().id(gx).includedGroups()).isEmpty();
  }

  @Test
  public void includeNonExistingGroup() throws Exception {
    String gx = createGroup("gx");
    exception.expect(UnprocessableEntityException.class);
    gApi.groups().id(gx).addGroups("non-existing");
  }

  @Test
  public void listNonEmptyGroupIncludes() throws Exception {
    String gx = createGroup("gx");
    String gy = createGroup("gy");
    String gz = createGroup("gz");
    gApi.groups().id(gx).addGroups(gy);
    gApi.groups().id(gx).addGroups(gz);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy, gz);
  }

  @Test
  public void listOneIncludeMember() throws Exception {
    String gx = createGroup("gx");
    String gy = createGroup("gy");
    gApi.groups().id(gx).addGroups(gy);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy);
  }

  @Test
  public void listNonExistingGroupMembers_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id("non-existing").members();
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

  @Test
  public void defaultGroupsCreated() throws Exception {
    Iterable<String> names = gApi.groups().list().getAsMap().keySet();
    assertThat(names).containsAllOf("Administrators", "Non-Interactive Users").inOrder();
  }

  @Test
  public void testListAllGroups() throws Exception {
    List<String> expectedGroups =
        groupCache.all().stream().map(a -> a.getName()).sorted().collect(toList());
    assertThat(expectedGroups.size()).isAtLeast(2);
    assertThat(gApi.groups().list().getAsMap().keySet())
        .containsExactlyElementsIn(expectedGroups)
        .inOrder();
  }

  @Test
  public void testOnlyVisibleGroupsReturned() throws Exception {
    String newGroupName = name("newGroup");
    GroupInput in = new GroupInput();
    in.name = newGroupName;
    in.description = "a hidden group";
    in.visibleToAll = false;
    in.ownerId = getFromCache("Administrators").getGroupUUID().get();
    gApi.groups().create(in);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).doesNotContainKey(newGroupName);

    setApiUser(admin);
    gApi.groups().id(newGroupName).addMembers(user.username);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).containsKey(newGroupName);
  }

  @Test
  public void testSuggestGroup() throws Exception {
    Map<String, GroupInfo> groups = gApi.groups().list().withSuggest("adm").getAsMap();
    assertThat(groups).containsKey("Administrators");
    assertThat(groups).hasSize(1);
  }

  @Test
  public void testAllGroupInfoFieldsSetCorrectly() throws Exception {
    AccountGroup adminGroup = getFromCache("Administrators");
    Map<String, GroupInfo> groups = gApi.groups().list().addGroup(adminGroup.getName()).getAsMap();
    assertThat(groups).hasSize(1);
    assertThat(groups).containsKey("Administrators");
    assertGroupInfo(adminGroup, Iterables.getOnlyElement(groups.values()));
  }

  @Test
  public void getAuditLog() throws Exception {
    GroupApi g = gApi.groups().create(name("group"));
    List<? extends GroupAuditEventInfo> auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(1);
    assertAuditEvent(auditEvents.get(0), Type.ADD_USER, admin.id, admin.id);

    g.addMembers(user.username);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(2);
    assertAuditEvent(auditEvents.get(0), Type.ADD_USER, admin.id, user.id);

    g.removeMembers(user.username);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(3);
    assertAuditEvent(auditEvents.get(0), Type.REMOVE_USER, admin.id, user.id);

    String otherGroup = name("otherGroup");
    gApi.groups().create(otherGroup);
    g.addGroups(otherGroup);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(4);
    assertAuditEvent(auditEvents.get(0), Type.ADD_GROUP, admin.id, otherGroup);

    g.removeGroups(otherGroup);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(5);
    assertAuditEvent(auditEvents.get(0), Type.REMOVE_GROUP, admin.id, otherGroup);

    Timestamp lastDate = null;
    for (GroupAuditEventInfo auditEvent : auditEvents) {
      if (lastDate != null) {
        assertThat(lastDate).isGreaterThan(auditEvent.date);
      }
      lastDate = auditEvent.date;
    }
  }

  private void assertAuditEvent(
      GroupAuditEventInfo info,
      Type expectedType,
      Account.Id expectedUser,
      Account.Id expectedMember) {
    assertThat(info.user._accountId).isEqualTo(expectedUser.get());
    assertThat(info.type).isEqualTo(expectedType);
    assertThat(info).isInstanceOf(UserMemberAuditEventInfo.class);
    assertThat(((UserMemberAuditEventInfo) info).member._accountId).isEqualTo(expectedMember.get());
  }

  private void assertAuditEvent(
      GroupAuditEventInfo info,
      Type expectedType,
      Account.Id expectedUser,
      String expectedMemberGroupName) {
    assertThat(info.user._accountId).isEqualTo(expectedUser.get());
    assertThat(info.type).isEqualTo(expectedType);
    assertThat(info).isInstanceOf(GroupMemberAuditEventInfo.class);
    assertThat(((GroupMemberAuditEventInfo) info).member.name).isEqualTo(expectedMemberGroupName);
  }

  private void assertMembers(String group, TestAccount... expectedMembers) throws Exception {
    assertMembers(
        gApi.groups().id(group).members(),
        TestAccount.names(expectedMembers).stream().toArray(String[]::new));
    assertAccountInfos(Arrays.asList(expectedMembers), gApi.groups().id(group).members());
  }

  private void assertMembers(Iterable<AccountInfo> members, String... expectedNames) {
    assertThat(Iterables.transform(members, i -> i.name))
        .containsExactlyElementsIn(Arrays.asList(expectedNames))
        .inOrder();
  }

  private void assertNoMembers(String group) throws Exception {
    assertThat(gApi.groups().id(group).members()).isEmpty();
  }

  private void assertIncludes(String group, String... expectedNames) throws Exception {
    assertIncludes(gApi.groups().id(group).includedGroups(), expectedNames);
  }

  private static void assertIncludes(Iterable<GroupInfo> includes, String... expectedNames) {
    assertThat(Iterables.transform(includes, i -> i.name))
        .containsExactlyElementsIn(Arrays.asList(expectedNames))
        .inOrder();
  }

  private void assertNoIncludes(String group) throws Exception {
    assertThat(gApi.groups().id(group).includedGroups()).isEmpty();
  }

  private AccountGroup getFromCache(String name) throws Exception {
    return groupCache.get(new AccountGroup.NameKey(name));
  }

  private String createAccount(String name, String group) throws Exception {
    name = name(name);
    accounts.create(name, group);
    return name;
  }
}
