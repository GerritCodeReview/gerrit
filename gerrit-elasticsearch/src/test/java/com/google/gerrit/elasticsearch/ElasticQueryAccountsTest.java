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

import static com.google.gerrit.elasticsearch.ElasticAccountIndex.ACCOUNTS_PREFIX;

import com.google.gerrit.elasticsearch.ElasticAccountIndex.AccountMapping;
import com.google.gerrit.elasticsearch.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.query.account.AbstractQueryAccountsTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.concurrent.ExecutionException;

public class ElasticQueryAccountsTest extends AbstractQueryAccountsTest {
  private static final String INDEX_NAME =
      String.format("%s%04d", ACCOUNTS_PREFIX,
          AccountSchemaDefinitions.INSTANCE.getLatest().getVersion());
  private static ElasticNodeInfo nodeInfo;

  @BeforeClass
  public static void startIndexService()
      throws InterruptedException, ExecutionException {
    if (nodeInfo != null) {
      // do not start Elasticsearch twice
      return;
    }
    nodeInfo = ElasticTestUtils.startElasticsearchNode();
    createIndexes();
  }

  private static void createIndexes() {
    AccountMapping accountMapping =
        new AccountMapping(AccountSchemaDefinitions.INSTANCE.getLatest());
    nodeInfo.node
        .client()
        .admin()
        .indices()
        .prepareCreate(INDEX_NAME)
        .addMapping(ElasticAccountIndex.ACCOUNTS,
            ElasticTestUtils.gson.toJson(accountMapping))
        .execute()
        .actionGet();
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (nodeInfo != null) {
      nodeInfo.node.close();
      nodeInfo.elasticDir.delete();
      nodeInfo = null;
    }
  }

  @After
  public void cleanupIndex() {
    if (nodeInfo != null) {
      ElasticTestUtils.deleteIndexes(nodeInfo.node, INDEX_NAME);
      createIndexes();
    }
  }

  @Override
  protected Injector createInjector() {
    Config elasticsearchConfig = new Config(config);
    InMemoryModule.setDefaults(elasticsearchConfig);
    ElasticTestUtils.configure(elasticsearchConfig, nodeInfo.port);
    return Guice.createInjector(
        new InMemoryModule(elasticsearchConfig, notesMigration));
  }
}
