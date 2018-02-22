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
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.GroupsConsistencyChecker;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.group.testing.InternalGroupSubject;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gerrit.truth.OptionalSubject;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

// TODO(aliceks): Add more tests.
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

  @Inject private Schema_167 schema167;
  @Inject private ReviewDb db;
  @Inject private GitRepositoryManager gitRepoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private GroupsConsistencyChecker consistencyChecker;
  @Inject private @ServerInitiated GroupsUpdate groupsUpdate;
  @Inject private Sequences seq;

  @Before
  public void unwrapDb() {
    // We must unwrap the ReviewDb to remove NoGroupsReviewDbWrapper,
    // otherwise we can't insert group data into ReviewDb.
    db = ReviewDbUtil.unwrapDb(db);
  }

  @Test
  public void reviewDbOnlyGroupsAreMigratedToNoteDb() throws Exception {
    // Create groups only in ReviewDb
    AccountGroup group1 = newGroup().setName("verifiers").build();
    AccountGroup group2 = newGroup().setName("contributors").build();
    storeInReviewDb(group1, group2);

    executeSchemaMigration(schema167);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).containsAllOf("verifiers", "contributors");
  }

  @Test
  public void alreadyExistingGroupsAreMigratedToNoteDb() throws Exception {
    // Create group in NoteDb and ReviewDb
    InternalGroup group1 =
        groupsUpdate.createGroup(
            db,
            InternalGroupCreation.builder()
                .setNameKey(new AccountGroup.NameKey("verifiers"))
                .setGroupUUID(new AccountGroup.UUID("verifiers"))
                .setId(new AccountGroup.Id(seq.nextGroupId()))
                .build(),
            InternalGroupUpdate.builder().setDescription("old").build());
    AccountGroup group1InReviewDb = getFromReviewDb(group1.getId());
    assertThat(group1InReviewDb).isNotNull();

    // Update group only in ReviewDb
    group1InReviewDb.setDescription("new");
    updateInReviewDb(group1InReviewDb);

    // Create a second group in NoteDb and ReviewDb
    InternalGroup group2 =
        groupsUpdate.createGroup(
            db,
            InternalGroupCreation.builder()
                .setNameKey(new AccountGroup.NameKey("contributors"))
                .setGroupUUID(new AccountGroup.UUID("contributors"))
                .setId(new AccountGroup.Id(seq.nextGroupId()))
                .build(),
            InternalGroupUpdate.builder().build());
    assertThat(getFromReviewDb(group2.getId())).isNotNull();

    executeSchemaMigration(schema167);

    // Verify that both groups are present in NoteDb
    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).containsAllOf("verifiers", "contributors");

    // Verify that group1 has the description from ReviewDb
    Optional<InternalGroup> group1InNoteDb = getGroupFromNoteDb(group1.getGroupUUID());
    assertThatGroup(group1InNoteDb).value().description().isEqualTo("new");
  }

  @Test
  public void adminGroupIsMigratedToNoteDb() throws Exception {
    // Administrators group is automatically created for all Gerrit servers.

    executeSchemaMigration(schema167);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).contains("Administrators");
  }

  @Test
  public void nonInteractiveUsersGroupIsMigratedToNoteDb() throws Exception {
    // 'Non-Interactive Users' group is automatically created for all Gerrit servers.

    executeSchemaMigration(schema167);

    ImmutableList<GroupReference> groups = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groups.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).contains("Non-Interactive Users");
  }

  @Test
  public void groupsAreConsistentAfterMigrationToNoteDb() throws Exception {
    AccountGroup group1 = newGroup().setName("verifiers").build();
    AccountGroup group2 = newGroup().setName("contributors").build();
    storeInReviewDb(group1, group2);

    executeSchemaMigration(schema167);

    List<ConsistencyCheckInfo.ConsistencyProblemInfo> consistencyProblems =
        consistencyChecker.check();
    assertThat(consistencyProblems).isEmpty();
  }

  @Test
  public void nameIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setName("verifiers").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().name().isEqualTo("verifiers");
  }

  @Test
  public void emptyNameIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setName("").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().name().isEqualTo("");
  }

  @Test
  public void uuidIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("ABCDEF");
    AccountGroup group = newGroup().setGroupUuid(groupUuid).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(groupUuid);
    assertThatGroup(groupInNoteDb).value().groupUuid().isEqualTo(groupUuid);
  }

  @Test
  public void idIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.Id id = new AccountGroup.Id(12345);
    AccountGroup group = newGroup().setId(id).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

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

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().createdOn().isEqualTo(createdOn);
  }

  @Test
  public void ownerUuidIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup.UUID ownerGroupUuid = new AccountGroup.UUID("UVWXYZ");
    AccountGroup group = newGroup().setOwnerGroupUuid(ownerGroupUuid).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().ownerGroupUuid().isEqualTo(ownerGroupUuid);
  }

  @Test
  public void descriptionIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setDescription("A test group").build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().description().isEqualTo("A test group");
  }

  @Test
  public void absentDescriptionIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().description().isNull();
  }

  @Test
  public void visibleToAllIsKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().setVisibleToAll(true).build();
    storeInReviewDb(group);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().visibleToAll().isTrue();
  }

  @Test
  public void membersAreKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);
    Account.Id member1 = new Account.Id(23456);
    Account.Id member2 = new Account.Id(93483);
    addMembersInReviewDb(group, member1, member2);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().members().containsExactly(member1, member2);
  }

  @Test
  public void subgroupsAreKeptDuringMigrationToNoteDb() throws Exception {
    AccountGroup group = newGroup().build();
    storeInReviewDb(group);
    AccountGroup.UUID subgroup1 = new AccountGroup.UUID("FGHIKL");
    AccountGroup.UUID subgroup2 = new AccountGroup.UUID("MNOPQR");
    addSubgroupsInReviewDb(group, subgroup1, subgroup2);

    executeSchemaMigration(schema167);

    Optional<InternalGroup> groupInNoteDb = getGroupFromNoteDb(group.getGroupUUID());
    assertThatGroup(groupInNoteDb).value().subgroups().containsExactly(subgroup1, subgroup2);
  }

  private static TestGroup.Builder newGroup() {
    return TestGroup.builder();
  }

  private void storeInReviewDb(AccountGroup... groups) throws Exception {
    db.accountGroups().insert(ImmutableList.copyOf(groups));
  }

  private void updateInReviewDb(AccountGroup... groups) throws Exception {
    db.accountGroups().update(ImmutableList.copyOf(groups));
  }

  private AccountGroup getFromReviewDb(AccountGroup.Id groupId) throws Exception {
    return db.accountGroups().get(groupId);
  }

  private void addMembersInReviewDb(AccountGroup group, Account.Id... memberIds) throws Exception {
    ImmutableList<AccountGroupMember> groupMembers =
        Arrays.stream(memberIds)
            .map(
                memberId ->
                    new AccountGroupMember(new AccountGroupMember.Key(memberId, group.getId())))
            .collect(toImmutableList());
    db.accountGroupMembers().insert(groupMembers);
  }

  private void addSubgroupsInReviewDb(AccountGroup group, AccountGroup.UUID... subgroupUuids)
      throws Exception {
    ImmutableList<AccountGroupById> subgroups =
        Arrays.stream(subgroupUuids)
            .map(
                subgroupId ->
                    new AccountGroupById(new AccountGroupById.Key(group.getId(), subgroupId)))
            .collect(toImmutableList());
    db.accountGroupById().insert(subgroups);
  }

  private void executeSchemaMigration(SchemaVersion schema) throws Exception {
    schema.migrateData(db, new TestUpdateUI());
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
}
