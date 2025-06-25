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
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowInputWithInvalidCondition;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowInputWithMultipleStages;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowInputWithOneStage;
import static com.google.gerrit.extensions.common.testing.FlowInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.flow.FlowKey;
import com.google.gerrit.server.flow.FlowService;
import com.google.inject.Inject;
import java.time.Instant;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.CreateFlow} REST endpoint.
 */
public class CreateFlowIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void createFlowIfNoFlowServiceIsBound_methodNotAllowed() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () ->
                gApi.changes()
                    .id(project.get(), changeId.get())
                    .createFlow(createTestFlowInputWithOneStage(accountCreator, changeId)));
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void createFlowWithoutStages_badRequest() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      flowInput.stageExpressions = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("at least one stage expression is required");

      flowInput.stageExpressions = ImmutableList.of();
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("at least one stage expression is required");
    }
  }

  @Test
  public void createFlowWithoutConditionInStageExpression_badRequest() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      Iterables.getOnlyElement(flowInput.stageExpressions).condition = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("condition in stage expression is required");

      Iterables.getOnlyElement(flowInput.stageExpressions).condition = "";
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("condition in stage expression is required");
    }
  }

  @Test
  public void createFlowWithoutActionInStageExpression_badRequest() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      Iterables.getOnlyElement(flowInput.stageExpressions).action = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("action in stage expression is required");
    }
  }

  @Test
  public void createFlowWithoutNameInAction_badRequest() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      Iterables.getOnlyElement(flowInput.stageExpressions).action.name = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("name in action is required");

      Iterables.getOnlyElement(flowInput.stageExpressions).action.name = "";
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("name in action is required");
    }
  }

  @Test
  public void createFlowWithSingleStage() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      FlowInfo flowInfo = gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(flowService.getFlow(FlowKey.create(project, changeId, flowInfo.uuid))).isPresent();
    }
  }

  @Test
  public void createFlowWithMultipleStage() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithMultipleStages(accountCreator, changeId);
      FlowInfo flowInfo = gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(flowService.getFlow(FlowKey.create(project, changeId, flowInfo.uuid))).isPresent();
    }
  }

  @Test
  public void createFlowWithoutParametersInAction() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      Iterables.getOnlyElement(flowInput.stageExpressions).action.parameters = null;
      FlowInfo flowInfo = gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(flowService.getFlow(FlowKey.create(project, changeId, flowInfo.uuid))).isPresent();

      Iterables.getOnlyElement(flowInput.stageExpressions).action.parameters = ImmutableMap.of();
      flowInfo = gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(flowService.getFlow(FlowKey.create(project, changeId, flowInfo.uuid))).isPresent();
    }
  }

  @Test
  public void createFlowWithInvalidCondtition_badRequest() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithInvalidCondition(accountCreator, changeId);
      assertThrows(
          BadRequestException.class,
          () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
    }
  }

  @Test
  public void createFlow_authenticationRequired() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.rejectFlowCreation();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      AuthException exception =
          assertThrows(
              AuthException.class,
              () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("Authentication required");
    }
  }

  @Test
  public void createFlow_permissionDenied() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.rejectFlowCreation();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeId);
      assertThrows(
          AuthException.class,
          () -> gApi.changes().id(project.get(), changeId.get()).createFlow(flowInput));
    }
  }

  private static void assertFlowInfoForNewlyCreatedFlow(
      FlowInfo flowInfo, FlowInput flowInput, TestAccount owner, Instant beforeInstant) {
    assertThat(flowInfo).matches(flowInput);
    assertThat(flowInfo).hasOwnerThat().hasAccountIdThat().isEqualTo(owner.id());
    assertThat(flowInfo).hasCreatedThat().isAtLeast(beforeInstant);
  }
}
