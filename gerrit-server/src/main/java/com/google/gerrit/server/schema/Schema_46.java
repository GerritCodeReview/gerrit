// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

public class Schema_46 extends SchemaVersion {

  @Inject
  Schema_46(final Provider<Schema_45> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException,
      OrmException {
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());

    // update system_config
    final Connection connection = ((JdbcSchema) db).getConnection();
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.execute("UPDATE system_config SET OWNER_GROUP_ID = " + groupId.get());
      final ResultSet resultSet =
          stmt.executeQuery("SELECT ADMIN_GROUP_ID FROM system_config");
      resultSet.next();
      final int adminGroupId = resultSet.getInt(1);

      // create 'Project Owners' group
      AccountGroup.NameKey nameKey = new AccountGroup.NameKey("Project Owners");
      AccountGroup group = new AccountGroup(nameKey, groupId);
      group.setType(AccountGroup.Type.SYSTEM);
      group.setOwnerGroupId(new AccountGroup.Id(adminGroupId));
      group.setDescription("Any owner of the project");
      AccountGroupName gn = new AccountGroupName(group);
      db.accountGroupNames().insert(Collections.singleton(gn));
      db.accountGroups().insert(Collections.singleton(group));
    } finally {
      if (stmt != null) stmt.close();
    }
  }
}
