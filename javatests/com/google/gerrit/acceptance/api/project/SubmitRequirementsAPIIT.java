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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsAPIIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void createSubmitRequirement() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicability_expression = "project:foo AND branch:refs/heads/main";
    input.submittability_expression = "label:code-review=+2";
    input.override_expression = "label:build-cop-override=+1";
    input.allowOverrideInChildProjects = true;

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.applicabilityExpression).isEqualTo(input.applicability_expression);
    assertThat(info.applicabilityExpression).isEqualTo(input.applicability_expression);
    assertThat(info.submittabilityExpression).isEqualTo(input.submittability_expression);
    assertThat(info.overrideExpression).isEqualTo(input.override_expression);
    assertThat(info.canOverrideInChildProjects).isEqualTo(true);
  }

  @Test
  public void createSRWithEmptyApplicabilityExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittability_expression = "label:code-review=+2";
    input.override_expression = "label:build-cop-override=+1";

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
    input.applicability_expression = "project:foo AND branch:refs/heads/main";
    input.submittability_expression = "label:code-review=+2";

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
    input.submittability_expression = "label:code-review=+2";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.canOverrideInChildProjects).isEqualTo(false);
  }

  @Test
  public void cannotCreateSRAsAnonymousUser() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicability_expression = "project:foo AND branch:refs/heads/main";
    input.submittability_expression = "label:code-review=+2";
    input.override_expression = "label:build-cop-override=+1";

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
    input.submittability_expression = "label:code-review=+2";

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
    input.submittability_expression = "label:code-review=+2";

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
    input.applicability_expression = "project:foo AND branch:refs/heads/main";
    input.override_expression = "label:build-cop-override=+1";

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
    input.submittability_expression = "invalid_field:invalid_value";
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
    input.submittability_expression = "label:Code-Review=+2";
    input.override_expression = "invalid_field:invalid_value";
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
    input.applicability_expression = "invalid_field:invalid_value";
    input.submittability_expression = "label:Code-Review=+2";
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

  @Test
  public void cannotListSRsAsAnonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirements().get());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void cannotListSRsWithoutReadPermissionsToRefsMetaConfig() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirements().get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void listSRs() throws Exception {
    createSubmitRequirement("sr-1");
    createSubmitRequirement("sr-2");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().get();

    assertThat(names(infos)).containsExactly("sr-1", "sr-2");
  }

  @Test
  public void listSRsWithInheritance() throws Exception {
    createSubmitRequirement(allProjects.get(), "base-sr");
    createSubmitRequirement(project.get(), "sr-1");
    createSubmitRequirement(project.get(), "sr-2");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().withInherited(false).get();

    assertThat(names(infos)).containsExactly("sr-1", "sr-2");

    infos = gApi.projects().name(project.get()).submitRequirements().withInherited(true).get();

    assertThat(names(infos)).containsExactly("base-sr", "sr-1", "sr-2");
  }

  private SubmitRequirementInfo createSubmitRequirement(String srName) throws RestApiException {
    return createSubmitRequirement(project.get(), srName);
  }

  private SubmitRequirementInfo createSubmitRequirement(String project, String srName)
      throws RestApiException {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = srName;
    input.submittableIf = "label:dummy=+2";

    return gApi.projects().name(project).submitRequirement(srName).create(input).get();
  }

  private List<String> names(List<SubmitRequirementInfo> infos) {
    return infos.stream().map(sr -> sr.name).collect(Collectors.toList());
  }
}
