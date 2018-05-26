package com.google.gerrit.acceptance.pgm;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.testutil.ConfigSuite;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;
import org.junit.After;

@NoHttpd
public class ElasticReindexIT extends ReindexIT {
  private static ElasticContainer<?> container;

  private static Config getConfig(ElasticContainer.Version version) {
    ElasticNodeInfo elasticNodeInfo;
    try {
      container = ElasticContainer.createAndStart(version);
      elasticNodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
    } catch (Throwable t) {
      return null;
    }
    String indicesPrefix = UUID.randomUUID().toString();
    Config cfg = new Config();
    ElasticTestUtils.configure(cfg, elasticNodeInfo.port, indicesPrefix);
    return cfg;
  }

  @ConfigSuite.Default
  public static Config elasticsearchV2() {
    return getConfig(ElasticContainer.Version.V2);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV5() {
    return getConfig(ElasticContainer.Version.V5);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV6() {
    return getConfig(ElasticContainer.Version.V6);
  }

  @Override
  public void configureIndex(StandaloneSiteTest.ServerContext ctx) throws Exception {
    ElasticTestUtils.createAllIndexes(ctx.getInjector());
  }

  @After
  public void stopElasticServer() {
    if (container != null) {
      container.stop();
      container = null;
    }
  }
}
