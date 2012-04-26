// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

public class Schema_66 extends SchemaVersion {

  @Inject
  Schema_66(Provider<Schema_65> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    final Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      final ResultSet rs = stmt.executeQuery(
          "SELECT account_id, display_patch_sets_in_reverse_order, " +
          "       display_person_name_in_review_category " +
          "FROM accounts");
      try {
        final List<Account> accountsToUpdate = new LinkedList<Account>();
        while (rs.next()) {
          final boolean reversePatchSetOrder = rs.getBoolean(2);
          final boolean showUsernameInReviewCategory = rs.getBoolean(3);
          if (reversePatchSetOrder || showUsernameInReviewCategory) {
            final Account.Id id = new Account.Id(rs.getInt(1));
            final Account account = db.accounts().get(id);
            final AccountGeneralPreferences prefs = account.getGeneralPreferences();
            prefs.setReversePatchSetOrder(reversePatchSetOrder);
            prefs.setShowUsernameInReviewCategory(showUsernameInReviewCategory);
            accountsToUpdate.add(account);
          }
        }

        if (!accountsToUpdate.isEmpty()) {
          db.accounts().update(accountsToUpdate);
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }
}
