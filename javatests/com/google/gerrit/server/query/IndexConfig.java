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

package com.google.gerrit.server.query;

import org.eclipse.jgit.lib.Config;

public class IndexConfig {
  public static Config create() {
    return createFromExistingConfig(new Config());
  }

  public static Config createFromExistingConfig(Config cfg) {
    cfg.setInt("index", null, "maxPages", 10);
    cfg.setString("trackingid", "query-bug", "footer", "Bug:");
    cfg.setString("trackingid", "query-bug", "match", "QUERY\\d{2,8}");
    cfg.setString("trackingid", "query-bug", "system", "querytests");
    cfg.setString("trackingid", "query-feature", "footer", "Feature");
    cfg.setString("trackingid", "query-feature", "match", "QUERY\\d{2,8}");
    cfg.setString("trackingid", "query-feature", "system", "querytests");
    return cfg;
  }

  public static Config createForLucene() {
    return create();
  }

  public static Config createForElasticsearch() {
    Config cfg = create();

    // For some reason enabling the staleness checker increases the flakiness of the Elasticsearch
    // tests. Hence disable the staleness checker.
    cfg.setBoolean("index", null, "autoReindexIfStale", false);

    return cfg;
  }
}
