// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.IndexConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.eclipse.jgit.lib.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class ElasticV5QueryGroupsTest extends AbstractQueryGroupsTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return IndexConfig.createForElasticsearch();
  }

  private static ElasticNodeInfo nodeInfo;
  private static ElasticContainer container;

  @BeforeClass
  public static void startIndexService() {
    LogManager.getRootLogger().setLevel(Level.DEBUG);
    if (nodeInfo != null) {
      // do not start Elasticsearch twice
      return;
    }

    container = ElasticContainer.createAndStart(ElasticVersion.V5_6);
    nodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (container != null) {
      container.stop();
    }
    LogManager.getRootLogger().setLevel(Level.INFO);
  }

  @Override
  protected void initAfterLifecycleStart() throws Exception {
    super.initAfterLifecycleStart();
    ElasticTestUtils.createAllIndexes(injector);
  }

  @Override
  protected Injector createInjector() {
    Config elasticsearchConfig = new Config(config);
    InMemoryModule.setDefaults(elasticsearchConfig);
    String indicesPrefix = getSanitizedMethodName();
    ElasticTestUtils.configure(
        elasticsearchConfig, nodeInfo.port, indicesPrefix, ElasticVersion.V5_6);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig, notesMigration));
  }
}
