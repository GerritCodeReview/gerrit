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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreation;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreationWithMultipleStages;
import static com.google.gerrit.acceptance.api.flow.FlowTestUtil.createTestFlowCreationWithOneStage;
import static com.google.gerrit.extensions.common.testing.FlowInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestChange;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowService;
import com.google.gerrit.server.flow.FlowStageEvaluationStatus.State;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.ListFlows} REST endpoint.
 */
public class ListFlowsIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void listFlowsIfNoFlowServiceIsBound_methodNotAllowed() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class, () -> gApi.changes().id(changeIdentifier).flows());
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void listFlow_noFlowsExist() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      List<FlowInfo> flows = gApi.changes().id(changeIdentifier).flows();
      assertThat(flows).isEmpty();
    }
  }

  @Test
  public void listFlows() throws Exception {
    TestChange change = changeOperations.newChange().createAndGet();
    TestFlowService testFlowService = new TestExtensions.TestFlowService();
    Flow flow1 =
        testFlowService.createFlow(createTestFlowCreationWithOneStage(accountCreator, change));
    Flow flow2 =
        testFlowService.createFlow(createTestFlowCreationWithOneStage(accountCreator, change));
    flow2 =
        testFlowService.evaluate(
            flow2.key(), ImmutableList.of(State.DONE), ImmutableList.of(Optional.of("done")));
    Flow flow3 =
        testFlowService.createFlow(
            createTestFlowCreationWithMultipleStages(accountCreator, change));
    Flow flow4 = testFlowService.createFlow(createTestFlowCreation(accountCreator, change, 3));
    flow4 =
        testFlowService.evaluate(
            flow4.key(),
            ImmutableList.of(State.DONE, State.FAILED, State.TERMINATED),
            ImmutableList.of(
                Optional.empty(),
                Optional.of("error"),
                Optional.of("terminated because previous stage failed")));
    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      List<FlowInfo> flows = gApi.changes().id(change.id()).flows();
      ImmutableMap<String, FlowInfo> flowInfosByUuid =
          flows.stream().collect(toImmutableMap(flowInfo -> flowInfo.uuid, Function.identity()));
      assertThat(flowInfosByUuid.keySet())
          .containsExactly(
              flow1.key().uuid(), flow2.key().uuid(), flow3.key().uuid(), flow4.key().uuid());
      assertThat(flowInfosByUuid.get(flow1.key().uuid())).matches(flow1);
      assertThat(flowInfosByUuid.get(flow2.key().uuid())).matches(flow2);
      assertThat(flowInfosByUuid.get(flow3.key().uuid())).matches(flow3);
      assertThat(flowInfosByUuid.get(flow4.key().uuid())).matches(flow4);
    }
  }
}
