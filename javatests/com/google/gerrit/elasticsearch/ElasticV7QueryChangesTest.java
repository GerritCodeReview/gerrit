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

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.server.query.change.AbstractQueryChangesTest;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.GerritTestName;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.IndexConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;

public class ElasticV7QueryChangesTest extends AbstractQueryChangesTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return IndexConfig.createForElasticsearch();
  }

  @ConfigSuite.Config
  public static Config searchAfterPaginationType() {
    Config config = defaultConfig();
    config.setString("index", null, "paginationType", "SEARCH_AFTER");
    return config;
  }

  private static ElasticContainer container;
  private static CloseableHttpAsyncClient client;

  @BeforeClass
  public static void startIndexService() {
    container = ElasticContainer.createAndStart(ElasticVersion.V7_16);
    client = HttpAsyncClients.createDefault();
    client.start();
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (container != null) {
      container.stop();
    }
  }

  @Rule public final GerritTestName testName = new GerritTestName();

  @After
  public void closeIndex() throws Exception {
    // Close the index after each test to prevent exceeding Elasticsearch's
    // shard limit (see Issue 10120).
    client
        .execute(
            new HttpPost(
                String.format(
                    "http://%s:%d/%s*/_close",
                    container.getHttpHost().getHostName(),
                    container.getHttpHost().getPort(),
                    testName.getSanitizedMethodName())),
            HttpClientContext.create(),
            null)
        .get(5, MINUTES);
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
    String indicesPrefix = testName.getSanitizedMethodName();
    ElasticTestUtils.configure(elasticsearchConfig, container, indicesPrefix);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig));
  }
}
