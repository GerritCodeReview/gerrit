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

import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CHANGES_PREFIX;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.CLOSED_CHANGES;
import static com.google.gerrit.elasticsearch.ElasticChangeIndex.OPEN_CHANGES;

import com.google.gerrit.elasticsearch.ElasticChangeIndex.ChangeMapping;
import com.google.gerrit.elasticsearch.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class ElasticQueryChangesTest extends AbstractQueryChangesTest {
  private static final String INDEX_NAME =
      String.format("%s%04d", CHANGES_PREFIX,
          ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion());
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

  @After
  public void cleanupIndex() {
    if (nodeInfo != null) {
      ElasticTestUtils.deleteIndexes(nodeInfo.node, INDEX_NAME);
      createIndexes();
    }
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (nodeInfo != null) {
      nodeInfo.node.close();
      nodeInfo.elasticDir.delete();
      nodeInfo = null;
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

  private static void createIndexes() {
    ChangeMapping openChangesMapping =
        new ChangeMapping(ChangeSchemaDefinitions.INSTANCE.getLatest());
    ChangeMapping closedChangesMapping =
        new ChangeMapping(ChangeSchemaDefinitions.INSTANCE.getLatest());
    openChangesMapping.closedChanges = null;
    closedChangesMapping.openChanges = null;
    nodeInfo.node
        .client()
        .admin()
        .indices()
        .prepareCreate(INDEX_NAME)
        .addMapping(OPEN_CHANGES,
            ElasticTestUtils.gson.toJson(openChangesMapping))
        .addMapping(CLOSED_CHANGES,
            ElasticTestUtils.gson.toJson(closedChangesMapping))
        .execute()
        .actionGet();
  }

  @Test
  public void byOwnerInvalidQuery() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    insert(repo, newChange(repo), userId);
    String nameEmail = user.asIdentifiedUser().getNameEmail();
    assertQuery("owner: \"" + nameEmail + "\"\\");
  }

}
