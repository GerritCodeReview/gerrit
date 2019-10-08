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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.acceptance.rest.util.RestApiCallHelper.execute;
import static com.google.gerrit.acceptance.rest.util.RestCall.Method.GET;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.testsuite.config.GerritConfig;
import org.junit.Test;

/**
 * Tests for checking the bindings of the root REST API.
 *
 * <p>These tests only verify that the root REST endpoints are correctly bound, they do no test the
 * functionality of the root REST endpoints.
 */
public class RootCollectionsRestApiBindingsIT extends AbstractDaemonTest {
  /** Root REST endpoints to be tested, the URLs contain no placeholders. */
  private static final ImmutableList<RestCall> ROOT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/access/"),
          RestCall.get("/accounts/"),
          RestCall.put("/accounts/new-account"),
          RestCall.builder(GET, "/config/")
              // GET /config/ is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),
          RestCall.get("/changes/"),
          RestCall.post("/changes/"),
          RestCall.get("/groups/"),
          RestCall.put("/groups/new-group"),
          RestCall.get("/plugins/"),
          RestCall.put("/plugins/new-plugin"),
          RestCall.get("/projects/"),
          RestCall.put("/projects/new-project"));

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void rootEndpoints() throws Exception {
    execute(adminRestSession, ROOT_ENDPOINTS);
  }
}
