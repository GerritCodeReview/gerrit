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

package com.google.gerrit.elasticsearch.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.elasticsearch.ElasticAccountIndex.ACCOUNTS;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CLOSED_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.OPEN_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticGroupIndex.GROUPS;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gerrit.elasticsearch.ElasticAccountIndex;
import com.google.gerrit.elasticsearch.ElasticAccountIndex.AccountMapping;
import com.google.gerrit.elasticsearch.ElasticChangeIndex.ChangeMapping;
import com.google.gerrit.elasticsearch.ElasticGroupIndex;
import com.google.gerrit.elasticsearch.ElasticGroupIndex.GroupMapping;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

public final class ElasticTestUtils {
  static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();

  public static class ElasticNodeInfo {
    public final Node node;
    public final String port;
    public final File elasticDir;

    private ElasticNodeInfo(Node node, File rootDir, String port) {
      this.node = node;
      this.port = port;
      this.elasticDir = rootDir;
    }
  }

  public static void configure(Config config, String port) {
    config.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    config.setString("elasticsearch", "test", "protocol", "http");
    config.setString("elasticsearch", "test", "hostname", "localhost");
    config.setString("elasticsearch", "test", "port", port);
  }

  public static ElasticNodeInfo startElasticsearchNode()
      throws InterruptedException, ExecutionException {
    File elasticDir = Files.createTempDir();
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
    Node node = NodeBuilder.nodeBuilder().settings(settings).node();

    // Wait for it to be ready
    node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

    assertThat(node.isClosed()).isFalse();
    return new ElasticNodeInfo(node, elasticDir, getHttpPort(node));
  }

  public static void deleteAllIndexes(ElasticNodeInfo nodeInfo) {
    nodeInfo.node.client().admin().indices().prepareDelete("_all").execute();
  }

  public static class NodeInfo {
    String httpAddress;
  }

  public static class Info {
    Map<String, NodeInfo> nodes;
  }

  public static void createAllIndexes(ElasticNodeInfo nodeInfo) {
    Schema<ChangeData> changeSchema = ChangeSchemaDefinitions.INSTANCE.getLatest();
    ChangeMapping openChangesMapping = new ChangeMapping(changeSchema);
    ChangeMapping closedChangesMapping = new ChangeMapping(changeSchema);
    openChangesMapping.closedChanges = null;
    closedChangesMapping.openChanges = null;
    nodeInfo
        .node
        .client()
        .admin()
        .indices()
        .prepareCreate(String.format("%s_%04d", CHANGES, changeSchema.getVersion()))
        .addMapping(OPEN_CHANGES, gson.toJson(openChangesMapping))
        .addMapping(CLOSED_CHANGES, gson.toJson(closedChangesMapping))
        .execute()
        .actionGet();

    Schema<AccountState> accountSchema = AccountSchemaDefinitions.INSTANCE.getLatest();
    AccountMapping accountMapping = new AccountMapping(accountSchema);
    nodeInfo
        .node
        .client()
        .admin()
        .indices()
        .prepareCreate(String.format("%s_%04d", ACCOUNTS, accountSchema.getVersion()))
        .addMapping(ElasticAccountIndex.ACCOUNTS, gson.toJson(accountMapping))
        .execute()
        .actionGet();

    Schema<AccountGroup> groupSchema = GroupSchemaDefinitions.INSTANCE.getLatest();
    GroupMapping groupMapping = new GroupMapping(groupSchema);
    nodeInfo
        .node
        .client()
        .admin()
        .indices()
        .prepareCreate(String.format("%s_%04d", GROUPS, groupSchema.getVersion()))
        .addMapping(ElasticGroupIndex.GROUPS, gson.toJson(groupMapping))
        .execute()
        .actionGet();
  }

  private static String getHttpPort(Node node) throws InterruptedException, ExecutionException {
    String nodes =
        node.client().admin().cluster().nodesInfo(new NodesInfoRequest("*")).get().toString();
    Gson gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    Info info = gson.fromJson(nodes, Info.class);
    if (info.nodes == null || info.nodes.size() != 1) {
      throw new RuntimeException("Cannot extract local Elasticsearch http port");
    }
    Iterator<NodeInfo> values = info.nodes.values().iterator();
    String httpAddress = values.next().httpAddress;
    if (Strings.isNullOrEmpty(httpAddress)) {
      throw new RuntimeException("Cannot extract local Elasticsearch http port");
    }
    if (httpAddress.indexOf(':') < 0) {
      throw new RuntimeException("Seems that port is not included in Elasticsearch http_address");
    }
    return httpAddress.substring(httpAddress.indexOf(':') + 1, httpAddress.length());
  }

  private ElasticTestUtils() {
    // hide default constructor
  }
}
