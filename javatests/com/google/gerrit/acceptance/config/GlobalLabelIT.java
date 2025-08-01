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

package com.google.gerrit.acceptance.config;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import org.junit.Test;

public class GlobalLabelIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void globalLabelAppliesToAllProjects() throws Exception {
    LabelType globalLabelType =
        LabelType.builder(
                "Global-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Approved"),
                    LabelValue.create((short) 0, "No vote"),
                    LabelValue.create((short) -1, "Rejected")))
            .setDescription("A global label")
            .setFunction(LabelFunction.NO_OP)
            .build();
    try (Registration registration = extensionRegistry.newRegistration().add(globalLabelType)) {
      testThatGlobalLabelAppliesToProject(allProjects, globalLabelType);
      testThatGlobalLabelAppliesToProject(project, globalLabelType);

      Project.NameKey otherProject = projectOperations.newProject().create();
      testThatGlobalLabelAppliesToProject(otherProject, globalLabelType);
    }
  }

  private void testThatGlobalLabelAppliesToProject(
      Project.NameKey project, LabelType globalLabelType) throws RestApiException {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().project(project).create();

    ChangeInfo changeInfo = gApi.changes().id(changeIdentifier).get();
    assertThat(changeInfo.labels).containsKey(globalLabelType.getName());

    LabelInfo globalLabelInfo = changeInfo.labels.get(globalLabelType.getName());
    assertThat(globalLabelInfo.description).isEqualTo(globalLabelType.getDescription().get());
    assertThat(globalLabelInfo.values)
        .containsExactlyEntriesIn(
            globalLabelType.getValues().stream()
                .collect(
                    toImmutableMap(
                        labelValue -> LabelValue.formatValue(labelValue.getValue()),
                        LabelValue::getText)));
  }

  @Test
  public void globalLabelCannotBeOverriddenIfOverrideIsNotAllowed() throws Exception {
    LabelType globalLabelType =
        LabelType.builder(
                "Global-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Approved"),
                    LabelValue.create((short) 0, "No vote"),
                    LabelValue.create((short) -1, "Rejected")))
            .setDescription("A global label")
            .setFunction(LabelFunction.NO_OP)
            .setCanOverride(false)
            .build();

    // Try overriding the label with different values.
    configLabel(
        project,
        globalLabelType.getName(),
        LabelFunction.NO_OP,
        LabelValue.create((short) 2, "Approved"),
        LabelValue.create((short) 1, "LGTM"),
        LabelValue.create((short) 0, "No vote"),
        LabelValue.create((short) -1, "Rejected"),
        LabelValue.create((short) -2, "Veto"));

    try (Registration registration = extensionRegistry.newRegistration().add(globalLabelType)) {
      ChangeIdentifier changeIdentifier = changeOperations.newChange().project(project).create();

      ChangeInfo changeInfo = gApi.changes().id(changeIdentifier).get();
      assertThat(changeInfo.labels).containsKey(globalLabelType.getName());

      // Assert that the global label has not been overridden.
      LabelInfo globalLabelInfo = changeInfo.labels.get(globalLabelType.getName());
      assertThat(globalLabelInfo.values)
          .containsExactlyEntriesIn(
              globalLabelType.getValues().stream()
                  .collect(
                      toImmutableMap(
                          labelValue -> LabelValue.formatValue(labelValue.getValue()),
                          LabelValue::getText)));
    }
  }

  @Test
  public void globalLabelCannotBeRemovedIfOverrideIsNotAllowed() throws Exception {
    LabelType globalLabelType =
        LabelType.builder(
                "Global-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Approved"),
                    LabelValue.create((short) 0, "No vote"),
                    LabelValue.create((short) -1, "Rejected")))
            .setDescription("A global label")
            .setFunction(LabelFunction.NO_OP)
            .setCanOverride(false)
            .build();

    // Try removing the label (a label without values overrides an inherited label).
    configLabel(project, globalLabelType.getName(), LabelFunction.NO_OP);

    try (Registration registration = extensionRegistry.newRegistration().add(globalLabelType)) {
      ChangeIdentifier changeIdentifier = changeOperations.newChange().project(project).create();

      ChangeInfo changeInfo = gApi.changes().id(changeIdentifier).get();
      assertThat(changeInfo.labels).containsKey(globalLabelType.getName());

      // Assert that the global label has not been removed.
      LabelInfo globalLabelInfo = changeInfo.labels.get(globalLabelType.getName());
      assertThat(globalLabelInfo.values)
          .containsExactlyEntriesIn(
              globalLabelType.getValues().stream()
                  .collect(
                      toImmutableMap(
                          labelValue -> LabelValue.formatValue(labelValue.getValue()),
                          LabelValue::getText)));
    }
  }

  @Test
  public void globalLabelCanBeOverriddenIfOverrideIsAllowed() throws Exception {
    LabelType globalLabelType =
        LabelType.builder(
                "Global-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Approved"),
                    LabelValue.create((short) 0, "No vote"),
                    LabelValue.create((short) -1, "Rejected")))
            .setDescription("A global label")
            .setFunction(LabelFunction.NO_OP)
            .setCanOverride(true)
            .build();

    // Override the label with different values.
    configLabel(
        project,
        globalLabelType.getName(),
        LabelFunction.NO_OP,
        LabelValue.create((short) 2, "Approved"),
        LabelValue.create((short) 1, "LGTM"),
        LabelValue.create((short) 0, "No vote"),
        LabelValue.create((short) -1, "Rejected"),
        LabelValue.create((short) -2, "Veto"));

    try (Registration registration = extensionRegistry.newRegistration().add(globalLabelType)) {
      ChangeIdentifier changeIdentifier = changeOperations.newChange().project(project).create();

      ChangeInfo changeInfo = gApi.changes().id(changeIdentifier).get();
      assertThat(changeInfo.labels).containsKey(globalLabelType.getName());

      // Assert that the global label has been overridden.
      LabelInfo globalLabelInfo = changeInfo.labels.get(globalLabelType.getName());
      assertThat(globalLabelInfo.values)
          .containsExactly(
              "+2", "Approved", "+1", "LGTM", " 0", "No vote", "-1", "Rejected", "-2", "Veto");
    }
  }

  @Test
  public void globalLabelCanBeRemovedIfOverrideIsAllowed() throws Exception {
    LabelType globalLabelType =
        LabelType.builder(
                "Global-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Approved"),
                    LabelValue.create((short) 0, "No vote"),
                    LabelValue.create((short) -1, "Rejected")))
            .setDescription("A global label")
            .setFunction(LabelFunction.NO_OP)
            .setCanOverride(true)
            .build();

    // Remove the label (a label without values overrides an inherited label).
    configLabel(project, globalLabelType.getName(), LabelFunction.NO_OP);

    try (Registration registration = extensionRegistry.newRegistration().add(globalLabelType)) {
      ChangeIdentifier changeIdentifier = changeOperations.newChange().project(project).create();

      // Assert that the global label has been removed.
      ChangeInfo changeInfo = gApi.changes().id(changeIdentifier).get();
      assertThat(changeInfo.labels).doesNotContainKey(globalLabelType.getName());
    }
  }
}
