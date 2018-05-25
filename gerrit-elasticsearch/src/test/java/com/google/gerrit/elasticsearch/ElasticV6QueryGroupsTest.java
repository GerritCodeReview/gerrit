package com.google.gerrit.elasticsearch;

import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.server.query.group.AbstractQueryGroupsTest;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class ElasticV6QueryGroupsTest extends AbstractQueryGroupsTest {
  private static ElasticNodeInfo nodeInfo;
  private static ElasticContainer<?> container;

  @BeforeClass
  public static void startIndexService() {
    if (nodeInfo != null) {
      // do not start Elasticsearch twice
      return;
    }

    container = ElasticContainer.createAndStart(ElasticContainer.Version.V6);
    nodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
  }

  @AfterClass
  public static void stopElasticsearchServer() {
    if (container != null) {
      container.stop();
    }
  }

  private String testName() {
    return testName.getMethodName().toLowerCase() + "_";
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
    String indicesPrefix = testName();
    ElasticTestUtils.configure(elasticsearchConfig, nodeInfo.port, indicesPrefix);
    return Guice.createInjector(new InMemoryModule(elasticsearchConfig, notesMigration));
  }
}
