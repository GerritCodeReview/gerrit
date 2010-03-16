// Copyright (C) 2009 The Android Open Source Project
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;

class Schema_23 extends SchemaVersion {
  @Inject
  Schema_23(Provider<Schema_22> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    Collection<AccountGroupName> names = new ArrayList<AccountGroupName>();
    Statement queryStmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet results =
          queryStmt.executeQuery("SELECT group_id, name FROM account_groups");
      while (results.next()) {
        final int id = results.getInt(1);
        final String name = results.getString(2);

        final AccountGroup.Id group = new AccountGroup.Id(id);
        final AccountGroup.NameKey key = toKey(name);
        names.add(new AccountGroupName(key, group));
      }
    } finally {
      queryStmt.close();
    }
    db.accountGroupNames().insert(names);
  }

  private AccountGroup.NameKey toKey(final String name) {
    return new AccountGroup.NameKey(name);
  }
}
