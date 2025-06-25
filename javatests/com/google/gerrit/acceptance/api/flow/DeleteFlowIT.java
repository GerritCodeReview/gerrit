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

package com.google.gerrit.acceptance.api.flow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreationWithOneStage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowService;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.DeleteFlow} REST endpoint.
 */
public class DeleteFlowIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void deleteFlow() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithOneStage(accountCreator, project, changeId);
    Flow flow = flowService.createFlow(flowCreation);
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).delete();
      assertThat(flowService.getFlow(flow.key())).isEmpty();
    }
  }

  @Test
  public void deleteFlow_authenticationRequired() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithOneStage(accountCreator, project, changeId);
    Flow flow = flowService.createFlow(flowCreation);
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      AuthException exception =
          assertThrows(
              AuthException.class,
              () ->
                  gApi.changes()
                      .id(project.get(), changeId.get())
                      .flow(flow.key().uuid())
                      .delete());
      assertThat(exception).hasMessageThat().isEqualTo("Authentication required");
    }
  }

  @Test
  public void deleteFlow_permissionDenied() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithOneStage(accountCreator, project, changeId);
    Flow flow = testFlowService.createFlow(flowCreation);
    testFlowService.rejectFlowDeletion();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      assertThrows(
          AuthException.class,
          () -> gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).delete());
    }
  }
}
