// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescription.Basic;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.GroupMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.Type;
import com.google.gerrit.extensions.common.GroupAuditEventInfo.UserMemberAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.CommitUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.GroupsConsistencyChecker;
import com.google.gerrit.server.group.testing.InternalGroupSubject;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gerrit.testing.TestTimeUtil.TempClockStep;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gerrit.truth.OptionalSubject;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_166_to_167_WithGroupsInReviewDbTest {
  private static Config createConfig() {
    Config config = new Config();
    config.setString(GerritServerIdProvider.SECTION, null, GerritServerIdProvider.KEY, "1234567");

    // Enable groups in ReviewDb. This means the primary storage for groups is ReviewDb.
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, false);

    return config;
  }

  @Rule
  public InMemoryTestEnvironment testEnv =
      new InMemoryTestEnvironment(Schema_166_to_167_WithGroupsInReviewDbTest::createConfig);

  @Inject private GerritApi gApi;
  @Inject private Schema_167 schema167;
  @Inject private ReviewDb db;
  @Inject private GitRepositoryManager gitRepoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private GroupsConsistencyChecker consistencyChecker;
  @Inject private IdentifiedUser currentUser;
  @Inject private @GerritServerId String serverId;
  @Inject private @GerritPersonIdent PersonIdent serverIdent;
  @Inject private GroupBundle.Factory groupBundleFactory;
  @Inject private GroupBackend groupBackend;
  @Inject private DynamicSet<GroupBackend> backends;
  @Inject private Sequences seq;

  private JdbcSchema jdbcSchema;

  @Before
  public void initDb() throws Exception {
    jdbcSchema = ReviewDbWrapper.unwrapJbdcSchema(db);

    try (Statement stmt = jdbcSchema.getConnection().createStatement()) {
      stmt.execute(
          "CREATE TABLE account_groups ("
              + " group_uuid varchar(255) DEFAULT '' NOT NULL,"
              + " group_id INTEGER DEFAULT 0 NOT NULL,"
              + " name varchar(255) DEFAULT '' NOT NULL,"
              + " created_on TIMESTAMP,"
              + " description CLOB,"
              + " owner_group_uuid varchar(255) DEFAULT '' NOT NULL,"
              + " visible_to_all CHAR(1) DEFAULT 'N' NOT NULL"
              + ")");

      stmt.execute(
          "CREATE TABLE account_group_members ("
              + " group_id INTEGER DEFAULT 0 NOT NULL,"
              + " account_id INTEGER DEFAULT 0 NOT NULL"
              + ")");

      stmt.execute(
          "CREATE TABLE account_group_members_audit ("
              + " group_id INTEGER DEFAULT 0 NOT NULL,"
              + " account_id INTEGER DEFAULT 0 NOT NULL,"
              + " added_by INTEGER DEFAULT 0 NOT NULL,"
              + " added_on TIMESTAMP,"
              + " removed_by INTEGER,"
              + " removed_on TIMESTAMP"
              + ")");

      stmt.execute(
          "CREATE TABLE account_group_by_id ("
              + " group_id INTEGER DEFAULT 0 NOT NULL,"
              + " include_uuid VARCHAR(255) DEFAULT '' NOT NULL"
              + ")");

      stmt.execute(
          "CREATE TABLE account_group_by_id_aud ("
              + " group_id INTEGER DEFAULT 0 NOT NULL,"
              + " include_uuid VARCHAR(255) DEFAULT '' NOT NULL,"
              + " added_by INTEGER DEFAULT 0 NOT NULL,"
              + " added_on TIMESTAMP,"
              + " removed_by INTEGER,"
              + " removed_on TIMESTAMP"
              + ")");
    }
  }

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void reviewDbOnlyGroupsAreMigratedToNoteDb() throws Exception {
    // Create groups only in ReviewDb
    AccountGroup group1 = newGroup().setName("verifiers").build();
    AccountGroup group2 = newGroup().setName("contributors").build();
    storeInReviewDb(group1, group2);

    executeSchemaMigration(schema167, group1, group2);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).containsAllOf("verifiers", "contributors");
  }

  @Test
  public void alreadyExistingGroupsAreMigratedToNoteDb() throws Exception {
    // Create group in NoteDb and ReviewDb
    GroupInput groupInput = new GroupInput();
    groupInput.name = "verifiers";
    groupInput.description = "old";
    GroupInfo group1 = gApi.groups().create(groupInput).get();
    storeInReviewDb(group1);

    // Update group only in ReviewDb
    AccountGroup group1InReviewDb = getFromReviewDb(new AccountGroup.Id(group1.groupId));
    group1InReviewDb.setDescription("new");
    updateInReviewDb(group1InReviewDb);

    // Create a second group in NoteDb and ReviewDb
    GroupInfo group2 = gApi.groups().create("contributors").get();
    storeInReviewDb(group2);

    executeSchemaMigration(schema167, group1, group2);

    // Verify that both groups are present in NoteDb
    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).containsAllOf("verifiers", "contributors");

    // Verify that group1 has the description from ReviewDb
    Optional<InternalGroup> group1InNoteDb = getGroupFromNoteDb(new AccountGroup.UUID(group1.id));
    assertThatGroup(group1InNoteDb).value().description().isEqualTo("new");
  }

  @Test
  public void adminGroupIsMigratedToNoteDb() throws Exception {
    // Administrators group is automatically created for all Gerrit servers (NoteDb only).
    GroupInfo adminGroup = gApi.groups().id("Administrators").get();
    storeInReviewDb(adminGroup);

    executeSchemaMigration(schema167, adminGroup);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).contains("Administrators");
  }

  @Test
  public void nonInteractiveUsersGroupIsMigratedToNoteDb() throws Exception {
    // 'Non-Interactive Users' group is automatically created for all Gerrit servers (NoteDb only).
    GroupInfo nonInteractiveUsersGroup = gApi.groups().id("Non-Interactive Users").get();
    storeInReviewDb(nonInteractiveUsersGroup);

    executeSchemaMigration(schema167, nonInteractiveUsersGroup);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).contains("Non-Interactive Users");
  }

  @Test
  public void groupsAreConsistentAfterMigrationToNoteDb() throws Exception {
    // Administrators group are automatically created for all Gerrit servers (NoteDb only).
    GroupInfo adminGroup = gApi.groups().id("Administrators").get();
    GroupInfo nonInteractiveUsersGroup = gApi.groups().id("Non-Interactive Users").get();
    storeInReviewDb(adminGroup, nonInteractiveUsersGroup);

    AccountGroup group1 = newGroup().setName("verifiers").build();
    AccountGroup group2 = newGroup().setName("contributors").build();
    storeInReviewDb(group1, group2);

    executeSchemaMigration(schema167, group1, group2);

    List<ConsistencyCheckInfo.ConsistencyProblemInfo> consistencyProblems =
        consistencyChecker.check();
    assertThat(consistencyProblems).isEmpty();
  }

  @Test
  public void nameIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setName("verifiers").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().name().isEqualTo("verifiers");
  }

  @Test
  public void emptyNameIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setName("").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().name().isEqualTo("");
  }

  @Test
  public void uuidIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("ABCDEF");
    AccountGroup group = newGroup().setGroupUuid(groupUuid).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(groupUuid);
    assertThatGroup(groupInNoteDb).value().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void idIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.Id id = new AccountGroup.Id(12345);
    AccountGroup group = newGroup().setId(id).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().id().isEqualTo(id);
  }

  @Test
  public void createdOnIsKeptDuringMigrationToNoteDb() throws Exception {
    Timestamp createdOn =
        Timestamp.from(
            LocalDate.of(2018, Month.FEBRUARY, 20)
                .atTime(18, 2, 56)
                .atZone(ZoneOffset.UTC)
                .toInstant());
    AccountGroup group = newGroup().setCreatedOn(createdOn).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().createdOn().isEqualTo(createdOn);
  }

  @Test
  public void ownerUuidIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.UUID ownerGroupUuid = new AccountGroup.UUID("UVWXYZ");
    AccountGroup group = newGroup().setOwnerGroupUuid(ownerGroupUuid).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().ownerGroupUuid().isEqualTo(ownerGroupUuid);
  }

  @Test
  public void descriptionIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setDescription("A test group").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().description().isEqualTo("A test group");
  }

  @Test
  public void absentDescriptionIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().description().isNull();
  }

  @Test
  public void visibleToAllIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setVisibleToAll(true).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().visibleToAll().isTrue();
  }

  @Test
  public void membersAreKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);
    Account.Id member1 = new Account.Id(23456);
    Account.Id member2 = new Account.Id(93483);
    addMembersInReviewDb(group.getId(), member1, member2);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().members().containsExactly(member1, member2);
  }

  @Test
  public void subgroupsAreKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);
    AccountGroup.UUID subgroup1 = new AccountGroup.UUID("FGHIKL");
    AccountGroup.UUID subgroup2 = new AccountGroup.UUID("MNOPQR");
    addSubgroupsInReviewDb(group.getId(), subgroup1, subgroup2);

    executeSchemaMigration(schema167, group);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().subgroups().containsExactly(subgroup1, subgroup2);
  }

  @Test
  public void logFormatWithAccountsAndGerritGroups() throws Exception {
    AccountInfo user1 = createAccount("user1");
    AccountInfo user2 = createAccount("user2");

    AccountGroup group1 = createInReviewDb("group1");
    AccountGroup group2 = createInReviewDb("group2");
    AccountGroup group3 = createInReviewDb("group3");

    // Add some accounts
    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      addMembersInReviewDb(
          group1.getId(), new Account.Id(user1._accountId), new Account.Id(user2._accountId));
    }
    TimeUtil.nowTs();

    // Add some Gerrit groups
    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      addSubgroupsInReviewDb(group1.getId(), group2.getGroupUUID(), group3.getGroupUUID());
    }

    executeSchemaMigration(schema167, group1, group2, group3);

    GroupBundle noteDbBundle = readGroupBundleFromNoteDb(group1.getGroupUUID());

    ImmutableList<CommitInfo> log = log(group1);
    assertThat(log).hasSize(4);

    // Verify commit that created the group
    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbBundle.group().getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    // Verify commit that the group creator as member
    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(1)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(1)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    // Verify commit that added members
    assertThat(log.get(2))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + ("Add: user1 <" + user1._accountId + "@" + serverId + ">\n")
                + ("Add: user2 <" + user2._accountId + "@" + serverId + ">"));
    assertThat(log.get(2)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(2)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    // Verify commit that added Gerrit groups
    assertThat(log.get(3))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + ("Add-group: " + group2.getName() + " <" + group2.getGroupUUID().get() + ">\n")
                + ("Add-group: " + group3.getName() + " <" + group3.getGroupUUID().get() + ">"));
    assertThat(log.get(3)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(3)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(3)).committer().hasSameDateAs(log.get(3).author);

    // Verify that audit log is correctly read by Gerrit
    List<? extends GroupAuditEventInfo> auditEvents =
        gApi.groups().id(group1.getGroupUUID().get()).auditLog();
    assertThat(auditEvents).hasSize(5);
    AccountInfo currentUserInfo = gApi.accounts().id(currentUser.getAccountId().get()).get();
    assertMemberAuditEvent(
        auditEvents.get(4), Type.ADD_USER, currentUser.getAccountId(), currentUserInfo);
    assertMemberAuditEvents(
        auditEvents.get(3),
        auditEvents.get(2),
        Type.ADD_USER,
        currentUser.getAccountId(),
        user1,
        user2);
    assertSubgroupAuditEvents(
        auditEvents.get(1),
        auditEvents.get(0),
        Type.ADD_GROUP,
        currentUser.getAccountId(),
        toGroupInfo(group2),
        toGroupInfo(group3));
  }

  @Test
  public void logFormatWithSystemGroups() throws Exception {
    AccountGroup group = createInReviewDb("group");

    try (TempClockStep step = TestTimeUtil.freezeClock()) {
      addSubgroupsInReviewDb(
          group.getId(), SystemGroupBackend.ANONYMOUS_USERS, SystemGroupBackend.REGISTERED_USERS);
    }

    executeSchemaMigration(schema167, group);

    GroupBundle noteDbBundle = readGroupBundleFromNoteDb(group.getGroupUUID());

    ImmutableList<CommitInfo> log = log(group);
    assertThat(log).hasSize(3);

    // Verify commit that created the group
    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbBundle.group().getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    // Verify commit that the group creator as member
    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(1)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(1)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    // Verify commit that added system groups
    assertThat(log.get(2))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + "Add-group: Anonymous Users <global:Anonymous-Users>\n"
                + "Add-group: Registered Users <global:Registered-Users>");
    assertThat(log.get(2)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(2)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    // Verify that audit log is correctly read by Gerrit
    List<? extends GroupAuditEventInfo> auditEvents =
        gApi.groups().id(group.getGroupUUID().get()).auditLog();
    assertThat(auditEvents).hasSize(3);
    AccountInfo currentUserInfo = gApi.accounts().id(currentUser.getAccountId().get()).get();
    assertMemberAuditEvent(
        auditEvents.get(2), Type.ADD_USER, currentUser.getAccountId(), currentUserInfo);
    assertSubgroupAuditEvents(
        auditEvents.get(1),
        auditEvents.get(0),
        Type.ADD_GROUP,
        currentUser.getAccountId(),
        groupInfoForExternalGroup(SystemGroupBackend.ANONYMOUS_USERS),
        groupInfoForExternalGroup(SystemGroupBackend.REGISTERED_USERS));
  }

  @Test
  public void logFormatWithExternalGroup() throws Exception {
    AccountGroup group = createInReviewDb("group");

    backends.add(new TestGroupBackend());
    AccountGroup.UUID subgroupUuid = TestGroupBackend.createUuuid("foo");

    assertThat(groupBackend.handles(subgroupUuid)).isTrue();
    addSubgroupsInReviewDb(group.getId(), subgroupUuid);

    executeSchemaMigration(schema167, group);

    GroupBundle noteDbBundle = readGroupBundleFromNoteDb(group.getGroupUUID());

    ImmutableList<CommitInfo> log = log(group);
    assertThat(log).hasSize(3);

    // Verify commit that created the group
    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbBundle.group().getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    // Verify commit that the group creator as member
    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(1)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(1)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    // Verify commit that added system groups
    // Note: The schema migration can only resolve names of Gerrit groups, not of external groups
    // and system groups, hence the UUID shows up in commit messages where we would otherwise
    // expect the group name.
    assertThat(log.get(2))
        .message()
        .isEqualTo(
            "Update group\n"
                + "\n"
                + "Add-group: "
                + TestGroupBackend.PREFIX
                + "foo <"
                + TestGroupBackend.PREFIX
                + "foo>");
    assertThat(log.get(2)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(2)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    // Verify that audit log is correctly read by Gerrit
    List<? extends GroupAuditEventInfo> auditEvents =
        gApi.groups().id(group.getGroupUUID().get()).auditLog();
    assertThat(auditEvents).hasSize(2);
    AccountInfo currentUserInfo = gApi.accounts().id(currentUser.getAccountId().get()).get();
    assertMemberAuditEvent(
        auditEvents.get(1), Type.ADD_USER, currentUser.getAccountId(), currentUserInfo);
    assertSubgroupAuditEvent(
        auditEvents.get(0),
        Type.ADD_GROUP,
        currentUser.getAccountId(),
        groupInfoForExternalGroup(subgroupUuid));
  }

  @Test
  public void logFormatWithNonExistingExternalGroup() throws Exception {
    AccountGroup group = createInReviewDb("group");

    AccountGroup.UUID subgroupUuid = new AccountGroup.UUID("notExisting:foo");

    assertThat(groupBackend.handles(subgroupUuid)).isFalse();
    addSubgroupsInReviewDb(group.getId(), subgroupUuid);

    executeSchemaMigration(schema167, group);

    GroupBundle noteDbBundle = readGroupBundleFromNoteDb(group.getGroupUUID());

    ImmutableList<CommitInfo> log = log(group);
    assertThat(log).hasSize(3);

    // Verify commit that created the group
    assertThat(log.get(0)).message().isEqualTo("Create group");
    assertThat(log.get(0)).author().name().isEqualTo(serverIdent.getName());
    assertThat(log.get(0)).author().email().isEqualTo(serverIdent.getEmailAddress());
    assertThat(log.get(0)).author().date().isEqualTo(noteDbBundle.group().getCreatedOn());
    assertThat(log.get(0)).author().tz().isEqualTo(serverIdent.getTimeZoneOffset());
    assertThat(log.get(0)).committer().isEqualTo(log.get(0).author);

    // Verify commit that the group creator as member
    assertThat(log.get(1))
        .message()
        .isEqualTo(
            "Update group\n\nAdd: "
                + currentUser.getName()
                + " <"
                + currentUser.getAccountId()
                + "@"
                + serverId
                + ">");
    assertThat(log.get(1)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(1)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(1)).committer().hasSameDateAs(log.get(1).author);

    // Verify commit that added system groups
    // Note: The schema migration can only resolve names of Gerrit groups, not of external groups
    // and system groups, hence the UUID shows up in commit messages where we would otherwise
    // expect the group name.
    assertThat(log.get(2))
        .message()
        .isEqualTo("Update group\n" + "\n" + "Add-group: notExisting:foo <notExisting:foo>");
    assertThat(log.get(2)).author().name().isEqualTo(currentUser.getName());
    assertThat(log.get(2)).author().email().isEqualTo(currentUser.getAccountId() + "@" + serverId);
    assertThat(log.get(2)).committer().hasSameDateAs(log.get(2).author);

    // Verify that audit log is correctly read by Gerrit
    List<? extends GroupAuditEventInfo> auditEvents =
        gApi.groups().id(group.getGroupUUID().get()).auditLog();
    assertThat(auditEvents).hasSize(2);
    AccountInfo currentUserInfo = gApi.accounts().id(currentUser.getAccountId().get()).get();
    assertMemberAuditEvent(
        auditEvents.get(1), Type.ADD_USER, currentUser.getAccountId(), currentUserInfo);
    assertSubgroupAuditEvent(
        auditEvents.get(0),
        Type.ADD_GROUP,
        currentUser.getAccountId(),
        groupInfoForExternalGroup(subgroupUuid));
  }

  private static TestGroup.Builder newGroup() {
    return TestGroup.builder();
  }

  private AccountGroup createInReviewDb(String groupName) throws Exception {
    AccountGroup group =
        new AccountGroup(
            new AccountGroup.NameKey(groupName),
            new AccountGroup.Id(seq.nextGroupId()),
            GroupUUID.make(groupName, serverIdent),
            TimeUtil.nowTs());
    storeInReviewDb(group);
    addMembersInReviewDb(group.getId(), currentUser.getAccountId());
    return group;
  }

  private void storeInReviewDb(GroupInfo... groups) throws Exception {
    storeInReviewDb(
        Arrays.stream(groups)
            .map(Schema_166_to_167_WithGroupsInReviewDbTest::toAccountGroup)
            .toArray(AccountGroup[]::new));
  }

  private void storeInReviewDb(AccountGroup... groups) throws Exception {
    try (PreparedStatement stmt =
        jdbcSchema
            .getConnection()
            .prepareStatement(
                "INSERT INTO account_groups"
                    + " (group_uuid,"
                    + " group_id,"
                    + " name,"
                    + " description,"
                    + " created_on,"
                    + " owner_group_uuid,"
                    + " visible_to_all) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
      for (AccountGroup group : groups) {
        stmt.setString(1, group.getGroupUUID().get());
        stmt.setInt(2, group.getId().get());
        stmt.setString(3, group.getName());
        stmt.setString(4, group.getDescription());
        stmt.setTimestamp(5, group.getCreatedOn());
        stmt.setString(6, group.getOwnerGroupUUID().get());
        stmt.setString(7, group.isVisibleToAll() ? "Y" : "N");
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  private void updateInReviewDb(AccountGroup... groups) throws Exception {
    try (PreparedStatement stmt =
        jdbcSchema
            .getConnection()
            .prepareStatement(
                "UPDATE account_groups SET"
                    + " group_uuid = ?,"
                    + " name = ?,"
                    + " description = ?,"
                    + " created_on = ?,"
                    + " owner_group_uuid = ?,"
                    + " visible_to_all = ?"
                    + " WHERE group_id = ?")) {
      for (AccountGroup group : groups) {
        stmt.setString(1, group.getGroupUUID().get());
        stmt.setString(2, group.getName());
        stmt.setString(3, group.getDescription());
        stmt.setTimestamp(4, group.getCreatedOn());
        stmt.setString(5, group.getOwnerGroupUUID().get());
        stmt.setString(6, group.isVisibleToAll() ? "Y" : "N");
        stmt.setInt(7, group.getId().get());
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  private AccountGroup getFromReviewDb(AccountGroup.Id groupId) throws Exception {
    try (Statement stmt = jdbcSchema.getConnection().createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT group_uuid,"
                    + " name,"
                    + " description,"
                    + " created_on,"
                    + " owner_group_uuid,"
                    + " visible_to_all"
                    + " FROM account_groups"
                    + " WHERE group_id = "
                    + groupId.get())) {
      if (!rs.next()) {
        throw new OrmException(String.format("Group %s not found", groupId.get()));
      }

      AccountGroup.UUID groupUuid = new AccountGroup.UUID(rs.getString(1));
      AccountGroup.NameKey groupName = new AccountGroup.NameKey(rs.getString(2));
      String description = rs.getString(3);
      Timestamp createdOn = rs.getTimestamp(4);
      AccountGroup.UUID ownerGroupUuid = new AccountGroup.UUID(rs.getString(5));
      boolean visibleToAll = "Y".equals(rs.getString(6));

      AccountGroup group = new AccountGroup(groupName, groupId, groupUuid, createdOn);
      group.setDescription(description);
      group.setOwnerGroupUUID(ownerGroupUuid);
      group.setVisibleToAll(visibleToAll);

      if (rs.next()) {
        throw new OrmException(String.format("Group ID %s is ambiguous", groupId.get()));
      }

      return group;
    }
  }

  private void addMembersInReviewDb(AccountGroup.Id groupId, Account.Id... memberIds)
      throws Exception {
    try (PreparedStatement addMemberStmt =
            jdbcSchema
                .getConnection()
                .prepareStatement(
                    "INSERT INTO account_group_members"
                        + " (group_id,"
                        + " account_id) VALUES ("
                        + groupId.get()
                        + ", ?)");
        PreparedStatement addMemberAuditStmt =
            jdbcSchema
                .getConnection()
                .prepareStatement(
                    "INSERT INTO account_group_members_audit"
                        + " (group_id,"
                        + " account_id,"
                        + " added_by,"
                        + " added_on) VALUES ("
                        + groupId.get()
                        + ", ?, "
                        + currentUser.getAccountId().get()
                        + ", ?)")) {
      Timestamp addedOn = TimeUtil.nowTs();
      for (Account.Id memberId : memberIds) {
        addMemberStmt.setInt(1, memberId.get());
        addMemberStmt.addBatch();

        addMemberAuditStmt.setInt(1, memberId.get());
        addMemberAuditStmt.setTimestamp(2, addedOn);
        addMemberAuditStmt.addBatch();
      }
      addMemberStmt.executeBatch();
      addMemberAuditStmt.executeBatch();
    }
  }

  private void addSubgroupsInReviewDb(AccountGroup.Id groupId, AccountGroup.UUID... subgroupUuids)
      throws Exception {
    try (PreparedStatement addSubGroupStmt =
            jdbcSchema
                .getConnection()
                .prepareStatement(
                    "INSERT INTO account_group_by_id"
                        + " (group_id,"
                        + " include_uuid) VALUES ("
                        + groupId.get()
                        + ", ?)");
        PreparedStatement addSubGroupAuditStmt =
            jdbcSchema
                .getConnection()
                .prepareStatement(
                    "INSERT INTO account_group_by_id_aud"
                        + " (group_id,"
                        + " include_uuid,"
                        + " added_by,"
                        + " added_on) VALUES ("
                        + groupId.get()
                        + ", ?, "
                        + currentUser.getAccountId().get()
                        + ", ?)")) {
      Timestamp addedOn = TimeUtil.nowTs();
      for (AccountGroup.UUID subgroupUuid : subgroupUuids) {
        addSubGroupStmt.setString(1, subgroupUuid.get());
        addSubGroupStmt.addBatch();

        addSubGroupAuditStmt.setString(1, subgroupUuid.get());
        addSubGroupAuditStmt.setTimestamp(2, addedOn);
        addSubGroupAuditStmt.addBatch();
      }
      addSubGroupStmt.executeBatch();
      addSubGroupAuditStmt.executeBatch();
    }
  }

  private AccountInfo createAccount(String name) throws RestApiException {
    AccountInput accountInput = new AccountInput();
    accountInput.username = name;
    accountInput.name = name;
    return gApi.accounts().create(accountInput).get();
  }

  private GroupBundle readGroupBundleFromNoteDb(AccountGroup.UUID groupUuid) throws Exception {
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName)) {
      return groupBundleFactory.fromNoteDb(allUsersRepo, groupUuid);
    }
  }

  private void executeSchemaMigration(SchemaVersion schema, AccountGroup... groupsToVerify)
      throws Exception {
    executeSchemaMigration(
        schema,
        Arrays.stream(groupsToVerify)
            .map(AccountGroup::getGroupUUID)
            .toArray(AccountGroup.UUID[]::new));
  }

  private void executeSchemaMigration(SchemaVersion schema, GroupInfo... groupsToVerify)
      throws Exception {
    executeSchemaMigration(
        schema,
        Arrays.stream(groupsToVerify)
            .map(i -> new AccountGroup.UUID(i.id))
            .toArray(AccountGroup.UUID[]::new));
  }

  private void executeSchemaMigration(SchemaVersion schema, AccountGroup.UUID... groupsToVerify)
      throws Exception {
    List<GroupBundle> reviewDbBundles = new ArrayList<>();
    for (AccountGroup.UUID groupUuid : groupsToVerify) {
      reviewDbBundles.add(GroupBundle.Factory.fromReviewDb(db, groupUuid));
    }

    schema.migrateData(db, new TestUpdateUI());

    for (GroupBundle reviewDbBundle : reviewDbBundles) {
      assertMigratedCleanly(readGroupBundleFromNoteDb(reviewDbBundle.uuid()), reviewDbBundle);
    }
  }

  private void assertMigratedCleanly(GroupBundle noteDbBundle, GroupBundle expectedReviewDbBundle) {
    assertThat(GroupBundle.compareWithAudits(expectedReviewDbBundle, noteDbBundle)).isEmpty();
  }

  private ImmutableList<CommitInfo> log(AccountGroup group) throws Exception {
    ImmutableList.Builder<CommitInfo> result = ImmutableList.builder();
    List<Date> commitDates = new ArrayList<>();
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(allUsersRepo)) {
      Ref ref = allUsersRepo.exactRef(RefNames.refsGroups(group.getGroupUUID()));
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.setRetainBody(true);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        for (RevCommit c : rw) {
          result.add(CommitUtil.toCommitInfo(c));
          commitDates.add(c.getCommitterIdent().getWhen());
        }
      }
    }
    assertThat(commitDates).named("commit timestamps for %s", result).isOrdered();
    return result.build();
  }

  private ImmutableList<GroupReference> getAllGroupsFromNoteDb()
      throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName)) {
      return GroupNameNotes.loadAllGroups(allUsersRepo);
    }
  }

  private Optional<InternalGroup> getGroupFromNoteDb(AccountGroup.UUID groupUuid) throws Exception {
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName)) {
      return GroupConfig.loadForGroup(allUsersRepo, groupUuid).getLoadedGroup();
    }
  }

  private static OptionalSubject<InternalGroupSubject, InternalGroup> assertThatGroup(
      Optional<InternalGroup> group) {
    return assertThat(group, InternalGroupSubject::assertThat).named("group");
  }

  private void assertMemberAuditEvent(
      GroupAuditEventInfo info,
      Type expectedType,
      Account.Id expectedUser,
      AccountInfo expectedMember) {
    assertThat(info.user._accountId).isEqualTo(expectedUser.get());
    assertThat(info.type).isEqualTo(expectedType);
    assertThat(info).isInstanceOf(UserMemberAuditEventInfo.class);
    assertAccount(((UserMemberAuditEventInfo) info).member, expectedMember);
  }

  private void assertMemberAuditEvents(
      GroupAuditEventInfo info1,
      GroupAuditEventInfo info2,
      Type expectedType,
      Account.Id expectedUser,
      AccountInfo expectedMember1,
      AccountInfo expectedMember2) {
    assertThat(info1).isInstanceOf(UserMemberAuditEventInfo.class);
    assertThat(info2).isInstanceOf(UserMemberAuditEventInfo.class);

    UserMemberAuditEventInfo event1 = (UserMemberAuditEventInfo) info1;
    UserMemberAuditEventInfo event2 = (UserMemberAuditEventInfo) info2;

    assertThat(event1.member._accountId)
        .isAnyOf(expectedMember1._accountId, expectedMember2._accountId);
    assertThat(event2.member._accountId)
        .isAnyOf(expectedMember1._accountId, expectedMember2._accountId);
    assertThat(event1.member._accountId).isNotEqualTo(event2.member._accountId);

    if (event1.member._accountId == expectedMember1._accountId) {
      assertMemberAuditEvent(info1, expectedType, expectedUser, expectedMember1);
      assertMemberAuditEvent(info2, expectedType, expectedUser, expectedMember2);
    } else {
      assertMemberAuditEvent(info1, expectedType, expectedUser, expectedMember2);
      assertMemberAuditEvent(info2, expectedType, expectedUser, expectedMember1);
    }
  }

  private void assertSubgroupAuditEvent(
      GroupAuditEventInfo info,
      Type expectedType,
      Account.Id expectedUser,
      GroupInfo expectedSubGroup) {
    assertThat(info.user._accountId).isEqualTo(expectedUser.get());
    assertThat(info.type).isEqualTo(expectedType);
    assertThat(info).isInstanceOf(GroupMemberAuditEventInfo.class);
    assertGroup(((GroupMemberAuditEventInfo) info).member, expectedSubGroup);
  }

  private void assertSubgroupAuditEvents(
      GroupAuditEventInfo info1,
      GroupAuditEventInfo info2,
      Type expectedType,
      Account.Id expectedUser,
      GroupInfo expectedSubGroup1,
      GroupInfo expectedSubGroup2) {
    assertThat(info1).isInstanceOf(GroupMemberAuditEventInfo.class);
    assertThat(info2).isInstanceOf(GroupMemberAuditEventInfo.class);

    GroupMemberAuditEventInfo event1 = (GroupMemberAuditEventInfo) info1;
    GroupMemberAuditEventInfo event2 = (GroupMemberAuditEventInfo) info2;

    assertThat(event1.member.id).isAnyOf(expectedSubGroup1.id, expectedSubGroup2.id);
    assertThat(event2.member.id).isAnyOf(expectedSubGroup1.id, expectedSubGroup2.id);
    assertThat(event1.member.id).isNotEqualTo(event2.member.id);

    if (event1.member.id.equals(expectedSubGroup1.id)) {
      assertSubgroupAuditEvent(info1, expectedType, expectedUser, expectedSubGroup1);
      assertSubgroupAuditEvent(info2, expectedType, expectedUser, expectedSubGroup2);
    } else {
      assertSubgroupAuditEvent(info1, expectedType, expectedUser, expectedSubGroup2);
      assertSubgroupAuditEvent(info2, expectedType, expectedUser, expectedSubGroup1);
    }
  }

  private void assertAccount(AccountInfo actual, AccountInfo expected) {
    assertThat(actual._accountId).isEqualTo(expected._accountId);
    assertThat(actual.name).isEqualTo(expected.name);
    assertThat(actual.email).isEqualTo(expected.email);
    assertThat(actual.username).isEqualTo(expected.username);
  }

  private void assertGroup(GroupInfo actual, GroupInfo expected) {
    assertThat(actual.id).isEqualTo(expected.id);
    assertThat(actual.name).isEqualTo(expected.name);
    assertThat(actual.groupId).isEqualTo(expected.groupId);
  }

  private GroupInfo groupInfoForExternalGroup(AccountGroup.UUID groupUuid) {
    GroupInfo groupInfo = new GroupInfo();
    groupInfo.id = IdString.fromDecoded(groupUuid.get()).encoded();

    if (groupBackend.handles(groupUuid)) {
      groupInfo.name = groupBackend.get(groupUuid).getName();
    }

    return groupInfo;
  }

  private static AccountGroup toAccountGroup(GroupInfo info) {
    AccountGroup group =
        new AccountGroup(
            new AccountGroup.NameKey(info.name),
            new AccountGroup.Id(info.groupId),
            new AccountGroup.UUID(info.id),
            info.createdOn);
    group.setDescription(info.description);
    if (info.ownerId != null) {
      group.setOwnerGroupUUID(new AccountGroup.UUID(info.ownerId));
    }
    group.setVisibleToAll(
        info.options != null && info.options.visibleToAll != null && info.options.visibleToAll);
    return group;
  }

  private static GroupInfo toGroupInfo(AccountGroup group) {
    GroupInfo groupInfo = new GroupInfo();
    groupInfo.id = group.getGroupUUID().get();
    groupInfo.groupId = group.getId().get();
    groupInfo.name = group.getName();
    groupInfo.createdOn = group.getCreatedOn();
    groupInfo.description = group.getDescription();
    groupInfo.owner = group.getOwnerGroupUUID().get();
    groupInfo.options = new GroupOptionsInfo();
    groupInfo.options.visibleToAll = group.isVisibleToAll() ? true : null;
    return groupInfo;
  }

  private static class TestGroupBackend implements GroupBackend {
    static final String PREFIX = "testbackend:";

    static AccountGroup.UUID createUuuid(String name) {
      return new AccountGroup.UUID(PREFIX + name);
    }

    @Override
    public Collection<GroupReference> suggest(String name, ProjectState project) {
      return ImmutableSet.of();
    }

    @Override
    public GroupMembership membershipsOf(IdentifiedUser user) {
      return new GroupMembership() {
        @Override
        public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds) {
          return ImmutableSet.of();
        }

        @Override
        public Set<AccountGroup.UUID> getKnownGroups() {
          return ImmutableSet.of();
        }

        @Override
        public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds) {
          return false;
        }

        @Override
        public boolean contains(AccountGroup.UUID groupId) {
          return false;
        }
      };
    }

    @Override
    public boolean isVisibleToAll(AccountGroup.UUID uuid) {
      return false;
    }

    @Override
    public boolean handles(AccountGroup.UUID uuid) {
      return uuid.get().startsWith(PREFIX);
    }

    @Override
    public Basic get(AccountGroup.UUID uuid) {
      return new GroupDescription.Basic() {
        @Override
        public AccountGroup.UUID getGroupUUID() {
          return uuid;
        }

        @Override
        public String getName() {
          return uuid.get().substring(PREFIX.length());
        }

        @Override
        public String getEmailAddress() {
          return null;
        }

        @Override
        public String getUrl() {
          return null;
        }
      };
    }
  }
}
