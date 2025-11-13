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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.FlowActionTypeInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.flow.FlowActionType;
import com.google.gerrit.server.flow.FlowService;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.ListActions} REST
 * endpoint.
 */
public class ListActionsIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void listActionsIfNoFlowServiceIsBound_methodNotAllowed() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.changes().id(changeIdentifier).flowsActions());
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void listActions_noActionsExist() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      List<FlowActionTypeInfo> actions = gApi.changes().id(changeIdentifier).flowsActions();
      assertThat(actions).isEmpty();
    }
  }

  @Test
  public void listActions() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();

    TestExtensions.TestFlowService testFlowService = new TestExtensions.TestFlowService();
    testFlowService.setActions(ImmutableList.of(action("action1"), action("action2")));

    try (Registration registration = extensionRegistry.newRegistration().set(testFlowService)) {
      List<FlowActionTypeInfo> actions = gApi.changes().id(changeIdentifier).flowsActions();
      assertThat(actions).hasSize(2);
      assertThat(actions.get(0).name).isEqualTo("action1");
      assertThat(actions.get(1).name).isEqualTo("action2");
    }
  }

  private static FlowActionType action(String name) {
    return FlowActionType.builder(name).build();
  }
}
