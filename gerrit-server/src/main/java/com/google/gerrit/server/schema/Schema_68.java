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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_68 extends SchemaVersion {
  @Inject
  Schema_68(Provider<Schema_67> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(final ReviewDb db, final UpdateUI ui)
      throws SQLException {
    final Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("CREATE INDEX submodule_subscription_access_bySubscription"
          + " ON submodule_subscriptions (submodule_project_name, submodule_branch_name)");
    } catch (SQLException e) {
      // the index creation might have failed because the index exists already,
      // in this case the exception can be safely ignored,
      // but there are also other possible reasons for an exception here that
      // should not be ignored,
      // -> ask the user whether to ignore this exception or not
      ui.message("warning: Cannot create index for submodule subscriptions");
      ui.message(e.getMessage());

      if (ui.isBatch()) {
        ui.message("you may ignore this warning when running in interactive mode");
        throw e;
      } else {
        final boolean answer = ui.yesno(false, "Ignore warning and proceed with schema upgrade");
        if (!answer) {
          throw e;
        }
      }
    } finally {
      stmt.close();
    }
  }
}
