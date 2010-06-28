package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_36 extends SchemaVersion {
  @Inject
  Schema_36(Provider<Schema_35> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("DROP INDEX account_project_watches_ntNew");
      stmt.execute("DROP INDEX account_project_watches_ntCmt");
      stmt.execute("DROP INDEX account_project_watches_ntSub");
      stmt.execute("CREATE INDEX account_project_watches_byProject"
          + " ON account_project_watches (project_name)");
    } finally {
      stmt.close();
    }
  }
}
