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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
      case V6_6:
        return "blacktop/elasticsearch:6.6.2";
      case V6_7:
        return "blacktop/elasticsearch:6.7.2";
      case V6_8:
        return "blacktop/elasticsearch:6.8.11";
      case V7_0:
        return "blacktop/elasticsearch:7.0.1";
      case V7_1:
        return "blacktop/elasticsearch:7.1.1";
      case V7_2:
        return "blacktop/elasticsearch:7.2.1";
      case V7_3:
        return "blacktop/elasticsearch:7.3.2";
      case V7_4:
        return "blacktop/elasticsearch:7.4.2";
      case V7_5:
        return "blacktop/elasticsearch:7.5.2";
      case V7_6:
        return "blacktop/elasticsearch:7.6.2";
      case V7_7:
        return "blacktop/elasticsearch:7.7.1";
      case V7_8:
        return "blacktop/elasticsearch:7.8.0";
    }
    throw new IllegalStateException("No tests for version: " + version.name());
  }

  private ElasticContainer(ElasticVersion version) {
    super(getImageName(version));
  }

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger("org.testcontainers");
  }

  public HttpHost getHttpHost() {
    return new HttpHost(getContainerIpAddress(), getMappedPort(ELASTICSEARCH_DEFAULT_PORT));
  }
}
