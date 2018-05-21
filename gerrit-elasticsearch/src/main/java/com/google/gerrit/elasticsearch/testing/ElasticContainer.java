package com.google.gerrit.elasticsearch.testing;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.apache.http.HttpHost;
import org.testcontainers.containers.GenericContainer;

/* Helper class for running ES integration tests in docker container */
public class ElasticContainer<SELF extends ElasticContainer<SELF>> extends GenericContainer<SELF> {
  private static final String NAME = "elasticsearch";
  private static final String VERSION = "2.4.6-alpine";
  private static final int ELASTICSEARCH_DEFAULT_PORT = 9200;

  public ElasticContainer() {
    this(NAME + ":" + VERSION);
  }

  public ElasticContainer(String dockerImageName) {
    super(dockerImageName);
  }

  @Override
  protected void configure() {
    addExposedPort(ELASTICSEARCH_DEFAULT_PORT);

    // https://github.com/docker-library/elasticsearch/issues/58
    addEnv("-Ees.network.host", "0.0.0.0");
  }

  @Override
  protected Set<Integer> getLivenessCheckPorts() {
    return ImmutableSet.of(getMappedPort(ELASTICSEARCH_DEFAULT_PORT));
  }

  public HttpHost getHttpHost() {
    return new HttpHost(getContainerIpAddress(), getMappedPort(ELASTICSEARCH_DEFAULT_PORT));
  }
}
