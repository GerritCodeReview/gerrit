// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsAPIIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void createSubmitRequirement() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicableIf = "project:foo AND branch:refs/heads/main";
    input.submittableIf = "label:code-review=+2";
    input.overrideIf = "label:build-cop-override=+1";
    input.canOverrideInChildProjects = true;

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.applicabilityExpression).isEqualTo(input.applicableIf);
    assertThat(info.applicabilityExpression).isEqualTo(input.applicableIf);
    assertThat(info.submittabilityExpression).isEqualTo(input.submittableIf);
    assertThat(info.overrideExpression).isEqualTo(input.overrideIf);
    assertThat(info.canOverrideInChildProjects).isEqualTo(true);
  }

  @Test
  public void createSRWithEmptyApplicabilityExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittableIf = "label:code-review=+2";
    input.overrideIf = "label:build-cop-override=+1";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.applicabilityExpression).isNull();
  }

  @Test
  public void createSRWithEmptyOverrideExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicableIf = "project:foo AND branch:refs/heads/main";
    input.submittableIf = "label:code-review=+2";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.overrideExpression).isNull();
  }

  @Test
  public void canOverrideInChildProjectsDefaultsToFalse() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittableIf = "label:code-review=+2";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.canOverrideInChildProjects).isEqualTo(false);
  }

  @Test
  public void cannotCreateSRAsAnonymousUser() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicableIf = "project:foo AND branch:refs/heads/main";
    input.submittableIf = "label:code-review=+2";
    input.overrideIf = "label:build-cop-override=+1";

    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .create(new SubmitRequirementInput()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void cannotCreateSRtIfNameInInputDoesNotMatchResource() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittableIf = "label:code-review=+2";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("other-requirement")
                    .create(input)
                    .get());
    assertThat(thrown).hasMessageThat().isEqualTo("name in input must match name in URL");
  }

  @Test
  public void cannotCreateSRWithInvalidName() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "wrong$%";
    input.submittableIf = "label:code-review=+2";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("wrong$%")
                    .create(input)
                    .get());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "Illegal submit requirement name \"wrong$%\". "
                + "Name can only consist of alphanumeric characters and -");
  }

  @Test
  public void cannotCreateSRWithEmptySubmittableIf() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicableIf = "project:foo AND branch:refs/heads/main";
    input.overrideIf = "label:build-cop-override=+1";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .create(input)
                    .get());

    assertThat(thrown).hasMessageThat().isEqualTo("submittability_expression is required");
  }

  @Test
  public void cannotCreateSRWithInvalidSubmittableIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittableIf = "invalid_field:invalid_value";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .create(input)
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid submit requirement input: "
                + "[Invalid project configuration,   "
                + "project.config: Expression 'invalid_field:invalid_value' of "
                + "submit requirement 'code-review' "
                + "(parameter submit-requirement.code-review.submittableIf) is invalid: "
                + "Unsupported operator invalid_field:invalid_value]");
  }

  @Test
  public void cannotCreateSRWithInvalidOverrideIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittableIf = "label:Code-Review=+2";
    input.overrideIf = "invalid_field:invalid_value";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .create(input)
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid submit requirement input: "
                + "[Invalid project configuration,   "
                + "project.config: Expression 'invalid_field:invalid_value' of "
                + "submit requirement 'code-review' "
                + "(parameter submit-requirement.code-review.overrideIf) is invalid: "
                + "Unsupported operator invalid_field:invalid_value]");
  }

  @Test
  public void cannotCreateSRWithInvalidApplicableIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicableIf = "invalid_field:invalid_value";
    input.submittableIf = "label:Code-Review=+2";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .create(input)
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid submit requirement input: "
                + "[Invalid project configuration,   "
                + "project.config: Expression 'invalid_field:invalid_value' of "
                + "submit requirement 'code-review' "
                + "(parameter submit-requirement.code-review.applicableIf) is invalid: "
                + "Unsupported operator invalid_field:invalid_value]");
  }
}
