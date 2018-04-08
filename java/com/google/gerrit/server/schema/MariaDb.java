// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.schema.JdbcUtil.hostname;
import static com.google.gerrit.server.schema.JdbcUtil.port;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.server.config.ConfigSection;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

class MariaDb extends BaseDataSourceType {
  private final Config cfg;

  @Inject
  MariaDb(@GerritServerConfig Config cfg) {
    super("org.mariadb.jdbc.Driver");
    this.cfg = cfg;
  }

  @Override
  public String getUrl() {
    StringBuilder b = new StringBuilder();
    ConfigSection dbs = new ConfigSection(cfg, "database");
    b.append("jdbc:mariadb://");
    b.append(hostname(dbs.optional("hostname")));
    b.append(port(dbs.optional("port")));
    b.append("/");
    b.append(dbs.required("database"));
    b.append("?useBulkStmts=false");
    return b.toString();
  }

  @Override
  public boolean usePool() {
    // MariaDB has given us trouble with the connection pool,
    // sometimes the backend disconnects and the pool winds
    // up with a stale connection. Fortunately opening up
    // a new MariaDB connection is usually very fast.
    return false;
  }
}
