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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.SystemGroupBackend;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@NoHttpd
public class GroupsIT extends AbstractDaemonTest {
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

  @Test
  public void testCreateGroup() throws Exception {
    String newGroupName = name("newGroup");
    GroupInfo g = gApi.groups().create(newGroupName).get();
    assertGroupInfo(getFromCache(newGroupName), g);
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
    try {
      gApi.groups().create(name("newGroup"));
    } catch (AuthException expected) {
      // Expected.
    }
  }

  @Test
  public void testCreateGroupWhenGroupAlreadyExists_Conflict()
      throws Exception {
    try {
      gApi.groups().create("Administrators");
    } catch (ResourceConflictException expected) {
      // Expected.
    }
  }
  @Test
  public void testGetGroup() throws Exception {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    testGetGroup(adminGroup.getGroupUUID().get(), adminGroup);
    testGetGroup(adminGroup.getName(), adminGroup);
    testGetGroup(adminGroup.getId().get(), adminGroup);
  }

  private void testGetGroup(Object id, AccountGroup expectedGroup)
      throws Exception {
    GroupInfo group = gApi.groups().id(id.toString()).get();
    assertGroupInfo(expectedGroup, group);
  }

  @Test
  public void testGroupName() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get name
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // set name with name conflict
    String other = name("other");
    gApi.groups().create(other);
    try {
      gApi.groups().id(name).name(other);
    } catch (ResourceConflictException expected) {
      // Expected.
    }

    // set name to same name
    gApi.groups().id(name).name(name);
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // rename
    String newName = name("newName");
    gApi.groups().id(name).name(newName);
    assertThat(getFromCache(newName)).isNotNull();
    assertThat(gApi.groups().id(newName).name()).isEqualTo(newName);

    assertThat(getFromCache(name)).isNull();
    try {
      gApi.groups().id(name).get();
    } catch (ResourceNotFoundException expected) {
      // Expceted.
    }
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
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(info.id);

    // set owner by name
    gApi.groups().id(name).owner("Registered Users");
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(registeredUUID);

    // set owner by UUID
    gApi.groups().id(name).owner(adminUUID);
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(adminUUID);

    // set non existing owner
    try {
      gApi.groups().id(name).owner("Non-Existing Group");
    } catch (UnprocessableEntityException expected) {
      // Expected.
    }
  }

  @Test
  public void listNonExistingGroupIncludes_NotFound() throws Exception {
    try {
      gApi.groups().id("non-existing").includedGroups();
    } catch (ResourceNotFoundException expected) {
      // Expected.
    }
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    String gx = group("gx");
    assertThat(gApi.groups().id(gx).includedGroups()).isEmpty();
  }

  @Test
  public void includeNonExistingGroup() throws Exception {
    String gx = group("gx");
    try {
      gApi.groups().id(gx).addGroups("non-existing");
    } catch (UnprocessableEntityException expecetd) {
      // Expected.
    }
  }

  @Test
  public void listNonEmptyGroupIncludes() throws Exception {
    String gx = group("gx");
    String gy = group("gy");
    String gz = group("gz");
    gApi.groups().id(gx).addGroups(gy);
    gApi.groups().id(gx).addGroups(gz);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy, gz);
  }

  @Test
  public void listOneIncludeMember() throws Exception {
    String gx = group("gx");
    String gy = group("gy");
    gApi.groups().id(gx).addGroups(gy);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy);
  }

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

  @Test
  public void defaultGroupsCreated() throws Exception {
    Iterable<String> names = gApi.groups().list().getAsMap().keySet();
    assertThat(names).containsAllOf("Administrators", "Non-Interactive Users")
        .inOrder();
  }

  @Test
  public void testListAllGroups() throws Exception {
    List<String> expectedGroups = FluentIterable
          .from(groupCache.all())
          .transform(new Function<AccountGroup, String>() {
            @Override
            public String apply(AccountGroup group) {
              return group.getName();
            }
          }).toSortedList(Ordering.natural());
    assertThat(expectedGroups.size()).isAtLeast(2);
    assertThat((Iterable<?>) gApi.groups().list().getAsMap().keySet())
        .containsExactlyElementsIn(expectedGroups).inOrder();
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
    assertThat(gApi.groups().list().getAsMap())
        .doesNotContainKey(newGroupName);

    setApiUser(admin);
    gApi.groups().id(newGroupName).addMembers(user.username);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).containsKey(newGroupName);
  }

  @Test
  public void testAllGroupInfoFieldsSetCorrectly() throws Exception {
    AccountGroup adminGroup = getFromCache("Administrators");
    Map<String, GroupInfo> groups =
        gApi.groups().list().addGroup(adminGroup.getName()).getAsMap();
    assertThat(groups).hasSize(1);
    assertThat(groups).containsKey("Administrators");
    assertGroupInfo(adminGroup, Iterables.getOnlyElement(groups.values()));
  }

  private void assertMembers(String group, TestAccount... members)
      throws Exception {
    assertAccountInfos(
        Arrays.asList(members),
        gApi.groups().id(group).members());
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

  private void assertIncludes(List<GroupInfo> includes, String... names) {
    Iterable<String> includeNames = Iterables.transform(includes,
        new Function<GroupInfo, String>() {
          @Override
          public String apply(@Nullable GroupInfo info) {
            return info.name;
          }
        });
    assertThat(includeNames)
        .containsExactlyElementsIn(Arrays.asList(names)).inOrder();
  }

  private void assertNoIncludes(String group) throws Exception {
    assertThat(gApi.groups().id(group).includedGroups().isEmpty());
  }

  private AccountGroup getFromCache(String name) throws Exception {
    return groupCache.get(new AccountGroup.NameKey(name));
  }

  private String group(String name) throws Exception {
    name = name(name);
    gApi.groups().create(name);
    return name;
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
}
