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

import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.File;

public class ElasticsearchQueryChangesTest extends AbstractQueryChangesTest {
  private static Node node;

  @Before
  public void startElasticsearchNode() throws Exception {
    Settings settings = ImmutableSettings.settingsBuilder()
        .loadFromClasspath("elasticsearch.yml")
        .build();

    // Delete any existing data
    File dataDir = new File(settings.get("path.data"));
    if (dataDir.exists()) {
      FileSystemUtils.deleteRecursively(dataDir, true);
    }

    // Start the node
    node = NodeBuilder.nodeBuilder()
        .settings(settings)
        .local(true)
        .node();

    // Wait for it to be ready
    node.client()
        .admin()
        .cluster()
        .prepareHealth()
        .setWaitForYellowStatus()
        .execute()
        .actionGet();

    assertThat(node.isClosed()).isFalse();
  }

  @After
  public void stopElasticsearchServer() {
    if (node != null) {
      node.stop();
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
    elasticsearchConfig.setBoolean("index", "elasticsearch", "refresh", true);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig));
  }
}
