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
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore.Row;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.util.List;

/** Migrates AccountPatchReviewStore from one to another */
public class MigrateAccountPatchReviewStore extends SiteProgram {

  @Option(name = "--sourceUrl", usage = "Url of source database", required = true)
  private String sourceUrl;

  @Option(name = "--targetUrl", usage = "Url of target database", required = true)
  private String targetUrl;

  @Option(name = "--limit", usage = "limit of fetching from source and push to target on each time",
      required = false)
  private long limit = 100000;

  @Override
  public int run() throws Exception {
    JdbcAccountPatchReviewStore sourceJdbcAccountPatchReviewStore =
        getJdbcAccountPatchReviewStore(sourceUrl);
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
