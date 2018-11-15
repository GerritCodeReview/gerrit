// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Schema_87 extends SchemaVersion {
  @Inject
  Schema_87(Provider<Schema_86> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (PreparedStatement uuidRetrieval =
            prepareStatement(db, "SELECT group_uuid FROM account_groups WHERE group_id = ?");
        PreparedStatement groupDeletion =
            prepareStatement(db, "DELETE FROM account_groups WHERE group_id = ?");
        PreparedStatement groupNameDeletion =
            prepareStatement(db, "DELETE FROM account_group_names WHERE group_id = ?")) {
      for (AccountGroup.Id id : scanSystemGroups(db)) {
        Optional<AccountGroup.UUID> groupUuid = getUuid(uuidRetrieval, id);
        if (groupUuid.filter(SystemGroupBackend::isSystemGroup).isPresent()) {
          groupDeletion.setInt(1, id.get());
          groupDeletion.executeUpdate();

          groupNameDeletion.setInt(1, id.get());
          groupNameDeletion.executeUpdate();
        }
      }
    }
  }

  private static Optional<AccountGroup.UUID> getUuid(
      PreparedStatement uuidRetrieval, AccountGroup.Id id) throws SQLException {
    uuidRetrieval.setInt(1, id.get());
    try (ResultSet uuidResults = uuidRetrieval.executeQuery()) {
      if (uuidResults.next()) {
        Optional.of(new AccountGroup.UUID(uuidResults.getString(1)));
      }
    }
    return Optional.empty();
  }

  private static Set<AccountGroup.Id> scanSystemGroups(ReviewDb db) throws SQLException {
    try (Statement stmt = newStatement(db);
        ResultSet rs =
            stmt.executeQuery("SELECT group_id FROM account_groups WHERE group_type = 'SYSTEM'")) {
      Set<AccountGroup.Id> ids = new HashSet<>();
      while (rs.next()) {
        ids.add(new AccountGroup.Id(rs.getInt(1)));
      }
      return ids;
    }
  }
}
