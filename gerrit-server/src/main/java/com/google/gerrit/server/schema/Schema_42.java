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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefMergeStrategy;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

class Schema_42 extends SchemaVersion {
  @Inject
  Schema_42(Provider<Schema_41> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException, OrmException {
    final SystemConfig cfg = db.systemConfig().get(new SystemConfig.Key());
    final String wildProjectName = cfg.wildProjectName.get();

    JdbcSchema jdbc = (JdbcSchema) db;
    Statement stmt = jdbc.getConnection().createStatement();

    try {
      //It certifies the projects submit action is preserved:

      //It selects all the rows of projects table,
      //except the one representing the wild project.
      final ResultSet rs = stmt.executeQuery(
          "SELECT * FROM projects WHERE name <> '" + wildProjectName + "'");

      //It identifies the ref merge strategies to insert in the database:
      //no information should be lost.

      final List<RefMergeStrategy> rmsToInsert = new ArrayList<RefMergeStrategy>();

      try {
        while (rs.next()) {
          //A ref merge strategy should be created for each project
          //considering the ref pattern "*".
          //It is stored in the submit_type column of projects table.

          RefMergeStrategy rms = new RefMergeStrategy(
              new RefMergeStrategy.Key(new Project.NameKey(rs.getString("name")),
                  new RefMergeStrategy.RefPattern("*")));

          rms.setSubmitType(RefMergeStrategy.SubmitType.forCode(
              rs.getString("submit_type").charAt(0)));

          rmsToInsert.add(rms);
        }
      } finally {
        rs.close();
      }

      if (rmsToInsert.size() > 0) {
        //It inserts in the database the ref merge strategies.
        db.refMergeStrategies().insert(rmsToInsert);
      }

      //It should now drop the column submit_type of projects table,
      //the info has been preserved in the ref_merge_strategies table.
      stmt.execute("ALTER TABLE projects DROP COLUMN submit_type");
    } finally {
      stmt.close();
    }
  }
}