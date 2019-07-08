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

package com.google.gerrit.acceptance.ssh;

import com.google.gerrit.elasticsearch.ElasticVersion;
import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;

public class ElasticIndexIT extends AbstractIndexTests {

  private static Config getConfig(ElasticVersion version) {
    ElasticNodeInfo elasticNodeInfo;
    ElasticContainer<?> container = ElasticContainer.createAndStart(version);
    elasticNodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
    String indicesPrefix = UUID.randomUUID().toString();
    Config cfg = new Config();
    ElasticTestUtils.configure(cfg, elasticNodeInfo.port, indicesPrefix, version);
    return cfg;
  }

  @ConfigSuite.Default
  public static Config elasticsearchV2() {
    return getConfig(ElasticVersion.V2_4);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV5() {
    return getConfig(ElasticVersion.V5_6);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV6() {
    return getConfig(ElasticVersion.V6_4);
  }

  @Override
  public void configureIndex(Injector injector) throws Exception {
    ElasticTestUtils.createAllIndexes(injector);
  }
}
