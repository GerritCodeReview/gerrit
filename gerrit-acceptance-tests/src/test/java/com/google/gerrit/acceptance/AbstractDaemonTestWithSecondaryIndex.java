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

package com.google.gerrit.acceptance;

import com.google.gerrit.pgm.Reindex;

import org.eclipse.jgit.lib.Config;

import java.io.File;

public class AbstractDaemonTestWithSecondaryIndex extends AbstractDaemonTest {

  @Override
  protected GerritServer startServer(Config cfg) throws Exception {
    if (cfg == null) {
      cfg = new Config();
    }
    cfg.setString("index", null, "type", "lucene");
    File site = GerritServer.initSite(cfg);
    reindex(site);
    GerritServer server = GerritServer.start(site);
    return server;
  }

  private static void reindex(File site) throws Exception {
    Reindex reindex = new Reindex();
    int rc = reindex.main(new String[] {"-d", site.getPath()});
    if (rc != 0) {
      throw new RuntimeException("Reindex failed");
    }
  }
}
