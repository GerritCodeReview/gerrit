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

package com.google.gerrit.elasticsearch;

import com.google.gerrit.elasticsearch.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.server.query.group.AbstractQueryGroupsTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class ElasticQueryGroupsTest extends AbstractQueryGroupsTest {
  private static ElasticNodeInfo nodeInfo;

  @BeforeClass
  public static void startIndexService() throws InterruptedException, ExecutionException {
    if (nodeInfo != null) {
      // do not start Elasticsearch twice
      return;
    }
    nodeInfo = ElasticTestUtils.startElasticsearchNode();
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
    String indicesPrefix = testName.getMethodName().toLowerCase() + "_";
    ElasticTestUtils.configure(elasticsearchConfig, nodeInfo.port, indicesPrefix);
    ElasticTestUtils.createAllIndexes(nodeInfo, indicesPrefix);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig, notesMigration));
  }
}
