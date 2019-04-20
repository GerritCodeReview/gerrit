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

package com.google.gerrit.elasticsearch;

import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.util.Collection;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;

public final class ElasticTestUtils {
  public static class ElasticNodeInfo {
    public final int port;

    public ElasticNodeInfo(int port) {
      this.port = port;
    }
  }

  public static void configure(Config config, int port, String prefix, ElasticVersion version) {
    config.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    config.setString("elasticsearch", null, "server", "http://localhost:" + port);
    config.setString("elasticsearch", null, "prefix", prefix);
    config.setInt("index", null, "maxLimit", 10000);
    String password = version == ElasticVersion.V5_6 ? "changeme" : null;
    if (password != null) {
      config.setString("elasticsearch", null, "password", password);
    }
  }

  public static void configure(Config config, int port, String prefix) {
    configure(config, port, prefix, null);
  }

  public static void createAllIndexes(Injector injector) {
    Collection<IndexDefinition<?, ?, ?>> indexDefs =
        injector.getInstance(Key.get(new TypeLiteral<Collection<IndexDefinition<?, ?, ?>>>() {}));
    for (IndexDefinition<?, ?, ?> indexDef : indexDefs) {
      indexDef.getIndexCollection().getSearchIndex().deleteAll();
    }
  }

  public static Config getConfig(ElasticVersion version) {
    ElasticNodeInfo elasticNodeInfo;
    ElasticContainer container = ElasticContainer.createAndStart(version);
    elasticNodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
    String indicesPrefix = UUID.randomUUID().toString();
    Config cfg = new Config();
    configure(cfg, elasticNodeInfo.port, indicesPrefix, version);
    return cfg;
  }

  private ElasticTestUtils() {
    // hide default constructor
  }
}
