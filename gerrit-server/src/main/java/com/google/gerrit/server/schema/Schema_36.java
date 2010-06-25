//Copyright (C) 2010 The Android Open Source Project
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

public class Schema_36 extends SchemaVersion {
  @Inject
  Schema_36(Provider<Schema_35> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException, OrmException {
    Transaction transaction = null;
    Statement stmt = null;
    try {
      transaction = db.beginTransaction();

      Connection connection = ((JdbcSchema) db).getConnection();
      final boolean projectIdColumnExists = checkColumn(connection);

      stmt = connection.createStatement();

      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        if (!projectIdColumnExists) {
          stmt.execute("ALTER TABLE projects ADD COLUMN project_id INTEGER");
        }
        stmt.execute("UPDATE projects SET project_id = nextval('project_id')");
      } else if (dialect instanceof DialectH2) {
        if (!projectIdColumnExists) {
          stmt.execute("ALTER TABLE projects ADD COLUMN project_id INTEGER");
        }
        stmt.execute("UPDATE projects SET project_id = nextval('project_id')");
      } else if (dialect instanceof DialectMySQL) {
        if (!projectIdColumnExists){
          stmt.execute("ALTER TABLE projects ADD project_id INTEGER");
        }

        stmt.execute("CREATE FUNCTION nextval_project_id () RETURNS BIGINT"
            + " LANGUAGE SQL NOT DETERMINISTIC MODIFIES SQL DATA"
            + " BEGIN INSERT INTO project_id (s) VALUES (NULL);"
            + " DELETE FROM project_id WHERE s = LAST_INSERT_ID();"
            + " RETURN LAST_INSERT_ID(); END");

        stmt.execute("UPDATE projects SET project_id = nextval_project_id()");
      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }

      if (transaction != null) {
        transaction.commit();
      }
    }
    catch (OrmException ormex) {
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
      if (stmt != null) {
        stmt.close();
      }
    }
  }

  /**
   * Verifies if the table "projects" contains the column "project_id"
   * @param connection an open Connection
   * @return true if the  column "project_id" exists on  table "projects", false otherwise
   * @throws SQLException
   */
  private boolean checkColumn(Connection connection) throws SQLException{
    Statement statement = connection.createStatement();

    ResultSet results = statement.executeQuery("SELECT * FROM projects");
    try  {
      int index = results.findColumn("project_id");
      return index >= 0;

    } catch (Exception e) {
      // An error on find column means that column "project_id" does not exists
      return false;
    } finally {
      results.close();
    }
  }
}
