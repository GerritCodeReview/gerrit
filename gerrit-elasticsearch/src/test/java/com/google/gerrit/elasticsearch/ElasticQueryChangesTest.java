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
import org.junit.Before;

import java.io.File;

public class ElasticQueryChangesTest extends AbstractQueryChangesTest {
  private static Node node;

  @Before
  public void startElasticsearchNode() throws Exception {
    Settings settings = Settings.settingsBuilder()
      .put("cluster.name", "gerrit")
      .put("node.name", "Gerrit Elasticsearch Test Node")
      .put("node.local", true)
      .put("discovery.zen.ping.multicast.enabled", false)
      .put("index.store.fs.memory.enabled", true)
      .put("index.gateway.type", "none")
      .put("gateway.type", "none")
      .put("discovery.zen.ping.unicast.hosts", "[\"localhost\"]")
      .put("path.home", "./test_site/elasticsearch")
      .put("path.data", "./test_site/elasticsearch/data")
      .put("path.work", "./test_site/elasticsearch/work")
      .put("path.logs", "./test_site/elasticsearch/logs")
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
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig));
  }
}
