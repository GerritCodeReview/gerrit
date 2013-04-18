package com.google.gerrit.server.schema;

import static com.google.gerrit.server.schema.JdbcUtil.hostname;

import com.google.gerrit.server.config.ConfigSection;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

public class MaxDb extends BaseDataSourceType {

  private Config cfg;

  @Inject
  public MaxDb(@GerritServerConfig final Config cfg) {
    super("com.sap.dbtech.jdbc.DriverSapDB");
    this.cfg = cfg;
  }

  @Override
  public String getUrl() {
    final StringBuilder b = new StringBuilder();
    final ConfigSection dbs = new ConfigSection(cfg, "database");
    b.append("jdbc:sapdb://");
    b.append(hostname(dbs.optional("hostname")));
    b.append("/");
    b.append(dbs.required("database"));
    return b.toString();
  }

  @Override
  public boolean usePool() {
    return false;
  }

  @Override
  public ScriptRunner getIndexScript() throws IOException {
    return getScriptRunner("index_maxdb.sql");
  }
}