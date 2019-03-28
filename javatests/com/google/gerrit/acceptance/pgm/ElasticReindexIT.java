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

import static com.google.gerrit.elasticsearch.ElasticTestUtils.createAllIndexes;
import static com.google.gerrit.elasticsearch.ElasticTestUtils.getConfig;

import com.google.gerrit.elasticsearch.ElasticVersion;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;

public class ElasticReindexIT extends AbstractReindexTests {

  @ConfigSuite.Default
  public static Config elasticsearchV5() {
    return getConfig(ElasticVersion.V5_6);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV6() {
    return getConfig(ElasticVersion.V6_7);
  }

  @ConfigSuite.Config
  public static Config elasticsearchV7() {
    return getConfig(ElasticVersion.V7_0);
  }

  @Override
  public void configureIndex(Injector injector) throws Exception {
    createAllIndexes(injector);
  }

  @Before
  public void reindexFirstSinceElastic() throws Exception {
    assertServerStartupFails();
    runGerrit("reindex", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
  }
}
