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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Schema_87 extends SchemaVersion {
  @Inject
  Schema_87(Provider<Schema_86> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    for (AccountGroup.Id id : scanSystemGroups(db)) {
      AccountGroup group = db.accountGroups().get(id);
      if (group != null && SystemGroupBackend.isSystemGroup(group.getGroupUUID())) {
        db.accountGroups().delete(Collections.singleton(group));
        db.accountGroupNames().deleteKeys(Collections.singleton(group.getNameKey()));
      }
    }
  }

  private Set<AccountGroup.Id> scanSystemGroups(ReviewDb db) throws SQLException {
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
