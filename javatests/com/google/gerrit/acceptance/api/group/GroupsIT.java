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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.api.group.GroupAssert.assertGroupInfo;
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfos;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.READ;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigration.WRITE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.ProjectResetter;
import com.google.gerrit.acceptance.ProjectResetter.Builder;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.groups.Groups.ListRequest;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.GroupMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.Type;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.UserMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsConsistencyChecker;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.index.group.StalenessChecker;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GroupsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbConfig() {
    Config config = new Config();
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, true);
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, true);
    return config;
  }

  @ConfigSuite.Config
  public static Config disableReviewDb() {
    Config config = noteDbConfig();
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, true);
    return config;
  }

  @Inject private Groups groups;
  @Inject @ServerInitiated private GroupsUpdate groupsUpdate;
  @Inject private GroupIncludeCache groupIncludeCache;
  @Inject private StalenessChecker stalenessChecker;
  @Inject private GroupIndexer groupIndexer;
  @Inject private GroupsConsistencyChecker consistencyChecker;
  @Inject private PeriodicGroupIndexer slaveGroupIndexer;
  @Inject private DynamicSet<GroupIndexedListener> groupIndexedListeners;
  @Inject private Sequences seq;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @After
  public void consistencyCheck() throws Exception {
    if (description.getAnnotation(IgnoreGroupInconsistencies.class) == null) {
      assertThat(consistencyChecker.check()).isEmpty();
    }
  }

  @Override
  protected ProjectResetter resetProjects(Builder resetter) throws IOException {
    // Don't reset All-Users since deleting users makes groups inconsistent (e.g. groups would
    // contain members that no longer exist) and as result of this the group consistency checker
    // that is executed after each test would fail.
    return resetter.reset(allProjects, RefNames.REFS_CONFIG).build();
  }

  @Test
  public void systemGroupCanBeRetrievedFromIndex() throws Exception {
    List<GroupInfo> groupInfos = gApi.groups().query("name:Administrators").get();
    assertThat(groupInfos).isNotEmpty();
  }

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
  public void cachedGroupsForMemberAreUpdatedOnMemberAdditionAndRemoval() throws Exception {
    TestAccount account = createUniqueAccount("user", "User");

    // Fill the cache for the observed account.
    groupIncludeCache.getGroupsWithMember(account.getId());
    String groupName = createGroup("users");
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(gApi.groups().id(groupName).get().id);

    gApi.groups().id(groupName).addMembers(account.username);

    Collection<AccountGroup.UUID> groupsWithMemberAfterAddition =
        groupIncludeCache.getGroupsWithMember(account.getId());
    assertThat(groupsWithMemberAfterAddition).contains(groupUuid);

    gApi.groups().id(groupName).removeMembers(account.username);

    Collection<AccountGroup.UUID> groupsWithMemberAfterRemoval =
        groupIncludeCache.getGroupsWithMember(account.getId());
    assertThat(groupsWithMemberAfterRemoval).doesNotContain(groupUuid);
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
    TestAccount u1 = createUniqueAccount("u1", "Full Name 1");
    TestAccount u2 = createUniqueAccount("u2", "Full Name 2");
    gApi.groups().id(g).addMembers(u1.username, u2.username);
    assertMembers(g, u1, u2);
  }

  @Test
  public void addMembersWithAtSign() throws Exception {
    String g = createGroup("users");
    TestAccount u1 = createUniqueAccount("u1", "Full Name 1");
    TestAccount u2_at = createUniqueAccount("u2@something", "Full Name 2 With At");
    TestAccount u2 = createUniqueAccount("u2", "Full Name 2 Without At");
    gApi.groups().id(g).addMembers(u1.username, u2_at.username, u2.username);
    assertMembers(g, u1, u2_at, u2);
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
  public void includeExternalGroup() throws Exception {
    String g = createGroup("group");
    String subgroupUuid = SystemGroupBackend.REGISTERED_USERS.get();
    gApi.groups().id(g).addGroups(subgroupUuid);

    List<GroupInfo> subgroups = gApi.groups().id(g).includedGroups();
    assertThat(subgroups).hasSize(1);
    assertThat(subgroups.get(0).id).isEqualTo(subgroupUuid.replace(":", "%3A"));
    assertThat(subgroups.get(0).name).isEqualTo("Registered Users");
    assertThat(subgroups.get(0).groupId).isNull();

    List<? extends GroupAuditEventInfo> auditEvents = gApi.groups().id(g).auditLog();
    assertThat(auditEvents).hasSize(1);
    assertAuditEvent(auditEvents.get(0), Type.ADD_GROUP, admin.id, "Registered Users");
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
    List<String> groups = new ArrayList<>();
    groups.add(g1);
    groups.add(g2);
    gApi.groups().id(p).addGroups(g1, g2);
    assertIncludes(p, g1, g2);
  }

  @Test
  public void createGroup() throws Exception {
    String newGroupName = name("newGroup");
    GroupInfo g = gApi.groups().create(newGroupName).get();
    assertGroupInfo(group(newGroupName), g);
  }

  @Test
  public void createDuplicateInternalGroupCaseSensitiveName_Conflict() throws Exception {
    String dupGroupName = name("dupGroup");
    gApi.groups().create(dupGroupName);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group '" + dupGroupName + "' already exists");
    gApi.groups().create(dupGroupName);
  }

  @Test
  public void createDuplicateInternalGroupCaseInsensitiveName() throws Exception {
    String dupGroupName = name("dupGroupA");
    String dupGroupNameLowerCase = name("dupGroupA").toLowerCase();
    gApi.groups().create(dupGroupName);
    gApi.groups().create(dupGroupNameLowerCase);
    assertThat(gApi.groups().list().getAsMap().keySet()).contains(dupGroupName);
    assertThat(gApi.groups().list().getAsMap().keySet()).contains(dupGroupNameLowerCase);
  }

  @Test
  public void createDuplicateSystemGroupCaseSensitiveName_Conflict() throws Exception {
    String newGroupName = "Registered Users";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group 'Registered Users' already exists");
    gApi.groups().create(newGroupName);
  }

  @Test
  public void createDuplicateSystemGroupCaseInsensitiveName_Conflict() throws Exception {
    String newGroupName = "registered users";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group 'Registered Users' already exists");
    gApi.groups().create(newGroupName);
  }

  @Test
  @GerritConfig(name = "groups.global:Anonymous-Users.name", value = "All Users")
  public void createGroupWithConfiguredNameOfSystemGroup_Conflict() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group 'All Users' already exists");
    gApi.groups().create("all users");
  }

  @Test
  @GerritConfig(name = "groups.global:Anonymous-Users.name", value = "All Users")
  public void createGroupWithDefaultNameOfSystemGroup_Conflict() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group name 'Anonymous Users' is reserved");
    gApi.groups().create("anonymous users");
  }

  @Test
  public void createGroupWithProperties() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("newGroup");
    in.description = "Test description";
    in.visibleToAll = true;
    in.ownerId = adminGroupUuid().get();
    GroupInfo g = gApi.groups().create(in).detail();
    assertThat(g.description).isEqualTo(in.description);
    assertThat(g.options.visibleToAll).isEqualTo(in.visibleToAll);
    assertThat(g.ownerId).isEqualTo(in.ownerId);
  }

  @Test
  public void createGroupWithoutCapability_Forbidden() throws Exception {
    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.groups().create(name("newGroup"));
  }

  @Test
  public void createdOnFieldIsPopulatedForNewGroup() throws Exception {
    // NoteDb allows only second precision.
    Timestamp testStartTime = TimeUtil.truncateToSecond(TimeUtil.nowTs());
    String newGroupName = name("newGroup");
    GroupInfo group = gApi.groups().create(newGroupName).get();

    assertThat(group.createdOn).isAtLeast(testStartTime);
  }

  @Test
  public void cachedGroupsForMemberAreUpdatedOnGroupCreation() throws Exception {
    TestAccount account = createUniqueAccount("user", "User");

    // Fill the cache for the observed account.
    groupIncludeCache.getGroupsWithMember(account.id);

    GroupInput groupInput = new GroupInput();
    groupInput.name = name("Users");
    groupInput.members = ImmutableList.of(account.username);
    GroupInfo group = gApi.groups().create(groupInput).get();

    Collection<AccountGroup.UUID> groups = groupIncludeCache.getGroupsWithMember(account.id);
    assertThat(groups).containsExactly(new AccountGroup.UUID(group.id));
  }

  @Test
  public void getGroup() throws Exception {
    InternalGroup adminGroup = adminGroup();
    testGetGroup(adminGroup.getGroupUUID().get(), adminGroup);
    testGetGroup(adminGroup.getName(), adminGroup);
    testGetGroup(adminGroup.getId().get(), adminGroup);
  }

  private void testGetGroup(Object id, InternalGroup expectedGroup) throws Exception {
    GroupInfo group = gApi.groups().id(id.toString()).get();
    assertGroupInfo(expectedGroup, group);
  }

  @Test
  @GerritConfig(name = "groups.global:Anonymous-Users.name", value = "All Users")
  public void getSystemGroupByConfiguredName() throws Exception {
    GroupReference anonymousUsersGroup = systemGroupBackend.getGroup(ANONYMOUS_USERS);
    assertThat(anonymousUsersGroup.getName()).isEqualTo("All Users");

    GroupInfo group = gApi.groups().id(anonymousUsersGroup.getUUID().get()).get();
    assertThat(group.name).isEqualTo(anonymousUsersGroup.getName());

    group = gApi.groups().id(anonymousUsersGroup.getName()).get();
    assertThat(group.id).isEqualTo(Url.encode((anonymousUsersGroup.getUUID().get())));
  }

  @Test
  public void getSystemGroupByDefaultName() throws Exception {
    GroupReference anonymousUsersGroup = systemGroupBackend.getGroup(ANONYMOUS_USERS);
    GroupInfo group = gApi.groups().id("Anonymous Users").get();
    assertThat(group.name).isEqualTo(anonymousUsersGroup.getName());
    assertThat(group.id).isEqualTo(Url.encode((anonymousUsersGroup.getUUID().get())));
  }

  @Test
  @GerritConfig(name = "groups.global:Anonymous-Users.name", value = "All Users")
  public void getSystemGroupByDefaultName_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id("Anonymous-Users").get();
  }

  @Test
  public void groupIsCreatedForSpecifiedName() throws Exception {
    String name = name("Users");
    gApi.groups().create(name);

    assertThat(gApi.groups().id(name).name()).isEqualTo(name);
  }

  @Test
  public void groupCannotBeCreatedWithNameOfAnotherGroup() throws Exception {
    String name = name("Users");
    gApi.groups().create(name).get();

    exception.expect(ResourceConflictException.class);
    gApi.groups().create(name);
  }

  @Test
  public void groupCanBeRenamed() throws Exception {
    String name = name("Name1");
    GroupInfo group = gApi.groups().create(name).get();

    String newName = name("Name2");
    gApi.groups().id(name).name(newName);
    assertThat(gApi.groups().id(group.id).name()).isEqualTo(newName);
  }

  @Test
  public void groupCanBeRenamedToItsCurrentName() throws Exception {
    String name = name("Users");
    GroupInfo group = gApi.groups().create(name).get();

    gApi.groups().id(group.id).name(name);
    assertThat(gApi.groups().id(group.id).name()).isEqualTo(name);
  }

  @Test
  public void groupCannotBeRenamedToNameOfAnotherGroup() throws Exception {
    String name1 = name("Name1");
    GroupInfo group1 = gApi.groups().create(name1).get();

    String name2 = name("Name2");
    gApi.groups().create(name2);

    exception.expect(ResourceConflictException.class);
    gApi.groups().id(group1.id).name(name2);
  }

  @Test
  public void renamedGroupCanBeLookedUpByNewName() throws Exception {
    String name = name("Name1");
    GroupInfo group = gApi.groups().create(name).get();

    String newName = name("Name2");
    gApi.groups().id(group.id).name(newName);

    GroupInfo foundGroup = gApi.groups().id(newName).get();
    assertThat(foundGroup.id).isEqualTo(group.id);
  }

  @Test
  public void oldNameOfRenamedGroupIsNotAccessibleAnymore() throws Exception {
    String name = name("Name1");
    GroupInfo group = gApi.groups().create(name).get();

    String newName = name("Name2");
    gApi.groups().id(group.id).name(newName);

    assertGroupDoesNotExist(name);
    exception.expect(ResourceNotFoundException.class);
    gApi.groups().id(name).get();
  }

  @Test
  public void oldNameOfRenamedGroupIsFreeForUseAgain() throws Exception {
    String name = name("Name1");
    GroupInfo group1 = gApi.groups().create(name).get();

    String newName = name("Name2");
    gApi.groups().id(group1.id).name(newName);

    GroupInfo group2 = gApi.groups().create(name).get();
    assertThat(group2.id).isNotEqualTo(group1.id);
  }

  @Test
  public void groupDescription() throws Exception {
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
  public void groupOptions() throws Exception {
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
  public void groupOwner() throws Exception {
    String name = name("group");
    GroupInfo info = gApi.groups().create(name).get();
    String adminUUID = adminGroupUuid().get();
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
  public void usersSeeTheirDirectMembershipWhenListingMembersRecursively() throws Exception {
    String group = createGroup("group");
    gApi.groups().id(group).addMembers(user.username);

    setApiUser(user);
    assertMembers(gApi.groups().id(group).members(true), user.fullName);
  }

  @Test
  public void usersDoNotSeeTheirIndirectMembershipWhenListingMembersRecursively() throws Exception {
    String group1 = createGroup("group1");
    String group2 = createGroup("group2");
    gApi.groups().id(group1).addGroups(group2);
    gApi.groups().id(group2).addMembers(user.username);

    setApiUser(user);
    List<AccountInfo> listedMembers = gApi.groups().id(group1).members(true);

    assertMembers(listedMembers);
  }

  @Test
  public void adminsSeeTheirIndirectMembershipWhenListingMembersRecursively() throws Exception {
    String ownerGroup = createGroup("ownerGroup", null);
    String group1 = createGroup("group1", ownerGroup);
    String group2 = createGroup("group2", ownerGroup);
    gApi.groups().id(group1).addGroups(group2);
    gApi.groups().id(group2).addMembers(admin.username);

    List<AccountInfo> listedMembers = gApi.groups().id(group1).members(true);

    assertMembers(listedMembers, admin.fullName);
  }

  @Test
  public void ownersSeeTheirIndirectMembershipWhenListingMembersRecursively() throws Exception {
    String ownerGroup = createGroup("ownerGroup", null);
    String group1 = createGroup("group1", ownerGroup);
    String group2 = createGroup("group2", ownerGroup);
    gApi.groups().id(group1).addGroups(group2);
    gApi.groups().id(ownerGroup).addMembers(user.username);
    gApi.groups().id(group2).addMembers(user.username);

    setApiUser(user);
    List<AccountInfo> listedMembers = gApi.groups().id(group1).members(true);

    assertMembers(listedMembers, user.fullName);
  }

  @Test
  public void defaultGroupsCreated() throws Exception {
    Iterable<String> names = gApi.groups().list().getAsMap().keySet();
    assertThat(names).containsAllOf("Administrators", "Non-Interactive Users").inOrder();
  }

  @Test
  public void listAllGroups() throws Exception {
    List<String> expectedGroups =
        groups.getAllGroupReferences(db).map(GroupReference::getName).sorted().collect(toList());
    assertThat(expectedGroups.size()).isAtLeast(2);
    assertThat(gApi.groups().list().getAsMap().keySet())
        .containsExactlyElementsIn(expectedGroups)
        .inOrder();
  }

  @Test
  public void getGroupsByOwner() throws Exception {
    String parent = createGroup("test-parent");
    List<String> children =
        Arrays.asList(createGroup("test-child1", parent), createGroup("test-child2", parent));

    // By UUID
    List<GroupInfo> owned = gApi.groups().list().withOwnedBy(groupUuid(parent).get()).get();
    assertThat(owned.stream().map(g -> g.name).collect(toList()))
        .containsExactlyElementsIn(children);

    // By name
    owned = gApi.groups().list().withOwnedBy(parent).get();
    assertThat(owned.stream().map(g -> g.name).collect(toList()))
        .containsExactlyElementsIn(children);

    // By group that does not own any others
    owned = gApi.groups().list().withOwnedBy(owned.get(0).id).get();
    assertThat(owned).isEmpty();

    // By non-existing group
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("Group Not Found: does-not-exist");
    gApi.groups().list().withOwnedBy("does-not-exist").get();
  }

  @Test
  public void onlyVisibleGroupsReturned() throws Exception {
    String newGroupName = name("newGroup");
    GroupInput in = new GroupInput();
    in.name = newGroupName;
    in.description = "a hidden group";
    in.visibleToAll = false;
    in.ownerId = adminGroupUuid().get();
    gApi.groups().create(in);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).doesNotContainKey(newGroupName);

    setApiUser(admin);
    gApi.groups().id(newGroupName).addMembers(user.username);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).containsKey(newGroupName);
  }

  @Test
  public void suggestGroup() throws Exception {
    Map<String, GroupInfo> groups = gApi.groups().list().withSuggest("adm").getAsMap();
    assertThat(groups).containsKey("Administrators");
    assertThat(groups).hasSize(1);
    assertBadRequest(gApi.groups().list().withSuggest("adm").withSubstring("foo"));
    assertBadRequest(gApi.groups().list().withSuggest("adm").withRegex("foo.*"));
    assertBadRequest(gApi.groups().list().withSuggest("adm").withUser("user"));
    assertBadRequest(gApi.groups().list().withSuggest("adm").withOwned(true));
    assertBadRequest(gApi.groups().list().withSuggest("adm").withVisibleToAll(true));
    assertBadRequest(gApi.groups().list().withSuggest("adm").withStart(1));
  }

  @Test
  public void withSubstring() throws Exception {
    String group = name("Abcdefghijklmnop");
    gApi.groups().create(group);

    // Choose a substring which isn't part of any group or test method within this class.
    String substring = "efghijk";
    Map<String, GroupInfo> groups = gApi.groups().list().withSubstring(substring).getAsMap();
    assertThat(groups).containsKey(group);
    assertThat(groups).hasSize(1);

    groups = gApi.groups().list().withSubstring("abcdefghi").getAsMap();
    assertThat(groups).containsKey(group);
    assertThat(groups).hasSize(1);

    String otherGroup = name("Abcdefghijklmnop2");
    gApi.groups().create(otherGroup);
    groups = gApi.groups().list().withSubstring(substring).getAsMap();
    assertThat(groups).hasSize(2);
    assertThat(groups).containsKey(group);
    assertThat(groups).containsKey(otherGroup);

    groups = gApi.groups().list().withSubstring("non-existing-substring").getAsMap();
    assertThat(groups).isEmpty();
  }

  @Test
  public void withRegex() throws Exception {
    Map<String, GroupInfo> groups = gApi.groups().list().withRegex("Admin.*").getAsMap();
    assertThat(groups).containsKey("Administrators");
    assertThat(groups).hasSize(1);

    groups = gApi.groups().list().withRegex("admin.*").getAsMap();
    assertThat(groups).isEmpty();

    groups = gApi.groups().list().withRegex(".*istrators").getAsMap();
    assertThat(groups).containsKey("Administrators");
    assertThat(groups).hasSize(1);

    assertBadRequest(gApi.groups().list().withRegex(".*istrators").withSubstring("s"));
  }

  @Test
  public void allGroupInfoFieldsSetCorrectly() throws Exception {
    InternalGroup adminGroup = adminGroup();
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

    // Add a removed member back again.
    g.addMembers(user.username);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(6);
    assertAuditEvent(auditEvents.get(0), Type.ADD_USER, admin.id, user.id);

    // Add a removed group back again.
    g.addGroups(otherGroup);
    auditEvents = g.auditLog();
    assertThat(auditEvents).hasSize(7);
    assertAuditEvent(auditEvents.get(0), Type.ADD_GROUP, admin.id, otherGroup);

    Timestamp lastDate = null;
    for (GroupAuditEventInfo auditEvent : auditEvents) {
      if (lastDate != null) {
        assertThat(lastDate).isAtLeast(auditEvent.date);
      }
      lastDate = auditEvent.date;
    }
  }

  // reindex is tested by {@link AbstractQueryGroupsTest#reindex}
  @Test
  public void reindexPermissions() throws Exception {
    TestAccount groupOwner = accountCreator.user2();
    GroupInput in = new GroupInput();
    in.name = name("group");
    in.members =
        Collections.singleton(groupOwner).stream().map(u -> u.id.toString()).collect(toList());
    in.visibleToAll = true;
    GroupInfo group = gApi.groups().create(in).get();

    // admin can reindex any group
    setApiUser(admin);
    gApi.groups().id(group.id).index();

    // group owner can reindex own group (group is owned by itself)
    setApiUser(groupOwner);
    gApi.groups().id(group.id).index();

    // user cannot reindex any group
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to index group");
    gApi.groups().id(group.id).index();
  }

  @Test
  public void pushToGroupBranchIsRejectedForAllUsersRepo() throws Exception {
    assume().that(groupsInNoteDb()).isTrue(); // branch only exists when groups are in NoteDb
    assertPushToGroupBranch(
        allUsers, RefNames.refsGroups(adminGroupUuid()), "group update not allowed");
  }

  @Test
  public void pushToDeletedGroupBranchIsRejectedForAllUsersRepo() throws Exception {
    String groupRef =
        RefNames.refsDeletedGroups(
            new AccountGroup.UUID(gApi.groups().create(name("foo")).get().id));
    createBranch(allUsers, groupRef);
    assertPushToGroupBranch(allUsers, groupRef, "group update not allowed");
  }

  @Test
  public void pushToGroupNamesBranchIsRejectedForAllUsersRepo() throws Exception {
    assume().that(groupsInNoteDb()).isTrue(); // branch only exists when groups are in NoteDb
    // refs/meta/group-names isn't usually available for fetch, so grant ACCESS_DATABASE
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    assertPushToGroupBranch(allUsers, RefNames.REFS_GROUPNAMES, "group update not allowed");
  }

  @Test
  public void pushToGroupsBranchForNonAllUsersRepo() throws Exception {
    assertCreateGroupBranch(project, null);
    String groupRef =
        RefNames.refsGroups(new AccountGroup.UUID(gApi.groups().create(name("foo")).get().id));
    createBranch(project, groupRef);
    assertPushToGroupBranch(project, groupRef, null);
  }

  @Test
  public void pushToDeletedGroupsBranchForNonAllUsersRepo() throws Exception {
    assertCreateGroupBranch(project, null);
    String groupRef =
        RefNames.refsDeletedGroups(
            new AccountGroup.UUID(gApi.groups().create(name("foo")).get().id));
    createBranch(project, groupRef);
    assertPushToGroupBranch(project, groupRef, null);
  }

  @Test
  public void pushToGroupNamesBranchForNonAllUsersRepo() throws Exception {
    createBranch(project, RefNames.REFS_GROUPNAMES);
    assertPushToGroupBranch(project, RefNames.REFS_GROUPNAMES, null);
  }

  private void assertPushToGroupBranch(
      Project.NameKey project, String groupRefName, String expectedErrorOnUpdate) throws Exception {
    grant(project, RefNames.REFS_GROUPS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPS + "*", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_DELETED_GROUPS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_DELETED_GROUPS + "*", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPNAMES, Permission.PUSH, false, REGISTERED_USERS);

    TestRepository<InMemoryRepository> repo = cloneProject(project);

    // update existing branch
    fetch(repo, groupRefName + ":groupRef");
    repo.reset("groupRef");
    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), repo, "Update group", "arbitraryFile.txt", "some content")
            .to(groupRefName);
    if (expectedErrorOnUpdate != null) {
      r.assertErrorStatus(expectedErrorOnUpdate);
    } else {
      r.assertOkStatus();
    }
  }

  private void assertCreateGroupBranch(Project.NameKey project, String expectedErrorOnCreate)
      throws Exception {
    grant(project, RefNames.REFS_GROUPS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPS + "*", Permission.PUSH, false, REGISTERED_USERS);
    TestRepository<InMemoryRepository> repo = cloneProject(project);
    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), repo, "Update group", "arbitraryFile.txt", "some content")
            .setParents(ImmutableList.of())
            .to(RefNames.REFS_GROUPS + name("bar"));
    if (expectedErrorOnCreate != null) {
      r.assertErrorStatus(expectedErrorOnCreate);
    } else {
      r.assertOkStatus();
    }
  }

  @Test
  public void pushToGroupBranchForReviewForAllUsersRepoIsRejectedOnSubmit() throws Exception {
    pushToGroupBranchForReviewAndSubmit(
        allUsers, RefNames.refsGroups(adminGroupUuid()), "group update not allowed");
  }

  @Test
  public void pushToGroupBranchForReviewForNonAllUsersRepoAndSubmit() throws Exception {
    String groupRef = RefNames.refsGroups(adminGroupUuid());
    createBranch(project, groupRef);
    pushToGroupBranchForReviewAndSubmit(project, groupRef, null);
  }

  @Test
  public void pushCustomInheritanceForAllUsersFails() throws Exception {
    TestRepository<InMemoryRepository> repo = cloneProject(allUsers, RefNames.REFS_CONFIG);

    String config =
        gApi.projects()
            .name(allUsers.get())
            .branch(RefNames.REFS_CONFIG)
            .file("project.config")
            .asString();

    Config cfg = new Config();
    cfg.fromText(config);
    cfg.setString("access", null, "inheritFrom", project.get());
    config = cfg.toText();

    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), repo, "Subject", "project.config", config)
            .to(RefNames.REFS_CONFIG);
    r.assertErrorStatus("invalid project configuration");
    r.assertMessage("All-Users must inherit from All-Projects");
  }

  @Test
  public void cannotCreateGroupBranch() throws Exception {
    testCannotCreateGroupBranch(
        RefNames.REFS_GROUPS + "*", RefNames.refsGroups(new AccountGroup.UUID(name("foo"))));
  }

  @Test
  public void cannotCreateDeletedGroupBranch() throws Exception {
    testCannotCreateGroupBranch(
        RefNames.REFS_DELETED_GROUPS + "*",
        RefNames.refsDeletedGroups(new AccountGroup.UUID(name("foo"))));
  }

  @Test
  @IgnoreGroupInconsistencies
  public void cannotCreateGroupNamesBranch() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    // Use ProjectResetter to restore the group names ref
    try (ProjectResetter resetter =
        projectResetter.builder().reset(allUsers, RefNames.REFS_GROUPNAMES).build()) {
      // Manually delete group names ref
      try (Repository repo = repoManager.openRepository(allUsers);
          RevWalk rw = new RevWalk(repo)) {
        RevCommit commit = rw.parseCommit(repo.exactRef(RefNames.REFS_GROUPNAMES).getObjectId());
        RefUpdate updateRef = repo.updateRef(RefNames.REFS_GROUPNAMES);
        updateRef.setExpectedOldObjectId(commit.toObjectId());
        updateRef.setNewObjectId(ObjectId.zeroId());
        updateRef.setForceUpdate(true);
        assertThat(updateRef.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }

      // refs/meta/group-names is only visible with ACCESS_DATABASE
      allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

      testCannotCreateGroupBranch(RefNames.REFS_GROUPNAMES, RefNames.REFS_GROUPNAMES);
    }
  }

  private void testCannotCreateGroupBranch(String refPattern, String groupRef) throws Exception {
    grant(allUsers, refPattern, Permission.CREATE);
    grant(allUsers, refPattern, Permission.PUSH);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), allUsersRepo).to(groupRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create group branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(groupRef)).isNull();
    }
  }

  @Test
  public void cannotDeleteGroupBranch() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();
    testCannotDeleteGroupBranch(RefNames.REFS_GROUPS + "*", RefNames.refsGroups(adminGroupUuid()));
  }

  @Test
  public void cannotDeleteDeletedGroupBranch() throws Exception {
    String groupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID(name("foo")));
    createBranch(allUsers, groupRef);
    testCannotDeleteGroupBranch(RefNames.REFS_DELETED_GROUPS + "*", groupRef);
  }

  @Test
  public void cannotDeleteGroupNamesBranch() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    // refs/meta/group-names is only visible with ACCESS_DATABASE
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    testCannotDeleteGroupBranch(RefNames.REFS_GROUPNAMES, RefNames.REFS_GROUPNAMES);
  }

  private void testCannotDeleteGroupBranch(String refPattern, String groupRef) throws Exception {
    grant(allUsers, refPattern, Permission.DELETE, true, REGISTERED_USERS);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushResult r = deleteRef(allUsersRepo, groupRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(groupRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete group branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(groupRef)).isNotNull();
    }
  }

  @Test
  public void defaultPermissionsOnGroupBranches() throws Exception {
    assertPermissions(
        allUsers, groupRef(REGISTERED_USERS), RefNames.REFS_GROUPS + "*", true, Permission.READ);
  }

  @Test
  @Sandboxed
  public void blockReviewDbUpdatesOnGroupCreation() throws Exception {
    assume().that(groupsInNoteDb()).isFalse();
    try (AutoCloseable ctx = createBlockReviewDbGroupUpdatesContext()) {
      gApi.groups().create(name("foo"));
      fail("Expected RestApiException: Updates to groups in ReviewDb are blocked");
    } catch (RestApiException e) {
      assertWriteGroupToReviewDbBlockedException(e);
    }
  }

  @Test
  @Sandboxed
  public void blockReviewDbUpdatesOnGroupUpdate() throws Exception {
    assume().that(groupsInNoteDb()).isFalse();
    String group1 = gApi.groups().create(name("foo")).get().id;
    String group2 = gApi.groups().create(name("bar")).get().id;
    try (AutoCloseable ctx = createBlockReviewDbGroupUpdatesContext()) {
      gApi.groups().id(group1).addGroups(group2);
      fail("Expected RestApiException: Updates to groups in ReviewDb are blocked");
    } catch (RestApiException e) {
      assertWriteGroupToReviewDbBlockedException(e);
    }
  }

  private void assertWriteGroupToReviewDbBlockedException(Exception e) throws Exception {
    Throwable t = Throwables.getRootCause(e);
    assertThat(t).isInstanceOf(OrmException.class);
    assertThat(t.getMessage()).isEqualTo("Updates to groups in ReviewDb are blocked");
  }

  @Test
  @IgnoreGroupInconsistencies
  public void stalenessChecker() throws Exception {
    assume().that(readGroupsFromNoteDb()).isTrue();

    // Newly created group is not stale
    GroupInfo groupInfo = gApi.groups().create(name("foo")).get();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(groupInfo.id);
    assertThat(stalenessChecker.isStale(groupUuid)).isFalse();

    // Manual update makes index document stale
    String groupRef = RefNames.refsGroups(groupUuid);
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(repo.exactRef(groupRef).getObjectId());
      ObjectId emptyCommit = createCommit(repo, commit.getFullMessage(), commit.getTree());
      RefUpdate updateRef = repo.updateRef(groupRef);
      updateRef.setExpectedOldObjectId(commit.toObjectId());
      updateRef.setNewObjectId(emptyCommit);
      assertThat(updateRef.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);
    }
    assertStaleGroupAndReindex(groupUuid);

    // Manually delete group
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(repo.exactRef(groupRef).getObjectId());
      RefUpdate updateRef = repo.updateRef(groupRef);
      updateRef.setExpectedOldObjectId(commit.toObjectId());
      updateRef.setNewObjectId(ObjectId.zeroId());
      updateRef.setForceUpdate(true);
      assertThat(updateRef.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
    assertStaleGroupAndReindex(groupUuid);
  }

  @Test
  public void groupNamesWithLeadingAndTrailingWhitespace() throws Exception {
    for (String leading : ImmutableList.of("", " ", "  ")) {
      for (String trailing : ImmutableList.of("", " ", "  ")) {
        String name = leading + name("group") + trailing;
        GroupInfo g = gApi.groups().create(name).get();
        assertThat(g.name).isEqualTo(name);
      }
    }
  }

  @Test
  @Sandboxed
  public void groupsOfUserCanBeListedInSlaveMode() throws Exception {
    assume().that(readGroupsFromNoteDb()).isTrue();

    GroupInput groupInput = new GroupInput();
    groupInput.name = name("contributors");
    groupInput.members = ImmutableList.of(user.username);
    gApi.groups().create(groupInput).get();
    restartAsSlave();

    setApiUser(user);
    List<GroupInfo> groups = gApi.groups().list().withUser(user.username).get();
    ImmutableList<String> groupNames =
        groups.stream().map(group -> group.name).collect(toImmutableList());
    assertThat(groupNames).contains(groupInput.name);
  }

  @Test
  @Sandboxed
  @GerritConfig(name = "index.scheduledIndexer.enabled", value = "false")
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  @IgnoreGroupInconsistencies
  public void reindexGroupsInSlaveMode() throws Exception {
    assume().that(readGroupsFromNoteDb()).isTrue();
    assume().that(cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, false)).isTrue();

    List<AccountGroup.UUID> expectedGroups =
        groups.getAllGroupReferences(db).map(GroupReference::getUUID).collect(toList());
    assertThat(expectedGroups.size()).isAtLeast(2);

    restartAsSlave();

    GroupIndexedCounter groupIndexedCounter = new GroupIndexedCounter();
    RegistrationHandle groupIndexEventCounterHandle =
        groupIndexedListeners.add(groupIndexedCounter);
    try {
      // On startup of the slave the test framework ensures that the group index is up-to-date.
      // Hence running the reindexer doesn't need to reindex any group.
      slaveGroupIndexer.run();
      groupIndexedCounter.assertNoReindex();

      // Create a group without updating the cache or index,
      // then run the reindexer -> only the new group is reindexed.
      String groupName = "foo";
      AccountGroup.UUID groupUuid = new AccountGroup.UUID(groupName + "-UUID");
      groupsUpdate.createGroupInNoteDb(
          InternalGroupCreation.builder()
              .setGroupUUID(groupUuid)
              .setNameKey(new AccountGroup.NameKey(groupName))
              .setId(new AccountGroup.Id(seq.nextGroupId()))
              .build(),
          InternalGroupUpdate.builder().build());
      slaveGroupIndexer.run();
      groupIndexedCounter.assertReindexOf(groupUuid);

      // Update a group without updating the cache or index,
      // then run the reindexer -> only the updated group is reindexed.
      groupsUpdate.updateGroupInDb(
          db, groupUuid, InternalGroupUpdate.builder().setDescription("bar").build());
      slaveGroupIndexer.run();
      groupIndexedCounter.assertReindexOf(groupUuid);

      // Delete a group  without updating the cache or index,
      // then run the reindexer -> only the deleted group is reindexed.
      try (Repository repo = repoManager.openRepository(allUsers)) {
        RefUpdate u = repo.updateRef(RefNames.refsGroups(groupUuid));
        u.setForceUpdate(true);
        assertThat(u.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }
      slaveGroupIndexer.run();
      groupIndexedCounter.assertReindexOf(groupUuid);
    } finally {
      groupIndexEventCounterHandle.remove();
    }
  }

  private void assertStaleGroupAndReindex(AccountGroup.UUID groupUuid) throws IOException {
    // Evict group from cache to be sure that we use the index state for staleness checks.
    groupCache.evict(groupUuid);
    assertThat(stalenessChecker.isStale(groupUuid)).isTrue();

    // Reindex fixes staleness
    groupIndexer.index(groupUuid);
    assertThat(stalenessChecker.isStale(groupUuid)).isFalse();
  }

  private void pushToGroupBranchForReviewAndSubmit(
      Project.NameKey project, String groupRef, String expectedError) throws Exception {
    assume().that(groupsInNoteDb()).isTrue(); // branch only exists when groups are in NoteDb

    grantLabel(
        "Code-Review", -2, 2, project, RefNames.REFS_GROUPS + "*", false, REGISTERED_USERS, false);
    grant(project, RefNames.REFS_GROUPS + "*", Permission.SUBMIT, false, REGISTERED_USERS);

    TestRepository<InMemoryRepository> repo = cloneProject(project);
    fetch(repo, groupRef + ":groupRef");
    repo.reset("groupRef");

    PushOneCommit.Result r =
        pushFactory
            .create(
                db, admin.getIdent(), repo, "Update group config", "group.config", "some content")
            .to(MagicBranch.NEW_CHANGE + groupRef);
    r.assertOkStatus();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(groupRef);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());

    if (expectedError != null) {
      exception.expect(ResourceConflictException.class);
      exception.expectMessage("group update not allowed");
    }
    gApi.changes().id(r.getChangeId()).current().submit();
  }

  private void createBranch(Project.NameKey project, String ref) throws IOException {
    try (Repository r = repoManager.openRepository(project);
        ObjectInserter oi = r.newObjectInserter();
        RevWalk rw = new RevWalk(r)) {
      ObjectId emptyCommit = createCommit(r, "Test change");
      RefUpdate updateRef = r.updateRef(ref);
      updateRef.setExpectedOldObjectId(ObjectId.zeroId());
      updateRef.setNewObjectId(emptyCommit);
      assertThat(updateRef.update(rw)).isEqualTo(RefUpdate.Result.NEW);
    }
  }

  private ObjectId createCommit(Repository repo, String commitMessage) throws IOException {
    return createCommit(repo, commitMessage, null);
  }

  private ObjectId createCommit(Repository repo, String commitMessage, @Nullable ObjectId treeId)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      if (treeId == null) {
        treeId = oi.insert(Constants.OBJ_TREE, new byte[] {});
      }

      PersonIdent ident = new PersonIdent(serverIdent.get(), TimeUtil.nowTs());
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(treeId);
      cb.setCommitter(ident);
      cb.setAuthor(ident);
      cb.setMessage(commitMessage);

      ObjectId commit = oi.insert(cb);
      oi.flush();
      return commit;
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

  private void assertBadRequest(ListRequest req) throws Exception {
    try {
      req.get();
      fail("Expected BadRequestException");
    } catch (BadRequestException e) {
      // Expected
    }
  }

  private boolean groupsInNoteDb() {
    return cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, false);
  }

  private boolean readGroupsFromNoteDb() {
    return groupsInNoteDb() && cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, false);
  }

  private AutoCloseable createBlockReviewDbGroupUpdatesContext() {
    cfg.setBoolean("user", null, "blockReviewDbGroupUpdates", true);
    return new AutoCloseable() {
      @Override
      public void close() {
        cfg.setBoolean("user", null, "blockReviewDbGroupUpdates", false);
      }
    };
  }

  @Target({METHOD})
  @Retention(RUNTIME)
  private @interface IgnoreGroupInconsistencies {}

  /** Checks if a group is indexed the correct number of times. */
  private static class GroupIndexedCounter implements GroupIndexedListener {
    private final AtomicLongMap<String> countsByGroup = AtomicLongMap.create();

    @Override
    public void onGroupIndexed(String uuid) {
      countsByGroup.incrementAndGet(uuid);
    }

    void clear() {
      countsByGroup.clear();
    }

    long getCount(AccountGroup.UUID groupUuid) {
      return countsByGroup.get(groupUuid.get());
    }

    void assertReindexOf(AccountGroup.UUID groupUuid) {
      assertReindexOf(ImmutableList.of(groupUuid));
    }

    void assertReindexOf(List<AccountGroup.UUID> groupUuids) {
      for (AccountGroup.UUID groupUuid : groupUuids) {
        assertThat(getCount(groupUuid)).named(groupUuid.get()).isEqualTo(1);
      }
      assertThat(countsByGroup).hasSize(groupUuids.size());
      clear();
    }

    void assertNoReindex() {
      assertThat(countsByGroup).isEmpty();
    }
  }
}
