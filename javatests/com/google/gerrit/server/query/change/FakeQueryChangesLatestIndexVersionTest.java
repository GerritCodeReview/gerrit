// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.IndexConfig;
import org.eclipse.jgit.lib.Config;

/**
 * Test against {@link com.google.gerrit.index.testing.AbstractFakeIndex} using the latest schema.
 */
public class FakeQueryChangesLatestIndexVersionTest extends FakeQueryChangesTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return IndexConfig.createForFake();
  }

  @ConfigSuite.Config
  public static Config searchAfterPaginationType() {
    Config config = defaultConfig();
    config.setString("index", null, "paginationType", "SEARCH_AFTER");
    return config;
  }

  @ConfigSuite.Config
  public static Config nonePaginationType() {
    Config config = defaultConfig();
    config.setString("index", null, "paginationType", "NONE");
    return config;
  }
}
