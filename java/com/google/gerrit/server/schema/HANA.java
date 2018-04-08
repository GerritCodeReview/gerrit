// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.config.ConfigSection;
import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;

class HANA extends BaseDataSourceType {

  private Config cfg;

  @Inject
  HANA(@GerritServerConfig Config cfg) {
    super("com.sap.db.jdbc.Driver");
    this.cfg = cfg;
  }

  @Override
  public String getUrl() {
    final StringBuilder b = new StringBuilder();
    final ConfigSection dbs = new ConfigSection(cfg, "database");
    b.append("jdbc:sap://");
    b.append(hostname(dbs.required("hostname")));
    b.append(port(dbs.optional("port")));
    String database = dbs.optional("database");
    if (!Strings.isNullOrEmpty(database)) {
      b.append("?databaseName=").append(database);
    }
    return b.toString();
  }

  @Override
  public ScriptRunner getIndexScript() throws IOException {
    // HANA uses column tables and should not require additional indices
    return ScriptRunner.NOOP;
  }
}
