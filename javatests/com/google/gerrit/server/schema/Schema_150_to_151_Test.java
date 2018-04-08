// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_150_to_151_Test {

  @Rule public InMemoryTestEnvironment testEnv = new InMemoryTestEnvironment();

  @Inject private Schema_151 schema151;
  @Inject private ReviewDb db;
  @Inject private IdentifiedUser currentUser;
  @Inject private @GerritPersonIdent Provider<PersonIdent> serverIdent;
  @Inject private Sequences seq;

  private Connection connection;
  private PreparedStatement createdOnRetrieval;
  private PreparedStatement createdOnUpdate;
  private PreparedStatement auditEntryDeletion;
  private JdbcSchema jdbcSchema;

  @Before
  public void unwrapDb() {
    jdbcSchema = ReviewDbWrapper.unwrapJbdcSchema(db);
  }

  @Before
  public void setUp() throws Exception {
    assume().that(db instanceof JdbcSchema).isTrue();

    connection = ((JdbcSchema) db).getConnection();

    try (Statement stmt = connection.createStatement()) {
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
    }

    createdOnRetrieval =
        connection.prepareStatement("SELECT created_on FROM account_groups WHERE group_id = ?");
    createdOnUpdate =
        connection.prepareStatement("UPDATE account_groups SET created_on = ? WHERE group_id = ?");
    auditEntryDeletion =
        connection.prepareStatement("DELETE FROM account_group_members_audit WHERE group_id = ?");
  }

  @After
  public void tearDown() throws Exception {
    if (auditEntryDeletion != null) {
      auditEntryDeletion.close();
    }
    if (createdOnUpdate != null) {
      createdOnUpdate.close();
    }
    if (createdOnRetrieval != null) {
      createdOnRetrieval.close();
    }
    if (connection != null) {
      connection.close();
    }
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedAfterAudit() throws Exception {
    Timestamp testStartTime = TimeUtil.nowTs();
    AccountGroup.Id groupId = createGroupInReviewDb("Group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    Timestamp createdOn = getCreatedOn(groupId);
    assertThat(createdOn).isAtLeast(testStartTime);
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedBeforeAudit() throws Exception {
    AccountGroup.Id groupId = createGroupInReviewDb("Ancient group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);
    removeAuditEntriesFor(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    Timestamp createdOn = getCreatedOn(groupId);
    assertThat(createdOn).isEqualTo(AccountGroup.auditCreationInstantTs());
  }

  private AccountGroup.Id createGroupInReviewDb(String name) throws Exception {
    AccountGroup group =
        new AccountGroup(
            new AccountGroup.NameKey(name),
            new AccountGroup.Id(seq.nextGroupId()),
            GroupUUID.make(name, serverIdent.get()),
            TimeUtil.nowTs());
    storeInReviewDb(group);
    addMembersInReviewDb(group.getId(), currentUser.getAccountId());
    return group.getId();
  }

  private Timestamp getCreatedOn(Id groupId) throws Exception {
    createdOnRetrieval.setInt(1, groupId.get());
    try (ResultSet results = createdOnRetrieval.executeQuery()) {
      if (results.first()) {
        return results.getTimestamp(1);
      }
    }
    return null;
  }

  private void setCreatedOnToVeryOldTimestamp(Id groupId) throws Exception {
    createdOnUpdate.setInt(1, groupId.get());
    Instant instant = LocalDateTime.of(1800, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC);
    createdOnUpdate.setTimestamp(1, Timestamp.from(instant));
    createdOnUpdate.setInt(2, groupId.get());
    createdOnUpdate.executeUpdate();
  }

  private void removeAuditEntriesFor(AccountGroup.Id groupId) throws Exception {
    auditEntryDeletion.setInt(1, groupId.get());
    auditEntryDeletion.executeUpdate();
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
}
