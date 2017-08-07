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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.testutil.SchemaUpgradeTestRule;
import com.google.gerrit.testutil.TestUpdateUI;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.Rule;
import org.junit.Test;

public class Schema_150_to_151_Test {

  @Rule public SchemaUpgradeTestRule testRule = new SchemaUpgradeTestRule();

  @Test
  public void createdOnIsPopulatedForGroupsCreatedAfterAudit() throws Exception {
    Timestamp testStartTime = TimeUtil.nowTs();
    AccountGroup.Id groupId = createGroup("Group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);

    testRule.schema151.migrateData(testRule.db, new TestUpdateUI());

    AccountGroup group = testRule.db.accountGroups().get(groupId);
    assertThat(group.getCreatedOn()).isAtLeast(testStartTime);
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedBeforeAudit() throws Exception {
    AccountGroup.Id groupId = createGroup("Ancient group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);
    removeAuditEntriesFor(groupId);

    testRule.schema151.migrateData(testRule.db, new TestUpdateUI());

    AccountGroup group = testRule.db.accountGroups().get(groupId);
    assertThat(group.getCreatedOn()).isEqualTo(AccountGroup.auditCreationInstantTs());
  }

  private AccountGroup.Id createGroup(String name) throws Exception {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name;
    GroupInfo groupInfo =
        testRule.createGroupFactory.create(name).apply(TopLevelResource.INSTANCE, groupInput);
    return new Id(groupInfo.groupId);
  }

  private void setCreatedOnToVeryOldTimestamp(Id groupId) throws OrmException {
    AccountGroup group = testRule.db.accountGroups().get(groupId);
    Instant instant = LocalDateTime.of(1800, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC);
    group.setCreatedOn(Timestamp.from(instant));
    testRule.db.accountGroups().update(ImmutableList.of(group));
  }

  private void removeAuditEntriesFor(AccountGroup.Id groupId) throws Exception {
    ResultSet<AccountGroupMemberAudit> groupMemberAudits =
        testRule.db.accountGroupMembersAudit().byGroup(groupId);
    testRule.db.accountGroupMembersAudit().delete(groupMemberAudits);
  }
}
