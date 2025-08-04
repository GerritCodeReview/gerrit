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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.BatchSubmitRequirementInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsAPIIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void cannotGetANonExistingSR() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("code-review").get());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Submit requirement 'code-review' does not exist");
  }

  @Test
  public void getExistingSR() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.applicabilityExpression = "topic:foo";
    input.submittabilityExpression = "label:code-review=+2";
    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").get();
    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.applicabilityExpression).isEqualTo("topic:foo");
    assertThat(info.submittabilityExpression).isEqualTo("label:code-review=+2");
    assertThat(info.allowOverrideInChildProjects).isEqualTo(false);
  }

  @Test
  public void updateSubmitRequirement() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.applicabilityExpression = "topic:foo";
    input.submittabilityExpression = "label:code-review=+2";
    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);

    input.submittabilityExpression = "label:code-review=+1";
    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").update(input);
    assertThat(info.submittabilityExpression).isEqualTo("label:code-review=+1");
  }

  @Test
  public void updateSRWithEmptyApplicabilityExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.applicabilityExpression = "topic:foo";
    input.submittabilityExpression = "label:code-review=+2";
    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);

    input.applicabilityExpression = null;
    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").update(input);
    assertThat(info.applicabilityExpression).isNull();
  }

  @Test
  public void updateSRWithEmptyOverrideExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.overrideExpression = "topic:foo";
    input.submittabilityExpression = "label:code-review=+2";
    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);

    input.overrideExpression = null;
    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").update(input);
    assertThat(info.overrideExpression).isNull();
  }

  @Test
  public void allowOverrideInChildProjectsDefaultsToFalse_updateSR() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "label:code-review=+2";
    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);

    input.overrideExpression = "topic:foo";
    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").update(input);
    assertThat(info.allowOverrideInChildProjects).isFalse();
  }

  @Test
  public void cannotUpdateSRAsAnonymousUser() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "label:code-review=+2";

    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);
    input.submittabilityExpression = "label:code-review=+1";
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .submitRequirement("code-review")
                    .update(new SubmitRequirementInput()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void cannotUpdateSRtIfSRDoesNotExist() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittabilityExpression = "label:code-review=+2";

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Submit requirement 'code-review' does not exist");
  }

  @Test
  public void cannotUpdateSRWithEmptySubmittableIf() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "project:foo AND branch:refs/heads/main";

    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);
    input.submittabilityExpression = null;
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));

    assertThat(thrown).hasMessageThat().isEqualTo("submittability_expression is required");
  }

  @Test
  public void cannotUpdateSRWithInvalidSubmittableIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "project:foo AND branch:refs/heads/main";

    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);
    input.submittabilityExpression = "invalid_field:invalid_value";
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));
    assertThat(thrown)
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
  public void cannotUpdateSRWithInvalidOverrideIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "project:foo AND branch:refs/heads/main";

    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);
    input.overrideExpression = "invalid_field:invalid_value";
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));
    assertThat(thrown)
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
  public void cannotUpdateSRWithInvalidApplicableIfExpression() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.submittabilityExpression = "project:foo AND branch:refs/heads/main";

    gApi.projects().name(project.get()).submitRequirement("code-review").create(input);
    input.applicabilityExpression = "invalid_field:invalid_value";
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));
    assertThat(thrown)
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
  public void createSubmitRequirement() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicabilityExpression = "project:foo AND branch:refs/heads/main";
    input.submittabilityExpression = "label:code-review=+2";
    input.overrideExpression = "label:build-cop-override=+1";
    input.allowOverrideInChildProjects = true;

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.projectName).isEqualTo(project.get());
    assertThat(info.applicabilityExpression).isEqualTo(input.applicabilityExpression);
    assertThat(info.applicabilityExpression).isEqualTo(input.applicabilityExpression);
    assertThat(info.submittabilityExpression).isEqualTo(input.submittabilityExpression);
    assertThat(info.overrideExpression).isEqualTo(input.overrideExpression);
    assertThat(info.allowOverrideInChildProjects).isEqualTo(true);
  }

  @Test
  public void createSRWithEmptyApplicabilityExpression_isAllowed() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittabilityExpression = "label:code-review=+2";
    input.overrideExpression = "label:build-cop-override=+1";

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
    input.applicabilityExpression = "project:foo AND branch:refs/heads/main";
    input.submittabilityExpression = "label:code-review=+2";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.name).isEqualTo("code-review");
    assertThat(info.overrideExpression).isNull();
  }

  @Test
  public void allowOverrideInChildProjectsDefaultsToFalse_createSR() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittabilityExpression = "label:code-review=+2";

    SubmitRequirementInfo info =
        gApi.projects().name(project.get()).submitRequirement("code-review").create(input).get();

    assertThat(info.allowOverrideInChildProjects).isEqualTo(false);
  }

  @Test
  public void cannotCreateSRAsAnonymousUser() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicabilityExpression = "project:foo AND branch:refs/heads/main";
    input.submittabilityExpression = "label:code-review=+2";
    input.overrideExpression = "label:build-cop-override=+1";

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
    input.submittabilityExpression = "label:code-review=+2";

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
    input.submittabilityExpression = "label:code-review=+2";

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
                + "Name can only consist of alphanumeric characters and '-'."
                + " Name cannot start with '-' or number.");
  }

  @Test
  public void cannotCreateSRWithEmptySubmittableIf() throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.applicabilityExpression = "project:foo AND branch:refs/heads/main";
    input.overrideExpression = "label:build-cop-override=+1";

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
    input.submittabilityExpression = "invalid_field:invalid_value";
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
    input.submittabilityExpression = "label:Code-Review=+2";
    input.overrideExpression = "invalid_field:invalid_value";
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
    input.applicabilityExpression = "invalid_field:invalid_value";
    input.submittabilityExpression = "label:Code-Review=+2";
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
  public void cannotListSRs_withMissingReadPermissionsToRefsConfig() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirements().get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void cannotListSRs_withMissingReadPermissionsInParent_withInheritance() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirements().withInherited(true).get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void canListSRs_withReadPermissionsInAllParentProjects_withInheritance() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    @SuppressWarnings("unused")
    var unused = gApi.projects().name(project.get()).submitRequirements().get();
  }

  @Test
  public void canListSRs_withMissingReadPermissionsInParent_withoutInheritance() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    @SuppressWarnings("unused")
    var unused =
        gApi.projects().name(project.get()).submitRequirements().withInherited(false).get();
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

    assertThat(names(infos))
        .containsExactly("Code-Review", "No-Unresolved-Comments", "base-sr", "sr-1", "sr-2")
        .inOrder();
    assertThat(infos.get(0).name).isEqualTo("Code-Review");
    assertThat(infos.get(0).projectName).isEqualTo(allProjects.get());
    assertThat(infos.get(1).name).isEqualTo("No-Unresolved-Comments");
    assertThat(infos.get(1).projectName).isEqualTo(allProjects.get());
    assertThat(infos.get(2).name).isEqualTo("base-sr");
    assertThat(infos.get(2).projectName).isEqualTo(allProjects.get());
    assertThat(infos.get(3).name).isEqualTo("sr-1");
    assertThat(infos.get(3).projectName).isEqualTo(project.get());
    assertThat(infos.get(4).name).isEqualTo("sr-2");
    assertThat(infos.get(4).projectName).isEqualTo(project.get());
  }

  @Test
  public void listSRsWithInheritanceIncludesGlobalSubmitRequirements() throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("Global-Submit-Requirement")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(false)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      List<SubmitRequirementInfo> infos =
          gApi.projects().name(project.get()).submitRequirements().withInherited(true).get();

      assertThat(names(infos))
          .containsExactly("Global-Submit-Requirement", "Code-Review", "No-Unresolved-Comments")
          .inOrder();
      assertThat(infos.get(0).name).isEqualTo("Global-Submit-Requirement");
      assertThat(infos.get(0).projectName).isNull();
      assertThat(infos.get(1).name).isEqualTo("Code-Review");
      assertThat(infos.get(1).projectName).isEqualTo(allProjects.get());
      assertThat(infos.get(2).name).isEqualTo("No-Unresolved-Comments");
      assertThat(infos.get(2).projectName).isEqualTo(allProjects.get());
    }
  }

  @Test
  public void cannotDeleteSRAsAnonymousUser() throws Exception {
    createSubmitRequirement("code-review");

    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("code-review").delete());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void cannotDeleteSRWithMissingWritePermissionsToRefsConfig() throws Exception {
    createSubmitRequirement("sr-1");
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .add(block("write").ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("sr-1").delete());
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void cannotDeleteNonExistingSR() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("non-existing").delete());
    assertThat(thrown)
        .hasMessageThat()
        .contains("Submit requirement 'non-existing' does not exist");
  }

  @Test
  public void deleteSubmitRequirement() throws Exception {
    createSubmitRequirement("code-review");
    createSubmitRequirement("verified");

    List<SubmitRequirementInfo> infos =
        gApi.projects().name(project.get()).submitRequirements().get();
    assertThat(names(infos)).containsExactly("code-review", "verified");

    gApi.projects().name(project.get()).submitRequirement("code-review").delete();
    infos = gApi.projects().name(project.get()).submitRequirements().get();
    assertThat(names(infos)).containsExactly("verified");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_createSubmitRequirementRejected() {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.description = "At least one +2 vote to the code-review label";
    input.submittabilityExpression = "label:code-review=+2";
    input.overrideExpression = "label:build-cop-override=+1";

    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").create(input));
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_updateSubmitRequirementRejected() throws Exception {
    createSubmitRequirementWithReview(project.get(), "code-review");

    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = "code-review";
    input.applicabilityExpression = "topic:foo";
    input.submittabilityExpression = "label:code-review=+2";

    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () ->
                gApi.projects().name(project.get()).submitRequirement("code-review").update(input));
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_deleteSubmitRequirementRejected() throws Exception {
    createSubmitRequirementWithReview(project.get(), "code-review");

    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("code-review").delete());
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }

  private SubmitRequirementInfo createSubmitRequirement(String srName) throws RestApiException {
    return createSubmitRequirement(project.get(), srName);
  }

  private SubmitRequirementInfo createSubmitRequirement(String project, String srName)
      throws RestApiException {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = srName;
    input.submittabilityExpression = "label:dummy=+2";

    return gApi.projects().name(project).submitRequirement(srName).create(input).get();
  }

  private void createSubmitRequirementWithReview(String project, String srName)
      throws RestApiException {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = srName;
    input.submittabilityExpression = "label:dummy=+2";
    BatchSubmitRequirementInput batchInput = new BatchSubmitRequirementInput();
    batchInput.create = ImmutableList.of(input);

    ChangeInfo change = gApi.projects().name(project).submitRequirementsReview(batchInput);
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.label("Code-Review", 2);
    gApi.changes().id(change.project, change._number).current().review(reviewInput);
    gApi.changes().id(change.project, change._number).current().submit();
  }

  private List<String> names(List<SubmitRequirementInfo> infos) {
    return infos.stream().map(sr -> sr.name).collect(Collectors.toList());
  }
}
