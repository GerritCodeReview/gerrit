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
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Schema_27 extends SchemaVersion {
  @Inject
  Schema_27(Provider<Schema_26> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException, OrmException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("ALTER TABLE account_groups"
            + " ALTER COLUMN name TYPE VARCHAR(255)");
        stmt.execute("ALTER TABLE account_group_names"
            + " ALTER COLUMN name TYPE VARCHAR(255)");

      } else if (dialect instanceof DialectH2) {
        stmt.execute("ALTER TABLE account_groups"
            + " ALTER COLUMN name VARCHAR(255)");
        stmt.execute("ALTER TABLE account_group_names"
            + " ALTER COLUMN name VARCHAR(255) NOT NULL");

      } else if (dialect instanceof DialectMySQL) {
        stmt.execute("ALTER TABLE account_groups MODIFY name VARCHAR(255)");
        stmt.execute("ALTER TABLE account_group_names"
            + " MODIFY name VARCHAR(255)");

      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
    }

    // Some groups might be missing their names, our older schema
    // creation logic failed to create the name objects. Do it now.
    //
    Map<AccountGroup.NameKey, AccountGroupName> names =
        db.accountGroupNames().toMap(db.accountGroupNames().all());

    List<AccountGroupName> insert = new ArrayList<AccountGroupName>();
    List<AccountGroupName> update = new ArrayList<AccountGroupName>();

    for (AccountGroup g : db.accountGroups().all()) {
      AccountGroupName n = names.get(g.getNameKey());
      if (n == null) {
        insert.add(new AccountGroupName(g));

      } else if (!g.getId().equals(n.getId())) {
        n.setId(g.getId());
        update.add(n);
      }
    }

    db.accountGroupNames().insert(insert);
    db.accountGroupNames().update(update);
  }
}
