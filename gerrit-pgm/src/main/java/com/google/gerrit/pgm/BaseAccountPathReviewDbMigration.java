package com.google.gerrit.pgm;

import com.google.auto.value.AutoValue;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public abstract class BaseAccountPathReviewDbMigration extends SiteProgram {

  @Option(name = "--chunkSize", usage = "number of rows to process in one transaction")
  protected long chunkSize = 100000;

  protected Injector dbInjector;
  protected SitePaths sitePaths;
  protected ThreadSettingsConfig threadSettingsConfig;
  protected Config cfg;

  @Override
  public final int run() throws Exception {
    dbInjector = createDbInjector(DataSourceProvider.Context.SINGLE_USER);
    sitePaths = new SitePaths(getSitePath());
    threadSettingsConfig = dbInjector.getInstance(ThreadSettingsConfig.class);
    cfg = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));

    return migrate();
  }

  protected abstract int migrate() throws Exception;

  @AutoValue
  protected abstract static class Row {
    abstract int accountId();

    abstract int changeId();

    abstract int patchSetId();

    abstract String fileName();
  }

  protected List<Row> selectRows(PreparedStatement stmt, long offset) throws SQLException {
    List<Row> results = new ArrayList<>();
    stmt.setLong(1, chunkSize);
    stmt.setLong(2, offset);
    try (ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        results.add(
            new AutoValue_BaseAccountPathReviewDbMigration_Row(
                rs.getInt("account_id"),
                rs.getInt("change_id"),
                rs.getInt("patch_set_id"),
                rs.getString("file_name")));
      }
    }
    return results;
  }
}
