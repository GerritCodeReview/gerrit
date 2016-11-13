// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Schema_105 extends SchemaVersion {
  private static final String TABLE = "changes";

  @Inject
  Schema_105(Provider<Schema_104> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException, OrmException {
    JdbcSchema schema = (JdbcSchema) db;
    SqlDialect dialect = schema.getDialect();

    Map<String, OrmException> errors = new HashMap<>();
    try (StatementExecutor e = newExecutor(db)) {
      for (String index : listChangesIndexes(schema)) {
        ui.message("Dropping index " + index + " on table " + TABLE);
        try {
          dialect.dropIndex(e, TABLE, index);
        } catch (OrmException err) {
          errors.put(index, err);
        }
      }
    }

    for (String index : listChangesIndexes(schema)) {
      String msg = "Failed to drop index " + index;
      OrmException err = errors.get(index);
      if (err != null) {
        msg += ": " + err.getMessage();
      }
      ui.message(msg);
    }
  }

  private Set<String> listChangesIndexes(JdbcSchema schema) throws SQLException {
    // List of all changes indexes ever created or dropped, found with the
    // following command:
    //   find g* -name \*.sql | xargs git log -i -p -S' index changes_' | grep -io ' index changes_\w*' | cut -d' ' -f3 | tr A-Z a-z | sort -u
    // Used rather than listIndexes as we're not sure whether it might include
    // primary key indexes.
    Set<String> allChanges =
        ImmutableSet.of(
            "changes_allclosed",
            "changes_allopen",
            "changes_bybranchclosed",
            "changes_byownerclosed",
            "changes_byowneropen",
            "changes_byproject",
            "changes_byprojectopen",
            "changes_key",
            "changes_submitted");
    return Sets.intersection(
        schema.getDialect().listIndexes(schema.getConnection(), TABLE), allChanges);
  }
}
