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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.flow.FlowService;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

/**
 * Integration tests for the {@link com.google.gerrit.server.restapi.flow.ListFlows} REST endpoint.
 */
public class ListFlowsIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void listFlowsIfNoFlowServiceIsBound_methodNotAllowed() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.changes().id(project.get(), changeId.get()).flows());
    assertThat(exception).hasMessageThat().isEqualTo("No FlowService bound.");
  }

  @Test
  public void listFlow_noFlowsExist() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    FlowService flowService = new TestExtensions.TestFlowService();
    try (Registration registration = extensionRegistry.newRegistration().set(flowService)) {
      List<FlowInfo> flows = gApi.changes().id(project.get(), changeId.get()).flows();
      assertThat(flows).isEmpty();
    }
  }
}
