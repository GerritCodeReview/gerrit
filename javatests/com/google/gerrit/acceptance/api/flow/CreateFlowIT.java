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
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowInputWithNStages;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowInputWithOneStage;
import static com.google.gerrit.extensions.common.testing.FlowInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestChange;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.flow.FlowKey;
import com.google.gerrit.server.flow.FlowService;
import com.google.gerrit.server.restapi.flow.CreateFlow;
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
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () ->
                gApi.changes()
                    .id(changeIdentifier)
                    .createFlow(createTestFlowInputWithOneStage(accountCreator, changeIdentifier)));
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void createFlowWithoutStages_badRequest() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      flowInput.stageExpressions = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("at least one stage expression is required");

      flowInput.stageExpressions = ImmutableList.of();
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("at least one stage expression is required");
    }
  }

  @Test
  public void createFlowWithoutConditionInStageExpression_badRequest() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      Iterables.getOnlyElement(flowInput.stageExpressions).condition = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("condition in stage expression is required");

      Iterables.getOnlyElement(flowInput.stageExpressions).condition = "";
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("condition in stage expression is required");
    }
  }

  @Test
  public void cannotCreateFlowWithoutActionOnLastStageExpression() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      Iterables.getOnlyElement(flowInput.stageExpressions).action = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo("the last stage expression is required to have an action");
    }
  }

  @Test
  public void createFlowWithStageExpressionsThatDontHaveAnAction() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithNStages(accountCreator, changeIdentifier, 3);
      flowInput.stageExpressions.get(0).action = null;
      FlowInfo flowInfo = gApi.changes().id(changeIdentifier).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);

      flowInput.stageExpressions.get(1).action = null;
      flowInfo = gApi.changes().id(changeIdentifier).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
    }
  }

  @Test
  public void createFlowWithoutNameInAction_badRequest() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      Iterables.getOnlyElement(flowInput.stageExpressions).action.name = null;
      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("name in action is required");

      Iterables.getOnlyElement(flowInput.stageExpressions).action.name = "";
      exception =
          assertThrows(
              BadRequestException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("name in action is required");
    }
  }

  @Test
  public void createFlowWithSingleStage() throws Exception {
    TestChange change = changeOperations.newChange().createAndGet();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, change.id());
      FlowInfo flowInfo = gApi.changes().id(change.id()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(
              flowService.getFlow(
                  FlowKey.create(change.project(), change.numericChangeId(), flowInfo.uuid)))
          .isPresent();
    }
  }

  @Test
  public void createFlowWithMultipleStage() throws Exception {
    TestChange change = changeOperations.newChange().createAndGet();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithMultipleStages(accountCreator, change.id());
      FlowInfo flowInfo = gApi.changes().id(change.id()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(
              flowService.getFlow(
                  FlowKey.create(change.project(), change.numericChangeId(), flowInfo.uuid)))
          .isPresent();
    }
  }

  @Test
  public void createFlowWithoutParametersInAction() throws Exception {
    TestChange change = changeOperations.newChange().createAndGet();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      Instant beforeInstant = Instant.now();
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, change.id());
      Iterables.getOnlyElement(flowInput.stageExpressions).action.parameters = null;
      FlowInfo flowInfo = gApi.changes().id(change.id()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(
              flowService.getFlow(
                  FlowKey.create(change.project(), change.numericChangeId(), flowInfo.uuid)))
          .isPresent();

      Iterables.getOnlyElement(flowInput.stageExpressions).action.parameters = ImmutableList.of();
      flowInfo = gApi.changes().id(change.id()).createFlow(flowInput);
      assertFlowInfoForNewlyCreatedFlow(flowInfo, flowInput, admin, beforeInstant);
      assertThat(
              flowService.getFlow(
                  FlowKey.create(change.project(), change.numericChangeId(), flowInfo.uuid)))
          .isPresent();
    }
  }

  @Test
  public void createFlowWithInvalidCondtition_badRequest() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      FlowInput flowInput =
          createTestFlowInputWithInvalidCondition(accountCreator, changeIdentifier);
      assertThrows(
          BadRequestException.class,
          () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
    }
  }

  @Test
  public void createFlow_authenticationRequired() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    requestScopeOperations.setApiUserAnonymous();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.rejectFlowCreation();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      AuthException exception =
          assertThrows(
              AuthException.class, () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception).hasMessageThat().isEqualTo("Authentication required");
    }
  }

  @Test
  public void createFlow_callerMustBeUploader() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    requestScopeOperations.setApiUser(accountCreator.user2().id());
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.rejectFlowCreation();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      AuthException exception =
          assertThrows(
              AuthException.class, () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              "Only latest uploader can create a flow, because actions are executed on behalf of"
                  + " uploader.");
    }
  }

  @Test
  public void createFlow_permissionDenied() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.rejectFlowCreation();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      assertThrows(
          AuthException.class, () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
    }
  }

  @Test
  public void numberOfFlowsPerChangeIsLimitedByDefault() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      for (int i = 1; i <= CreateFlow.DEFAULT_MAX_FLOWS_PER_CHANGE; i++) {
        gApi.changes().id(changeIdentifier).createFlow(flowInput);
      }

      ResourceConflictException exception =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Too many flows (max %s flow allowed per change)",
                  CreateFlow.DEFAULT_MAX_FLOWS_PER_CHANGE));
    }
  }

  @Test
  @GerritConfig(name = "flows.maxPerChange", value = "50")
  public void numberOfFlowsPerChangeIsLimitedByConfiguration() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      for (int i = 1; i <= 50; i++) {
        gApi.changes().id(changeIdentifier).createFlow(flowInput);
      }

      ResourceConflictException exception =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(changeIdentifier).createFlow(flowInput));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("Too many flows (max %s flow allowed per change)", 50));
    }
  }

  @Test
  @GerritConfig(name = "flows.maxPerChange", value = "0")
  public void numberOfFlowsPerChangeIsUnlimited() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      FlowInput flowInput = createTestFlowInputWithOneStage(accountCreator, changeIdentifier);
      for (int i = 1; i <= CreateFlow.DEFAULT_MAX_FLOWS_PER_CHANGE + 10; i++) {
        gApi.changes().id(changeIdentifier).createFlow(flowInput);
      }
    }
  }

  private static void assertFlowInfoForNewlyCreatedFlow(
      FlowInfo flowInfo, FlowInput flowInput, TestAccount owner, Instant beforeInstant) {
    assertThat(flowInfo).matches(flowInput);
    assertThat(flowInfo).hasOwnerThat().hasAccountIdThat().isEqualTo(owner.id());
    assertThat(flowInfo).hasCreatedThat().isAtLeast(beforeInstant);
  }
}
