// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.index.IndexType;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;

/**
 * Tests for a defaulted custom index configuration. This unknown type is the opposite of {@link
 * IndexType#getKnownTypes()}.
 */
public class CustomIndexIT extends AbstractIndexTests {

  @ConfigSuite.Default
  public static Config customIndexType() {
    Config config = new Config();
    config.setString("index", null, "type", "custom");
    return config;
  }
}
