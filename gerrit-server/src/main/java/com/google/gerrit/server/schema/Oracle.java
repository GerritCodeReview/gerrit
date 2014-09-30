// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.config.ConfigSection;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

public class Oracle extends BaseDataSourceType {
  private Config cfg;

  @Inject
  public Oracle(@GerritServerConfig final Config cfg) {
    super("oracle.jdbc.driver.OracleDriver");
    this.cfg = cfg;
  }

  @Override
  public String getUrl() {
    final StringBuilder b = new StringBuilder();
    final ConfigSection dbc = new ConfigSection(cfg, "database");
    b.append("jdbc:oracle:thin:@");
    b.append(hostname(dbc.optional("hostname")));
    b.append(port(dbc.optional("port")));
    b.append(":");
    b.append(dbc.required("instance"));
    return b.toString();
  }
}
