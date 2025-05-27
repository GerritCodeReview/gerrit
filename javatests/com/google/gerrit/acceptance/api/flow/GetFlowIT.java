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
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreation;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreationWithMultipleStages;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreationWithOneStage;
import static com.google.gerrit.extensions.common.testing.FlowInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowService;
import com.google.gerrit.server.flow.FlowStage;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.GetFlow} REST endpoint.
 */
public class GetFlowIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void getFlowIfNoFlowServiceIsBound_methodNotAllowed() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.changes().id(project.get(), changeId.get()).flow("flow-uuid"));
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void getNonExistingFlow_notFound() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      ResourceNotFoundException exception =
          assertThrows(
              ResourceNotFoundException.class,
              () ->
                  gApi.changes().id(project.get(), changeId.get()).flow("non-existing-flow-uuid"));
      assertThat(exception).hasMessageThat().isEqualTo("Flow non-existing-flow-uuid not found.");
    }
  }

  @Test
  public void getFlowWithSingleStage_notYetEvaluated() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    FlowService flowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithOneStage(accountCreator, project, changeId);
    Flow flow = flowService.createFlow(flowCreation);
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInfo flowInfo =
          gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).get();
      assertThat(flowInfo).matches(flow);
    }
  }

  @Test
  public void getFlowWithSingleStage_evaluated() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithOneStage(accountCreator, project, changeId);
    Flow flow = testFlowService.createFlow(flowCreation);
    flow =
        testFlowService.evaluate(
            flow.key(),
            ImmutableList.of(FlowStage.Status.DONE),
            ImmutableList.of(Optional.of("done")));
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInfo flowInfo =
          gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).get();
      assertThat(flowInfo).matches(flow);
    }
  }

  @Test
  public void getFlowWithMultipleStages_notYetEvaluated() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    FlowService flowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation =
        createTestFlowCreationWithMultipleStages(accountCreator, project, changeId);
    Flow flow = flowService.createFlow(flowCreation);
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInfo flowInfo =
          gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).get();
      assertThat(flowInfo).matches(flow);
    }
  }

  @Test
  public void getFlowWithMultipleStages_evaluated() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    FlowCreation flowCreation = createTestFlowCreation(accountCreator, project, changeId, 3);
    Flow flow = testFlowService.createFlow(flowCreation);
    flow =
        testFlowService.evaluate(
            flow.key(),
            ImmutableList.of(
                FlowStage.Status.DONE, FlowStage.Status.FAILED, FlowStage.Status.TERMINATED),
            ImmutableList.of(
                Optional.empty(),
                Optional.of("error"),
                Optional.of("terminated because previous stage failed")));
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInfo flowInfo =
          gApi.changes().id(project.get(), changeId.get()).flow(flow.key().uuid()).get();
      assertThat(flowInfo).matches(flow);
    }
  }
}
