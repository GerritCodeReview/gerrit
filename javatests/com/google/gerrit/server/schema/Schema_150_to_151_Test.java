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
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.testing.AbstractSchemaUpgradeTest;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Schema_150_to_151_Test extends AbstractSchemaUpgradeTest {
  @Inject private CreateGroup.Factory createGroupFactory;
  @Inject private Schema_151 schema151;

  private Connection connection;
  private PreparedStatement createdOnRetrieval;
  private PreparedStatement createdOnUpdate;
  private PreparedStatement auditEntryDeletion;

  @Before
  public void setUp() throws Exception {
    assume().that(db instanceof JdbcSchema).isTrue();

    connection = ((JdbcSchema) db).getConnection();
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
    AccountGroup.Id groupId = createGroup("Group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    Timestamp createdOn = getCreatedOn(groupId);
    assertThat(createdOn).isAtLeast(testStartTime);
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedBeforeAudit() throws Exception {
    AccountGroup.Id groupId = createGroup("Ancient group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);
    removeAuditEntriesFor(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    Timestamp createdOn = getCreatedOn(groupId);
    assertThat(createdOn).isEqualTo(AccountGroup.auditCreationInstantTs());
  }

  private AccountGroup.Id createGroup(String name) throws Exception {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name;
    GroupInfo groupInfo =
        createGroupFactory.create(name).apply(TopLevelResource.INSTANCE, groupInput);
    return new Id(groupInfo.groupId);
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
}
