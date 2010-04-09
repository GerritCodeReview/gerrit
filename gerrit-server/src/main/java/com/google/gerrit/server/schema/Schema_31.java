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
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Schema_31 extends SchemaVersion {

  @Inject
  Schema_31(Provider<Schema_30> prior) {
    super(prior);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * com.google.gerrit.server.schema.SchemaVersion#migrateData(com.google.gerrit
   * .reviewdb.ReviewDb)
   */
  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {

    // Adds a new FK 'parent_name' on table 'projects'. This change is
    // made in order to support project rights inheritance.
    Transaction transaction = null;
    Statement statement = null;
    try {
      transaction = db.beginTransaction();

      Connection connection = ((JdbcSchema) db).getConnection();
      final boolean parentColumnExists = checkProjectsTable(connection);

      final SystemConfig cfg = db.systemConfig().get(new SystemConfig.Key());
      final String wildProjectName = cfg.wildProjectName.get();

      statement = connection.createStatement();

      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        if (!parentColumnExists) {
          statement.execute("ALTER TABLE projects ADD COLUMN parent_name VARCHAR(255)");
        }
        statement.execute(
            String.format("ALTER TABLE projects ALTER COLUMN parent_name SET DEFAULT '%s'", wildProjectName));
        statement.execute(String.format("UPDATE projects SET parent_name = '%s'" +
                "WHERE parent_name IS NULL OR parent_name = ''", wildProjectName));

      } else if (dialect instanceof DialectH2) {
        if (!parentColumnExists) {
          statement.execute("ALTER TABLE projects ADD COLUMN parent_name VARCHAR(255)");
        }
        statement.execute(
            String.format("ALTER TABLE projects ALTER COLUMN parent_name SET DEFAULT '%s'", wildProjectName));
        statement.execute(String.format("UPDATE projects SET parent_name = '%s'" +
            "WHERE parent_name IS NULL OR parent_name = ''", wildProjectName));

      } else if (dialect instanceof DialectMySQL) {
        if (!parentColumnExists){
          statement.execute("ALTER TABLE projects ADD project_name VARCHAR(255)");
        }
        statement.execute(String.format("ALTER TABLE projects ALTER parent_name SET DEFAULT '%s'", wildProjectName));
        statement.execute(String.format("UPDATE projects SET parent_name = '%s'" +
            "WHERE parent_name IS NULL OR parent_name = ''", wildProjectName));

      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }

      if (transaction != null) {
        transaction.commit();
      }

    } catch (OrmException ormex) {
      if (transaction != null) {
        transaction.rollback();
      }
      throw ormex;

    } catch (SQLException sqlex) {
      if (transaction != null) {
        transaction.rollback();
      }
      throw sqlex;

    } finally {
      if (statement != null) {
        statement.close();
      }
    }

  }


  /**
   * Verifies if the table "projects" contains the column "parent_name"
   * @param connection an open Connection
   * @return true if the  column "parent_name" exists on  table "projects", false otherwise
   * @throws SQLException
   */
  private boolean checkProjectsTable(Connection connection) throws SQLException{
    Statement statement = connection.createStatement();
    ResultSet results = statement.executeQuery("SELECT * FROM projects");
    try  {
      int index = results.findColumn("parent_name");
      return index >= 0;

    } catch (Exception e) {
      // An error on find column means that column "parent_name" does not exists
      return false;

    } finally {
      results.close();
    }
  }
}
