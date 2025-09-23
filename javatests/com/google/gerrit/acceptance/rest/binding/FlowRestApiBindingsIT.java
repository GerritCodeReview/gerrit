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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowAction;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowExpression;
import com.google.gerrit.server.flow.FlowService;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Tests for checking the bindings of the flow REST API.
 *
 * <p>These tests only verify that the flow REST endpoints are correctly bound, they do no test the
 * functionality of the flow REST endpoints.
 */
public class FlowRestApiBindingsIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  private static final ImmutableList<RestCall> CHANGE_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/flows"),
          RestCall.get("/changes/%s/is-flows-enabled"),
          RestCall.post("/changes/%s/flows"));

  private static final ImmutableList<RestCall> FLOW_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/flows/%s"),
          // Deletion of flow must be tested last
          RestCall.delete("/changes/%s/flows/%s"));

  @Test
  public void changeEndpoints() throws Exception {
    try (Registration registration =
        extensionRegistry.newRegistration().set(new TestExtensions.TestFlowService())) {
      String changeId = createChange().getChangeId();
      gApi.changes().id(changeId).edit().create();
      RestApiCallHelper.execute(adminRestSession, CHANGE_ENDPOINTS, changeId);
    }
  }

  @Test
  public void flowEndpoints() throws Exception {
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      PushOneCommit.Result r = createChange();
      gApi.changes().id(r.getChangeId()).edit().create();
      Flow flow =
          flowService.createFlow(
              FlowCreation.builder()
                  .projectName(project)
                  .changeId(r.getChange().getId())
                  .ownerId(r.getChange().change().getOwner())
                  .addStageExpression(
                      FlowExpression.builder()
                          .condition(
                              String.format(
                                  "com.google.gerrit[change:%s label:Verified+1]",
                                  r.getChange().getId()))
                          .action(
                              FlowAction.builder()
                                  .name("AddReviewer")
                                  .addParameter(user.email())
                                  .build())
                          .build())
                  .build());
      RestApiCallHelper.execute(
          adminRestSession, FLOW_ENDPOINTS, r.getChangeId(), flow.key().uuid());
    }
  }
}
