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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CLOSED_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.OPEN_CHANGES;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gerrit.elasticsearch.ElasticChangeIndex.ChangeMapping;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class ElasticQueryChangesTest extends AbstractQueryChangesTest {
  private static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();
  private static Node node;
  private static String port;
  private static File elasticDir;

  static class NodeInfo {
    String httpAddress;
  }

  static class Info {
    Map<String, NodeInfo> nodes;
  }

  @BeforeClass
  public static void startIndexService() throws InterruptedException, ExecutionException {
    if (node != null) {
      // do not start Elasticsearch twice
      return;
    }
    elasticDir = Files.createTempDir();
    Path elasticDirPath = elasticDir.toPath();
    Settings settings =
        Settings.settingsBuilder()
            .put("cluster.name", "gerrit")
            .put("node.name", "Gerrit Elasticsearch Test Node")
            .put("node.local", true)
            .put("discovery.zen.ping.multicast.enabled", false)
            .put("index.store.fs.memory.enabled", true)
            .put("index.gateway.type", "none")
            .put("index.max_result_window", Integer.MAX_VALUE)
            .put("gateway.type", "default")
            .put("http.port", 0)
            .put("discovery.zen.ping.unicast.hosts", "[\"localhost\"]")
            .put("path.home", elasticDirPath.toAbsolutePath())
            .put("path.data", elasticDirPath.resolve("data").toAbsolutePath())
            .put("path.work", elasticDirPath.resolve("work").toAbsolutePath())
            .put("path.logs", elasticDirPath.resolve("logs").toAbsolutePath())
            .put("transport.tcp.connect_timeout", "60s")
            .build();

    // Start the node
    node = NodeBuilder.nodeBuilder().settings(settings).node();

    // Wait for it to be ready
    node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

    createIndexes();

    assertThat(node.isClosed()).isFalse();
    port = getHttpPort();
  }

  @After
  public void cleanupIndex() {
    node.client().admin().indices().prepareDelete("gerrit").execute();
    createIndexes();
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (node != null) {
      node.close();
      node = null;
    }
    if (elasticDir != null && elasticDir.delete()) {
      elasticDir = null;
    }
  }

  @Override
  protected Injector createInjector() {
    Config elasticsearchConfig = new Config(config);
    InMemoryModule.setDefaults(elasticsearchConfig);
    elasticsearchConfig.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    elasticsearchConfig.setString("index", null, "protocol", "http");
    elasticsearchConfig.setString("index", null, "hostname", "localhost");
    elasticsearchConfig.setString("index", null, "port", port);
    elasticsearchConfig.setString("index", null, "name", "gerrit");
    elasticsearchConfig.setBoolean("index", "elasticsearch", "test", true);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig, notesMigration));
  }

  private static void createIndexes() {
    ChangeMapping openChangesMapping =
        new ChangeMapping(ChangeSchemaDefinitions.INSTANCE.getLatest());
    ChangeMapping closedChangesMapping =
        new ChangeMapping(ChangeSchemaDefinitions.INSTANCE.getLatest());
    openChangesMapping.closedChanges = null;
    closedChangesMapping.openChanges = null;
    node.client()
        .admin()
        .indices()
        .prepareCreate("gerrit")
        .addMapping(OPEN_CHANGES, gson.toJson(openChangesMapping))
        .addMapping(CLOSED_CHANGES, gson.toJson(closedChangesMapping))
        .execute()
        .actionGet();
  }

  private static String getHttpPort() throws InterruptedException, ExecutionException {
    String nodes =
        node.client().admin().cluster().nodesInfo(new NodesInfoRequest("*")).get().toString();
    Gson gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    Info info = gson.fromJson(nodes, Info.class);

    checkState(info.nodes != null && info.nodes.size() == 1);
    Iterator<NodeInfo> values = info.nodes.values().iterator();
    String httpAddress = values.next().httpAddress;

    checkState(!Strings.isNullOrEmpty(httpAddress) && httpAddress.indexOf(':') > 0);
    return httpAddress.substring(httpAddress.indexOf(':') + 1, httpAddress.length());
  }
}
