// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.After;

import java.io.File;
import java.nio.file.Path;

public class ElasticQueryChangesTest extends AbstractQueryChangesTest {
  private static Node node;

  @Override
  protected void startIndexService(SitePaths sitePaths) {
    Path elasticDir = sitePaths.site_path.resolve("elasticsearch");
    Settings settings = Settings.settingsBuilder()
      .put("cluster.name", "gerrit")
      .put("node.name", "Gerrit Elasticsearch Test Node")
      .put("node.local", true)
      .put("discovery.zen.ping.multicast.enabled", false)
      .put("index.store.fs.memory.enabled", true)
      .put("index.gateway.type", "none")
      .put("index.max_result_window", Integer.MAX_VALUE)
      .put("gateway.type", "default")
      .put("discovery.zen.ping.unicast.hosts", "[\"localhost\"]")
      .put("path.home", elasticDir)
      .put("path.data", elasticDir.resolve("data"))
      .put("path.work", elasticDir.resolve("work"))
      .put("path.logs", elasticDir.resolve("logs"))
      .build();

    // Delete any existing data
    String dataPath = settings.get("path.data");
    assertThat(dataPath).isNotNull();
    try {
      File dataDir = new File(dataPath);
      if (dataDir.exists()) {
        FileSystemUtils.deleteSubDirectories(dataDir.toPath());
      }
    } catch (Exception e) {
      fail(String.format("Could not delete data folder %s: %s", dataPath, e));
    }

    // Start the node
    node = NodeBuilder.nodeBuilder()
        .settings(settings)
        .node();

    // Wait for it to be ready
    node.client()
        .admin()
        .cluster()
        .prepareHealth()
        .setWaitForYellowStatus()
        .execute()
        .actionGet();

    node.client()
        .admin()
        .indices()
        .prepareCreate("gerrit")
        .addMapping("open_changes", ElasticChangeIndex.getMappingProperties("open_changes"))
        .addMapping("closed_changes", ElasticChangeIndex.getMappingProperties("closed_changes"))
        .execute()
        .actionGet();

    assertThat(node.isClosed()).isFalse();
  }

  @After
  public void stopElasticsearchServer() {
    if (node != null) {
      node.close();
      node = null;
    }
  }

  @Override
  protected Injector createInjector() {
    Config elasticsearchConfig = new Config(config);
    InMemoryModule.setDefaults(elasticsearchConfig);
    elasticsearchConfig.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    elasticsearchConfig.setString("index", null, "url", "http://localhost:9200");
    elasticsearchConfig.setString("index", null, "name", "gerrit");
    elasticsearchConfig.setBoolean("index", "elasticsearch", "test", true);
    return Guice.createInjector(
        new InMemoryModule(elasticsearchConfig, notesMigration));
  }
}
