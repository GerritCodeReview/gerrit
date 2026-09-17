// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.project.MigrateLabelFunctionsToSubmitRequirement.Status;
import com.google.gerrit.server.restapi.project.CreateLabel;
import com.google.gerrit.server.restapi.project.MigrateLabelFunctionsToSubmitRequirement;
import com.google.gerrit.server.restapi.project.MigrateLabelsInfo;
import com.google.gerrit.server.restapi.project.MigrateLabelsReviewInfo;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class MigrateLabelsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private CreateLabel createLabel;

  @Test
  public void migrateLabels_updatesProjectConfigDirectly() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    createLabel(testProject, "Foo", "MaxWithBlock");

    RestResponse response =
        adminRestSession.post("/projects/" + testProject.get() + "/migrate-labels", null);
    response.assertOK();

    MigrateLabelsInfo info = newGson().fromJson(response.getReader(), MigrateLabelsInfo.class);
    assertThat(info.status).isEqualTo(Status.MIGRATED);

    assertLabelFunction(testProject, "Foo", "NoBlock");
    assertSubmitRequirement(
        testProject,
        "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN");
  }

  @Test
  public void migrateLabelsReviewEndpoint_createsReviewChange() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    createLabel(testProject, "Foo", "MaxWithBlock");

    RestResponse response =
        adminRestSession.post("/projects/" + testProject.get() + "/migrate-labels:review", null);
    response.assertOK();

    MigrateLabelsReviewInfo info =
        newGson().fromJson(response.getReader(), MigrateLabelsReviewInfo.class);
    assertThat(info.status).isEqualTo(Status.MIGRATED);
    assertThat(info.change).isNotNull();
    assertThat(info.change.subject).isEqualTo(MigrateLabelFunctionsToSubmitRequirement.COMMIT_MSG);

    assertLabelFunction(testProject, "Foo", "MaxWithBlock");
    assertSubmitRequirementDoesNotExist(testProject, "Foo");

    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(info.change.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getString("label", "Foo", "function")).isEqualTo("NoBlock");
    assertThat(config.getString("submit-requirement", "Foo", "submittableIf"))
        .isEqualTo("label:Foo=MAX AND -label:Foo=MIN");
  }

  private void createLabel(Project.NameKey project, String labelName, String function)
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = labelName;
    input.function = function;
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "No Score", "-1", "Looks Bad");
    createLabel.createLabelWithoutInputValidation(project, labelName, input);
  }

  private void assertLabelFunction(Project.NameKey project, String labelName, String function)
      throws Exception {
    LabelDefinitionInfo info = gApi.projects().name(project.get()).label(labelName).get();
    assertThat(info.function).isEqualTo(function);
  }

  private void assertSubmitRequirement(
      Project.NameKey project,
      String srName,
      String applicabilityExpression,
      String submittabilityExpression)
      throws Exception {
    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement(srName).get();
    assertThat(info.applicabilityExpression).isEqualTo(applicabilityExpression);
    assertThat(info.submittabilityExpression).isEqualTo(submittabilityExpression);
  }

  private void assertSubmitRequirementDoesNotExist(Project.NameKey project, String srName) {
    ResourceNotFoundException e =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).submitRequirement(srName).get());
    assertThat(e.getMessage()).isEqualTo("Submit requirement '" + srName + "' does not exist");
  }
}
