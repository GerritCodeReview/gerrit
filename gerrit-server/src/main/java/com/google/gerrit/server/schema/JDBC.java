// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

class JDBC extends BaseDataSourceType {

  protected final Config cfg;

  @Inject
  JDBC(@GerritServerConfig final Config cfg) {
    super(ConfigUtil.getRequired(cfg, "database", "driver"));
    this.cfg = cfg;
  }

  @Override
  public String getUrl() {
    return ConfigUtil.getRequired(cfg, "database", "url");
  }

  @Override
  public boolean usePool() {
    // MySQL has given us trouble with the connection pool,
    // sometimes the backend disconnects and the pool winds
    // up with a stale connection. Fortunately opening up
    // a new MySQL connection is usually very fast.
    String url = Strings.nullToEmpty(cfg.getString("database", null, "url"));
    return !url.startsWith("jdbc:mysql:");
  }
}
