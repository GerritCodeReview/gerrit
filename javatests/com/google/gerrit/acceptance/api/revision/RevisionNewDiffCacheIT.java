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

package com.google.gerrit.acceptance.api.revision;

import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;

/**
 * Runs the {@link RevisionDiffIT} tests with the new diff cache. This is temporary until the new
 * diff cache is fully deployed. The new diff cache will become the default in the future.
 */
public class RevisionNewDiffCacheIT extends RevisionDiffIT {
  @ConfigSuite.Default
  public static Config newDiffCacheConfig() {
    Config config = new Config();
    config.setBoolean("cache", "diff_cache", "useNewDiffCache", true);
    return config;
  }
}
