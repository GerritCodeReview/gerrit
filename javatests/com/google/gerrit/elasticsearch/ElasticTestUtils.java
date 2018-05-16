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

import static com.google.gerrit.elasticsearch.ElasticAccountIndex.ACCOUNTS;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CLOSED_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.OPEN_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticGroupIndex.GROUPS;
import static com.google.gerrit.elasticsearch.ElasticProjectIndex.PROJECTS;

import com.google.gerrit.elasticsearch.ElasticAccountIndex.AccountMapping;
import com.google.gerrit.elasticsearch.ElasticChangeIndex.ChangeMapping;
import com.google.gerrit.elasticsearch.ElasticGroupIndex.GroupMapping;
import com.google.gerrit.elasticsearch.ElasticProjectIndex.ProjectMapping;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.InetSocketAddress;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

final class ElasticTestUtils {
  static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();

  static class ElasticNodeInfo {
    final int port;
    final TransportClient client;

    ElasticNodeInfo(TransportClient client, int port) {
      this.port = port;
      this.client = client;
    }
  }

  static void configure(Config config, int port, String prefix) {
    config.setEnum("index", null, "type", IndexType.ELASTICSEARCH);
    config.setString("elasticsearch", "test", "protocol", "http");
    config.setString("elasticsearch", "test", "hostname", "localhost");
    config.setInt("elasticsearch", "test", "port", port);
    config.setString("elasticsearch", null, "prefix", prefix);
  }

  static TransportClient attachClient(int tcpPort) {
    TransportClient.Builder builder = new TransportClient.Builder();
    TransportClient client =
        builder
            .settings(Settings.builder().put("client.transport.ignore_cluster_name", true).build())
            .build();
    client.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(tcpPort)));
    return client;
  }

  static void deleteAllIndexes(ElasticNodeInfo nodeInfo, String prefix) {
    Schema<ChangeData> changeSchema = ChangeSchemaDefinitions.INSTANCE.getLatest();

    nodeInfo
        .client
        .admin()
        .indices()
        .prepareDelete(String.format("%s%s_%04d", prefix, CHANGES, changeSchema.getVersion()))
        .execute()
        .actionGet();

    Schema<AccountState> accountSchema = AccountSchemaDefinitions.INSTANCE.getLatest();
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareDelete(String.format("%s%s_%04d", prefix, ACCOUNTS, accountSchema.getVersion()))
        .execute()
        .actionGet();

    Schema<InternalGroup> groupSchema = GroupSchemaDefinitions.INSTANCE.getLatest();
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareDelete(String.format("%s%s_%04d", prefix, GROUPS, groupSchema.getVersion()))
        .execute()
        .actionGet();

    Schema<ProjectData> projectSchema = ProjectSchemaDefinitions.INSTANCE.getLatest();
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareDelete(String.format("%s%s_%04d", prefix, PROJECTS, projectSchema.getVersion()))
        .execute()
        .actionGet();
  }

  static class NodeInfo {
    String httpAddress;
  }

  static class Info {
    Map<String, NodeInfo> nodes;
  }

  static void createAllIndexes(ElasticNodeInfo nodeInfo, String prefix) {
    Schema<ChangeData> changeSchema = ChangeSchemaDefinitions.INSTANCE.getLatest();
    ChangeMapping openChangesMapping = new ChangeMapping(changeSchema);
    ChangeMapping closedChangesMapping = new ChangeMapping(changeSchema);
    openChangesMapping.closedChanges = null;
    closedChangesMapping.openChanges = null;

    Settings settings =
        Settings.builder()
            .put("index.max_result_window", Integer.MAX_VALUE)
            .put("index.store.fs.memory.enabled", true)
            .build();

    nodeInfo
        .client
        .admin()
        .indices()
        .prepareCreate(String.format("%s%s_%04d", prefix, CHANGES, changeSchema.getVersion()))
        .setSettings(settings)
        .addMapping(OPEN_CHANGES, gson.toJson(openChangesMapping))
        .addMapping(CLOSED_CHANGES, gson.toJson(closedChangesMapping))
        .execute()
        .actionGet();

    Schema<AccountState> accountSchema = AccountSchemaDefinitions.INSTANCE.getLatest();
    AccountMapping accountMapping = new AccountMapping(accountSchema);
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareCreate(String.format("%s%s_%04d", prefix, ACCOUNTS, accountSchema.getVersion()))
        .setSettings(settings)
        .addMapping(ElasticAccountIndex.ACCOUNTS, gson.toJson(accountMapping))
        .execute()
        .actionGet();

    Schema<InternalGroup> groupSchema = GroupSchemaDefinitions.INSTANCE.getLatest();
    GroupMapping groupMapping = new GroupMapping(groupSchema);
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareCreate(String.format("%s%s_%04d", prefix, GROUPS, groupSchema.getVersion()))
        .setSettings(settings)
        .addMapping(ElasticGroupIndex.GROUPS, gson.toJson(groupMapping))
        .execute()
        .actionGet();

    Schema<ProjectData> projectSchema = ProjectSchemaDefinitions.INSTANCE.getLatest();
    ProjectMapping projectMapping = new ProjectMapping(projectSchema);
    nodeInfo
        .client
        .admin()
        .indices()
        .prepareCreate(String.format("%s%s_%04d", prefix, PROJECTS, projectSchema.getVersion()))
        .setSettings(settings)
        .addMapping(ElasticProjectIndex.PROJECTS, gson.toJson(projectMapping))
        .execute()
        .actionGet();
  }

  private ElasticTestUtils() {
    // hide default constructor
  }
}
