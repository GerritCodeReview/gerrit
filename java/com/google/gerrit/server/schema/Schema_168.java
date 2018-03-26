// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Drop group indexes. */
public class Schema_168 extends SchemaVersion {
  @Inject
  Schema_168(Provider<Schema_167> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException, OrmException {
    JdbcSchema schema = ReviewDbWrapper.unwrapJbdcSchema(db);
    SqlDialect dialect = schema.getDialect();

    Map<String, OrmException> errors = new HashMap<>();
    try (StatementExecutor executor = newExecutor(db)) {
      for (Map.Entry<String, String> e : listGroupIndexes(schema).entries()) {
        String table = e.getKey();
        String index = e.getValue();
        ui.message("Dropping index " + index + " on table " + table);
        try {
          dialect.dropIndex(executor, table, index);
        } catch (OrmException err) {
          errors.put(table + "/" + index, err);
        }
      }
    }

    for (Map.Entry<String, String> e : listGroupIndexes(schema).entries()) {
      String table = e.getKey();
      String index = e.getValue();
      String msg = "Failed to drop index " + index + " on table " + table;
      OrmException err = errors.get(table + "/" + index);
      if (err != null) {
        msg += ": " + err.getMessage();
      }
      ui.message(msg);
    }
  }

  private Multimap<String, String> listGroupIndexes(JdbcSchema schema) throws SQLException {
    // List of all group indexes ever created or dropped, found with the
    // following commands:
    //   find g* -name \*.sql
    //     | xargs git log -i -p -S' index account_group_'
    //     | grep -io ' index account_group_\w*'
    //     | cut -d' ' -f3 | tr A-Z a-z | sort -u
    //   find g* -name \*.sql
    //     | xargs git log -i -p -S' index acc_gr_'
    //     | grep -io ' index acc_gr_\w*'
    //     | cut -d' ' -f3 | tr A-Z a-z | sort -u
    // Used rather than listIndexes as we're not sure whether it might include
    // primary key indexes.
    Multimap<String, String> allGroupIndexesByTable =
        ImmutableMultimap.of(
            "account_group_by_id", "account_group_id_byinclude",
            "account_group_by_id", "account_group_includes_byinclude",
            "account_group_by_id", "acc_gr_incl_by_uuid_byinclude",
            "account_group_members", "account_group_members_bygroup");

    Multimap<String, String> allExistingGroupIndexesByTable =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (Map.Entry<String, Collection<String>> e : allGroupIndexesByTable.asMap().entrySet()) {
      String table = e.getKey();
      Set<String> indexes = new HashSet<>(e.getValue());
      Set<String> existingIndexes =
          Sets.intersection(
              schema.getDialect().listIndexes(schema.getConnection(), table), indexes);
      allExistingGroupIndexesByTable.putAll(table, existingIndexes);
    }
    return allExistingGroupIndexesByTable;
  }
}
