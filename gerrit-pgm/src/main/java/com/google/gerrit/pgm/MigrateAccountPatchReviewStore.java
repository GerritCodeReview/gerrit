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

package com.google.gerrit.pgm;

import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore.Row;
import com.google.inject.Injector;
import com.google.inject.Key;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.util.List;

/** Migrates AccountPatchReviewStore from one to another */
public class MigrateAccountPatchReviewStore extends SiteProgram {

  @Option(name = "--sourceUrl", usage = "Url of source database")
  private String sourceUrl;

  @Option(name = "--limit", usage = "limit of fetching from source and push to target on each time",
      required = false)
  private long limit = 100000;

  @Override
  public int run() throws Exception {
    SitePaths sitePaths = new SitePaths(getSitePath());

    if (sourceUrl == null) {
      sourceUrl = new StringBuilder().append("jdbc:h2:")
          .append(sitePaths.db_dir.resolve("account_patch_reviews"))
          .toString();
    }
    System.out.println("source Url: " + sourceUrl);
    JdbcAccountPatchReviewStore sourceJdbcAccountPatchReviewStore =
        getJdbcAccountPatchReviewStore(sourceUrl);

    Injector dbInjector = createDbInjector(DataSourceProvider.Context.SINGLE_USER);
    Config cfg = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    String targetUrl = cfg.getString("accountPatchReviewDb", null, "url");
    if (targetUrl == null) {
      System.err.println("accountPatchReviewDb.url is null in gerrit.config");
      return 1;
    }
    System.out.println("target Url: " + targetUrl);
    JdbcAccountPatchReviewStore targetJdbcAccountPatchReviewStore =
        getJdbcAccountPatchReviewStore(targetUrl);
    targetJdbcAccountPatchReviewStore.createTableIfNotExists();
    long offset = 0;
    List<Row> rows = sourceJdbcAccountPatchReviewStore.selectRows(limit, offset);
    while (!rows.isEmpty()) {
      targetJdbcAccountPatchReviewStore.insertRows(rows);
      offset += limit;
      rows = sourceJdbcAccountPatchReviewStore.selectRows(limit, offset);
    }
    return 0;
  }

  private JdbcAccountPatchReviewStore getJdbcAccountPatchReviewStore(String url) {
    Config cfg = new Config();
    cfg.setString("accountPatchReviewDb", null, "url", url);
    return JdbcAccountPatchReviewStore.createAccountPatchReviewStore(cfg, null);
  }
}
