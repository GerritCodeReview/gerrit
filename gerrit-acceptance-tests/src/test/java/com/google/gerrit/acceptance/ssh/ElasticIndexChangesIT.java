package com.google.gerrit.acceptance.ssh;

import java.util.UUID;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;

@NoHttpd
public class ElasticIndexChangesIT extends AbstractIndexChangesTests {

  private static ElasticContainer<?> container;

  @ConfigSuite.Default
  public static Config elasticsearch() {
    ElasticNodeInfo elasticNodeInfo;
    try {
      container = ElasticContainer.createAndStart();
      elasticNodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
    } catch (Throwable t) {
      return null;
    }
    String indicesPrefix = UUID.randomUUID().toString();
    Config cfg = new Config();
    ElasticTestUtils.configure(cfg, elasticNodeInfo.port, indicesPrefix);
    return cfg;
  }

  @Override
  public void configureIndex(Injector injector) throws Exception {
    ElasticTestUtils.createAllIndexes(injector);
  }

  @After
  public void stopElasticServer() {
    if (container != null) {
      container.stop();
      container = null;
    }
  }
}
