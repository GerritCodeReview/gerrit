package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_48  extends SchemaVersion {

  @Inject
  Schema_48(Provider<Schema_47> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("CREATE INDEX projects_byParent"
          + " ON projects (parent_name)");
    } finally {
      stmt.close();
    }
  }
}
