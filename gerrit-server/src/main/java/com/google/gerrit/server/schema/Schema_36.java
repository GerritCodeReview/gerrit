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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

public class Schema_36 extends SchemaVersion {

  @Inject
  Schema_36(Provider<Schema_35> prior) {
    super(prior);
  }

  /**
   * Migrate the account.default_context column to account_diff_preferences.context column.
   * <p>
   * Other fields in account_diff_preferences will be filled in with their defaults as
   * defined in the {@link AccountDiffPreference#createDefault(com.google.gerrit.reviewdb.Account.Id)}
   */
  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet result = stmt.executeQuery("SELECT account_id, default_context FROM accounts");
      while (result.next()) {
        int accountId = result.getInt(1);
        short defaultContext = result.getShort(2);
        AccountDiffPreference diffPref = AccountDiffPreference.createDefault(new Account.Id(accountId));
        diffPref.setContext(defaultContext);
        db.accountDiffPreferences().insert(Collections.singleton(diffPref));
      }
    } finally {
      stmt.close();
    }
  }
}
