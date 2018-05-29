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

package com.google.gerrit.acceptance.pgm;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;
import org.junit.After;

@NoHttpd
public class ElasticReindexIT extends AbstractReindexIT {
  private static ElasticContainer<?> container;

  @ConfigSuite.Config
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
