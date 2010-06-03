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

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Schema_100 extends SchemaVersion {
  @Inject
  Schema_100(Provider<Schema_33> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {

    List<String> toCalc = new ArrayList<String>(1000);

    PreparedStatement selectStmt =
        ((JdbcSchema) db).getConnection().prepareStatement(
            "SELECT sort_key FROM changes WHERE sort_key_desc IS NULL"
                + " OR sort_key_desc='';");

    selectStmt.setMaxRows(1000);

    PreparedStatement updateStmt =
        ((JdbcSchema) db).getConnection().prepareStatement(
            "UPDATE changes SET sort_key_desc = ? WHERE sort_key = ?;");

    try {
      while (true) {
        ResultSet rs = selectStmt.executeQuery();

        try {
          while (rs.next()) {
            toCalc.add(rs.getString(1));
          }
        } finally {
          rs.close();
        }

        if (toCalc.isEmpty()) break;

        for (String s : toCalc) {
          String sortKeyDesc = Long.toHexString(-1l - Long.parseLong(s, 16));

          updateStmt.setString(1, sortKeyDesc);
          updateStmt.setString(2, s);

          updateStmt.addBatch();
        }
        updateStmt.executeBatch();

        toCalc.clear();

      }
    } finally {
      updateStmt.close();
      selectStmt.close();
    }
  }
}
