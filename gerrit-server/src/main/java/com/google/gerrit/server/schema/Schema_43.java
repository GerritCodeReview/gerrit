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
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class Schema_43 extends SchemaVersion {

  private Map<String, Integer> projects = new HashMap<String, Integer>();

  @Inject
  Schema_43(Provider<Schema_42> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException,
      OrmException {
    migrateProjects(db);
    migrateChanges(db);
    migrateAccountProjectWatches(db);
    migrateRefRights(db);
    migrateSystemConfig(db);
    migrateIndexes(db);
  }

  private void migrateProjects(ReviewDb db) throws SQLException, OrmException {
    final Connection connection = ((JdbcSchema) db).getConnection();
    final Statement stmt = connection.createStatement();
    final PreparedStatement updateProjectStmt = connection.prepareStatement("UPDATE projects SET project_id = ? WHERE name = ?");
    final PreparedStatement insertProjectNameStmt = connection.prepareStatement("INSERT INTO project_names (name, project_id) VALUES (?, ?)");
    try {
      // 1. load all projects and generate a project-id for them,
      //    store the project ids in the projects Map for fast lookup
      // 2. for every (migrated) project create an entry in the project_names table
      final ResultSet result =
        stmt.executeQuery("SELECT name FROM projects ORDER BY name");
      while (result.next()) {
        final String projectName = result.getString(1);
        final int projectId = db.nextProjectId();

        projects.put(projectName, projectId);

        updateProjectStmt.setInt(1, projectId);
        updateProjectStmt.setString(2, projectName);
        updateProjectStmt.addBatch();

        insertProjectNameStmt.setString(1, projectName);
        insertProjectNameStmt.setInt(2, projectId);
        insertProjectNameStmt.addBatch();
      }
      updateProjectStmt.executeBatch();
      insertProjectNameStmt.executeBatch();

      // 3. set the new primary key
      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("ALTER TABLE projects "
            + "DROP CONSTRAINT projects_pkey");
        stmt.execute("ALTER TABLE projects ADD PRIMARY KEY (project_id)");

      } else if ((dialect instanceof DialectH2)
          || (dialect instanceof DialectMySQL)) {
        stmt.execute("ALTER TABLE projects DROP PRIMARY KEY");
        stmt.execute("ALTER TABLE projects ADD PRIMARY KEY (project_id)");

      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
      updateProjectStmt.close();
      insertProjectNameStmt.close();
    }
  }

  private void migrateChanges(ReviewDb db) throws SQLException, OrmException {
    final Connection connection = ((JdbcSchema) db).getConnection();
    final Statement stmt = connection.createStatement();
    final PreparedStatement updateChangeStmt = connection.prepareStatement("UPDATE changes SET dest_project_id = ? WHERE dest_project_name = ?");
    try {
      final ResultSet result =
        stmt.executeQuery("SELECT dest_project_name FROM changes GROUP BY dest_project_name");
      while (result.next()) {
        final String destProjectName = result.getString(1);
        updateChangeStmt.setInt(1, getProjectId(destProjectName));
        updateChangeStmt.setString(2, destProjectName);
        updateChangeStmt.addBatch();
      }
      updateChangeStmt.executeBatch();
    } finally {
      stmt.close();
      updateChangeStmt.close();
    }
  }

  private void migrateAccountProjectWatches(ReviewDb db) throws SQLException, OrmException {
    final Connection connection = ((JdbcSchema) db).getConnection();
    final Statement stmt = connection.createStatement();
    final PreparedStatement updateAccountProjectWatchStmt =
        connection.prepareStatement("UPDATE account_project_watches SET project_id = ? WHERE project_name = ?");
    try {
      ResultSet result =
        stmt.executeQuery("SELECT project_name FROM account_project_watches GROUP BY project_name");
      while (result.next()) {
        final String projectName = result.getString(1);
        updateAccountProjectWatchStmt.setInt(1, getProjectId(projectName));
        updateAccountProjectWatchStmt.setString(2, projectName);
        updateAccountProjectWatchStmt.addBatch();
      }
      updateAccountProjectWatchStmt.executeBatch();

      // set the new primary key
      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("ALTER TABLE account_project_watches "
            + "DROP CONSTRAINT account_project_watches_pkey");
        stmt.execute("ALTER TABLE account_project_watches "
            + "ADD PRIMARY KEY (account_id, project_id, filter)");

      } else if ((dialect instanceof DialectH2)
          || (dialect instanceof DialectMySQL)) {
        stmt.execute("ALTER TABLE account_project_watches DROP PRIMARY KEY");
        stmt.execute("ALTER TABLE account_project_watches "
            + "ADD PRIMARY KEY (account_id, project_id, filter)");

      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
      updateAccountProjectWatchStmt.close();
    }
  }

  private void migrateRefRights(ReviewDb db) throws SQLException, OrmException {
    final Connection connection = ((JdbcSchema) db).getConnection();
    final Statement stmt = connection.createStatement();
    final PreparedStatement updateRefRightStmt =
        connection.prepareStatement("UPDATE ref_rights SET project_id = ? WHERE project_name = ?");
    try {
      ResultSet result =
        stmt.executeQuery("SELECT project_name FROM ref_rights GROUP BY project_name");
      while (result.next()) {
        final String projectName = result.getString(1);
        updateRefRightStmt.setInt(1, getProjectId(projectName));
        updateRefRightStmt.setString(2, projectName);
        updateRefRightStmt.addBatch();
      }
      updateRefRightStmt.executeBatch();

      // set the new primary key
      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("ALTER TABLE ref_rights "
            + "DROP CONSTRAINT ref_rights_pkey");
        stmt.execute("ALTER TABLE ref_rights "
            + "ADD PRIMARY KEY (project_id, ref_pattern, category_id, group_id)");

      } else if ((dialect instanceof DialectH2)
          || (dialect instanceof DialectMySQL)) {
        stmt.execute("ALTER TABLE ref_rights DROP PRIMARY KEY");
        stmt.execute("ALTER TABLE ref_rights "
            + "ADD PRIMARY KEY (project_id, ref_pattern, category_id, group_id)");

      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
      updateRefRightStmt.close();
    }
  }

  private void migrateSystemConfig(ReviewDb db) throws SQLException, OrmException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet result =
        stmt.executeQuery("SELECT wild_project_name FROM system_config");
      if (result.next()) {
        final String wildProjectName = result.getString(1);

        stmt.execute("UPDATE system_config"
            + " SET wild_project_id = " + getProjectId(wildProjectName));
      }
    } finally {
      stmt.close();
    }
  }

  private void migrateIndexes(ReviewDb db) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      if (((JdbcSchema) db).getDialect() instanceof DialectMySQL) {
        stmt.execute("DROP INDEX account_project_watches_byProject ON account_project_watches");
        stmt.execute("DROP INDEX changes_submitted ON changes");
        stmt.execute("DROP INDEX changes_byProjectOpen ON changes");
        stmt.execute("DROP INDEX changes_byProject ON changes");
      } else {
        stmt.execute("DROP INDEX account_project_watches_byProject");
        stmt.execute("DROP INDEX changes_submitted");
        stmt.execute("DROP INDEX changes_byProjectOpen");
        stmt.execute("DROP INDEX changes_byProject");
      }
      stmt.execute("CREATE INDEX account_project_watches_byProject"
          + " ON account_project_watches (project_id)");
      stmt.execute("CREATE INDEX changes_byProject"
          + " ON changes (dest_project_id)");

      if (((JdbcSchema) db).getDialect() instanceof DialectPostgreSQL) {
        stmt.execute("CREATE INDEX changes_submitted"
                + " ON changes (dest_project_id, dest_branch_name, last_updated_on)"
                + " WHERE status = 's'");
        stmt.execute("CREATE INDEX changes_byProjectOpen"
            + " ON changes (dest_project_id, sort_key) WHERE open = 'Y'");
      } else {
        stmt.execute("CREATE INDEX changes_submitted"
                + " ON changes (status, dest_project_id, dest_branch_name, last_updated_on)");
        stmt.execute("CREATE INDEX changes_byProjectOpen"
            + " ON changes (open, dest_project_id, sort_key)");
      }
    } finally {
      stmt.close();
    }
  }

  private int getProjectId(String projectName) throws OrmException {
    if (!projects.containsKey(projectName)) {
      throw new OrmException("Inconsistent database: detected reference to non-existing project [" + projectName + "]");
    }
    return projects.get(projectName);
  }
}
