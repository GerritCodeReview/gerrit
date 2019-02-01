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

package com.google.gerrit.acceptance.rest.binding;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class CheckersRestApiBindingsIT extends AbstractDaemonTest {
  private static final ImmutableList<RestCall> CHECKER_ENDPOINTS =
      ImmutableList.of(RestCall.get("/checkers/%s"), RestCall.post("/checkers/%s"));

  @Inject private CheckerOperations checkerOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("checks", "api", "enabled", true);
    return cfg;
  }

  @Test
  public void checkerEndpoints() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    RestApiCallHelper.execute(adminRestSession, CHECKER_ENDPOINTS, checkerUuid);
  }
}
