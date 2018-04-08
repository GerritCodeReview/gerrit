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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritServerIdProvider;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_166_to_167_WithGroupsInNoteDbTest {
  private static Config createConfig() {
    Config config = new Config();
    config.setString(GerritServerIdProvider.SECTION, null, GerritServerIdProvider.KEY, "1234567");

    // Disable groups in ReviewDb. This means the primary storage for groups is NoteDb.
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, true);

    return config;
  }

  @Rule
  public InMemoryTestEnvironment testEnv =
      new InMemoryTestEnvironment(Schema_166_to_167_WithGroupsInNoteDbTest::createConfig);

  @Inject private Schema_167 schema167;
  @Inject private ReviewDb db;
  @Inject private GitRepositoryManager gitRepoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private @ServerInitiated GroupsUpdate groupsUpdate;
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
    }
  }

  @Test
  public void migrationIsSkipped() throws Exception {
    // Create a group in NoteDb (doesn't create the group in ReviewDb since
    // disableReviewDb == true)
    InternalGroup internalGroup =
        groupsUpdate.createGroup(
            InternalGroupCreation.builder()
                .setNameKey(new AccountGroup.NameKey("users"))
                .setGroupUUID(new AccountGroup.UUID("users"))
                .setId(new AccountGroup.Id(seq.nextGroupId()))
                .build(),
            InternalGroupUpdate.builder().setDescription("description").build());

    // Insert the group into ReviewDb
    AccountGroup group1 =
        newGroup()
            .setName(internalGroup.getName())
            .setGroupUuid(internalGroup.getGroupUUID())
            .setId(internalGroup.getId())
            .setCreatedOn(internalGroup.getCreatedOn())
            .setDescription(internalGroup.getDescription())
            .setGroupUuid(internalGroup.getGroupUUID())
            .setVisibleToAll(internalGroup.isVisibleToAll())
            .build();
    storeInReviewDb(group1);

    // Update the group description in ReviewDb so that the group state differs between ReviewDb and
    // NoteDb
    group1.setDescription("outdated");
    updateInReviewDb(group1);

    // Create a group that only exists in ReviewDb
    AccountGroup group2 = newGroup().setName("reviewDbOnlyGroup").build();
    storeInReviewDb(group2);

    // Remember the SHA1 of the group ref in NoteDb
    ObjectId groupSha1 = getGroupSha1(group1.getGroupUUID());

    executeSchemaMigration(schema167);

    // Verify the groups in NoteDb: "users" should still exist, "reviewDbOnlyGroup" should not have
    // been created
    ImmutableList<GroupReference> groupReferences = getAllGroupsFromNoteDb();
    ImmutableList<String> groupNames =
        groupReferences.stream().map(GroupReference::getName).collect(toImmutableList());
    assertThat(groupNames).contains("users");
    assertThat(groupNames).doesNotContain("reviewDbOnlyGroup");

    // Verify that the group refs in NoteDb were not touched.
    assertThat(getGroupSha1(group1.getGroupUUID())).isEqualTo(groupSha1);
    assertThat(getGroupSha1(group2.getGroupUUID())).isNull();
  }

  private static TestGroup.Builder newGroup() {
    return TestGroup.builder();
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

  private void executeSchemaMigration(SchemaVersion schema) throws Exception {
    schema.migrateData(db, new TestUpdateUI());
  }

  private ImmutableList<GroupReference> getAllGroupsFromNoteDb()
      throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName)) {
      return GroupNameNotes.loadAllGroups(allUsersRepo);
    }
  }

  @Nullable
  private ObjectId getGroupSha1(AccountGroup.UUID groupUuid) throws IOException {
    try (Repository allUsersRepo = gitRepoManager.openRepository(allUsersName)) {
      Ref ref = allUsersRepo.exactRef(RefNames.refsGroups(groupUuid));
      return ref != null ? ref.getObjectId() : null;
    }
  }
}
