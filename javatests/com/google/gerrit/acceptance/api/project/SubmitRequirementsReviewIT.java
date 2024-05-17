// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.BatchSubmitRequirementInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SubmitRequirementsReviewIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void createSubmitRequirementsChangeWithDefaultMessage() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    SubmitRequirementInput fooSR = new SubmitRequirementInput();
    fooSR.name = "Foo";
    fooSR.description = "SR description";
    fooSR.applicabilityExpression = "topic:foo";
    fooSR.submittabilityExpression = "label:code-review=+2";
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(fooSR);

    ChangeInfo changeInfo = gApi.projects().name(testProject.get()).submitRequirementsReview(input);

    assertThat(changeInfo.subject).isEqualTo("Review submit requirements change");
    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getString("submit-requirement", "Foo", "description"))
        .isEqualTo("SR description");
    assertThat(config.getString("submit-requirement", "Foo", "applicableIf"))
        .isEqualTo("topic:foo");
    assertThat(config.getString("submit-requirement", "Foo", "submittableIf"))
        .isEqualTo("label:code-review=+2");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_batchUpdateRejected() {
    Project.NameKey testProject = projectOperations.newProject().create();
    SubmitRequirementInput fooSR = new SubmitRequirementInput();
    fooSR.name = "Foo";
    fooSR.description = "SR description";
    fooSR.applicabilityExpression = "topic:foo";
    fooSR.submittabilityExpression = "label:code-review=+2";
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(fooSR);

    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.projects().name(testProject.get()).submitRequirements(input));
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }

  @Test
  public void createSubmitRequirementsChangeWithCustomMessage() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    SubmitRequirementInput fooSR = new SubmitRequirementInput();
    fooSR.name = "Foo";
    fooSR.description = "SR description";
    fooSR.applicabilityExpression = "topic:foo";
    fooSR.submittabilityExpression = "label:code-review=+2";
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(fooSR);
    String customMessage = "test custom message";
    input.commitMessage = customMessage;

    ChangeInfo changeInfo = gApi.projects().name(testProject.get()).submitRequirementsReview(input);
    assertThat(changeInfo.subject).isEqualTo(customMessage);

    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getString("submit-requirement", "Foo", "description"))
        .isEqualTo("SR description");
    assertThat(config.getString("submit-requirement", "Foo", "applicableIf"))
        .isEqualTo("topic:foo");
    assertThat(config.getString("submit-requirement", "Foo", "submittableIf"))
        .isEqualTo("label:code-review=+2");
  }
}
