// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class UseGerritConfigAnnotationTest extends AbstractDaemonTest {

  @Inject @GerritServerConfig Config serverConfig;

  @Test
  @GerritConfig(name = "x.y", value = "z")
  public void testOne() {
    assertThat(serverConfig.getString("x", null, "y")).isEqualTo("z");
  }

  @Test
  @GerritConfig(name = "x.y", value = "z")
  @GerritConfig(name = "a.b", value = "c")
  public void testMultiple() {
    assertThat(serverConfig.getString("x", null, "y")).isEqualTo("z");
    assertThat(serverConfig.getString("a", null, "b")).isEqualTo("c");
  }
}
