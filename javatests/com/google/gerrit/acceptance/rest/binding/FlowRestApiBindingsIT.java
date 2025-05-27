// Copyright (C) 2025 The Android Open Source Project
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

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;

/**
 * Tests for checking the bindings of the flow REST API.
 *
 * <p>These tests only verify that the flow REST endpoints are correctly bound, they do no test
 * the functionality of the flow REST endpoints.
 */
public class FlowRestApiBindingsIT extends AbstractDaemonTest {
	private static final ImmutableList<RestCall> CHANGE_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/flows"),
          RestCall.post("/changes/%s/flows"));
          
	@Test
  public void changeEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).edit().create();
    RestApiCallHelper.execute(adminRestSession, CHANGE_ENDPOINTS, changeId);
  }
}
