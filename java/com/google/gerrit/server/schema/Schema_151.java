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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A schema which adds the 'created on' field to groups. */
public class Schema_151 extends SchemaVersion {
  @Inject
  protected Schema_151(Provider<Schema_150> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (PreparedStatement groupUpdate =
            prepareStatement(db, "UPDATE account_groups SET created_on = ? WHERE group_id = ?");
        PreparedStatement addedOnRetrieval =
            prepareStatement(
                db,
                "SELECT added_on FROM account_group_members_audit WHERE group_id = ?"
                    + " ORDER BY added_on ASC")) {
      List<AccountGroup.Id> accountGroups = getAllGroupIds(db);
      for (AccountGroup.Id groupId : accountGroups) {
        Optional<Timestamp> firstTimeMentioned = getFirstTimeMentioned(addedOnRetrieval, groupId);
        Timestamp createdOn = firstTimeMentioned.orElseGet(AccountGroup::auditCreationInstantTs);

        groupUpdate.setTimestamp(1, createdOn);
        groupUpdate.setInt(2, groupId.get());
        groupUpdate.executeUpdate();
      }
    }
  }

  private static Optional<Timestamp> getFirstTimeMentioned(
      PreparedStatement addedOnRetrieval, AccountGroup.Id groupId) throws SQLException {
    addedOnRetrieval.setInt(1, groupId.get());
    try (ResultSet resultSet = addedOnRetrieval.executeQuery()) {
      if (resultSet.next()) {
        return Optional.of(resultSet.getTimestamp(1));
      }
    }
    return Optional.empty();
  }

  private static List<AccountGroup.Id> getAllGroupIds(ReviewDb db) throws SQLException {
    try (Statement stmt = newStatement(db);
        ResultSet rs = stmt.executeQuery("SELECT group_id FROM account_groups")) {
      List<AccountGroup.Id> groupIds = new ArrayList<>();
      while (rs.next()) {
        groupIds.add(new AccountGroup.Id(rs.getInt(1)));
      }
      return groupIds;
    }
  }
}
