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

import org.apache.http.HttpHost;
import org.junit.AssumptionViolatedException;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/* Helper class for running ES integration tests in docker container */
public class ElasticContainer extends ElasticsearchContainer {
  private static final int ELASTICSEARCH_DEFAULT_PORT = 9200;

  public static ElasticContainer createAndStart(ElasticVersion version) {
    // Assumption violation is not natively supported by Testcontainers.
    // See https://github.com/testcontainers/testcontainers-java/issues/343
    try {
      ElasticContainer container = new ElasticContainer(version);
      container.start();
      return container;
    } catch (Throwable t) {
      throw new AssumptionViolatedException("Unable to start container", t);
    }
  }

  private static String getImageName(ElasticVersion version) {
    switch (version) {
      case V5_6:
        return "docker.elastic.co/elasticsearch/elasticsearch:5.6.16";
      case V6_2:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.2.4";
      case V6_3:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.3.2";
      case V6_4:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.4.3";
      case V6_5:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.5.4";
      case V6_6:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.6.2";
      case V6_7:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.7.1";
      case V7_0:
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:7.0.0";
    }
    throw new IllegalStateException("No tests for version: " + version.name());
  }

  private ElasticContainer(ElasticVersion version) {
    super(getImageName(version));
  }

  public HttpHost getHttpHost() {
    return new HttpHost(getContainerIpAddress(), getMappedPort(ELASTICSEARCH_DEFAULT_PORT));
  }
}
