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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.BatchSubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.restapi.project.PostLabels;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Tests for the {@link PostLabels} REST endpoint. */
public class PostSubmitRequirementsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects()
                    .name(allProjects.get())
                    .submitRequirements(new BatchSubmitRequirementInput()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects()
                    .name(allProjects.get())
                    .submitRequirements(new BatchSubmitRequirementInput()));
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void deleteNonExistingSR() throws Exception {
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo");

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown).hasMessageThat().contains("Submit requirement Foo not found");
  }

  @Test
  public void deleteSR() throws Exception {
    configSubmitRequirement(project, "Foo");
    configSubmitRequirement(project, "Bar");
    assertThat(gApi.projects().name(project.get()).submitRequirements().get()).isNotEmpty();

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo", "Bar");
    gApi.projects().name(project.get()).submitRequirements(input);
    assertThat(gApi.projects().name(project.get()).submitRequirements().get()).isEmpty();
  }

  @Test
  public void deleteSR_namesAreTrimmed() throws Exception {
    configSubmitRequirement(project, "Foo");
    configSubmitRequirement(project, "Bar");
    assertThat(gApi.projects().name(project.get()).submitRequirements().get()).isNotEmpty();

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of(" Foo ", " Bar ");
    gApi.projects().name(project.get()).submitRequirements(input);
    assertThat(gApi.projects().name(project.get()).submitRequirements().get()).isEmpty();
  }

  @Test
  public void cannotDeleteTheSameSRTwice() throws Exception {
    configSubmitRequirement(allProjects, "Foo");

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo", "Foo");

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown).hasMessageThat().contains("Submit requirement Foo not found");
  }

  @Test
  public void cannotCreateSRWithNameThatIsAlreadyInUse() throws Exception {
    configSubmitRequirement(allProjects, "Foo");
    SubmitRequirementInput srInput = new SubmitRequirementInput();
    srInput.name = "Foo";
    srInput.allowOverrideInChildProjects = false;
    srInput.submittabilityExpression = "label:code-review=+2";
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(srInput);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("submit requirement \"Foo\" conflicts with existing submit requirement \"Foo\"");
  }

  @Test
  public void cannotCreateTwoSRWithTheSameName() throws Exception {
    SubmitRequirementInput srInput = new SubmitRequirementInput();
    srInput.name = "Foo";
    srInput.allowOverrideInChildProjects = false;
    srInput.submittabilityExpression = "label:code-review=+2";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(srInput, srInput);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).submitRequirements(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("submit requirement \"Foo\" conflicts with existing submit requirement \"Foo\"");
  }

  @Test
  public void cannotCreateTwoSrWithConflictingNames() throws Exception {
    SubmitRequirementInput sr1Input = new SubmitRequirementInput();
    sr1Input.name = "Foo";
    sr1Input.allowOverrideInChildProjects = false;
    sr1Input.submittabilityExpression = "label:code-review=+2";

    SubmitRequirementInput sr2Input = new SubmitRequirementInput();
    sr2Input.name = "foo";
    sr2Input.allowOverrideInChildProjects = false;
    sr2Input.submittabilityExpression = "label:code-review=+2";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(sr1Input, sr2Input);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).submitRequirements(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("submit requirement \"foo\" conflicts with existing submit requirement \"Foo\"");
  }

  @Test
  public void createSubmitRequirements() throws Exception {
    SubmitRequirementInput fooInput = new SubmitRequirementInput();
    fooInput.name = "Foo";
    fooInput.allowOverrideInChildProjects = false;
    fooInput.submittabilityExpression = "label:code-review=+2";

    SubmitRequirementInput barInput = new SubmitRequirementInput();
    barInput.name = "Bar";
    barInput.allowOverrideInChildProjects = false;
    barInput.submittabilityExpression = "label:code-review=+1";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(fooInput, barInput);

    gApi.projects().name(allProjects.get()).submitRequirements(input);
    assertThat(gApi.projects().name(allProjects.get()).submitRequirement("Foo").get()).isNotNull();
    assertThat(gApi.projects().name(allProjects.get()).submitRequirement("Bar").get()).isNotNull();
  }

  @Test
  public void cannotCreateSRWithIncorrectName() throws Exception {
    SubmitRequirementInput fooInput = new SubmitRequirementInput();
    fooInput.name = "Foo ";
    fooInput.allowOverrideInChildProjects = false;
    fooInput.submittabilityExpression = "label:code-review=+2";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(fooInput, fooInput);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Name can only consist of alphanumeric characters");
  }

  @Test
  public void cannotCreateSRWithoutName() throws Exception {
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.create = ImmutableList.of(new SubmitRequirementInput());

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown).hasMessageThat().contains("Empty submit requirement name");
  }

  @Test
  public void updateNonExistingSR() throws Exception {
    SubmitRequirementInput fooInput = new SubmitRequirementInput();
    fooInput.name = "Foo2";
    fooInput.allowOverrideInChildProjects = false;
    fooInput.submittabilityExpression = "label:code-review=+2";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.update = ImmutableMap.of("Foo", fooInput);

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(allProjects.get()).submitRequirements(input));
    assertThat(thrown).hasMessageThat().contains("Submit requirement Foo not found");
  }

  @Test
  public void updateSR() throws Exception {
    configSubmitRequirement(project, "Foo");
    configSubmitRequirement(project, "Bar");

    SubmitRequirementInput fooUpdate = new SubmitRequirementInput();
    fooUpdate.name = "Foo";
    fooUpdate.description = "new description";
    fooUpdate.submittabilityExpression = "-has:submodule-update";

    SubmitRequirementInput barUpdate = new SubmitRequirementInput();
    barUpdate.name = "Baz";
    barUpdate.submittabilityExpression = "label:code-review=+1";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.update = ImmutableMap.of("Foo", fooUpdate, "Bar", barUpdate);

    gApi.projects().name(project.get()).submitRequirements(input);

    assertThat(gApi.projects().name(project.get()).submitRequirement("Foo").get().description)
        .isEqualTo(fooUpdate.description);
    assertThat(
            gApi.projects()
                .name(project.get())
                .submitRequirement("Foo")
                .get()
                .submittabilityExpression)
        .isEqualTo(fooUpdate.submittabilityExpression);
    assertThat(gApi.projects().name(project.get()).submitRequirement("Baz").get()).isNotNull();
    assertThat(
            gApi.projects()
                .name(project.get())
                .submitRequirement("Baz")
                .get()
                .submittabilityExpression)
        .isEqualTo(barUpdate.submittabilityExpression);
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).submitRequirement("Bar").get());
  }

  @Test
  public void deleteAndRecreateSR() throws Exception {
    configSubmitRequirement(project, "Foo");

    SubmitRequirementInput fooUpdate = new SubmitRequirementInput();
    fooUpdate.name = "Foo";
    fooUpdate.description = "new description";
    fooUpdate.submittabilityExpression = "-has:submodule-update";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo");
    input.create = ImmutableList.of(fooUpdate);

    gApi.projects().name(project.get()).submitRequirements(input);

    SubmitRequirementInfo fooSR =
        gApi.projects().name(project.get()).submitRequirement("Foo").get();
    assertThat(fooSR.description).isEqualTo(fooUpdate.description);
    assertThat(fooSR.submittabilityExpression).isEqualTo(fooUpdate.submittabilityExpression);
  }

  @Test
  public void cannotDeleteAndUpdateSR() throws Exception {
    configSubmitRequirement(project, "Foo");

    SubmitRequirementInput fooUpdate = new SubmitRequirementInput();
    fooUpdate.name = "Foo";
    fooUpdate.description = "new description";
    fooUpdate.submittabilityExpression = "-has:submodule-update";

    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo");
    input.update = ImmutableMap.of("Foo", fooUpdate);

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(project.get()).submitRequirements(input));
    assertThat(thrown).hasMessageThat().contains("Submit requirement Foo not found");
  }

  @Test
  public void noOpUpdate() throws Exception {
    RevCommit refsMetaConfigHead =
        projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG);

    gApi.projects().name(allProjects.get()).submitRequirements(new BatchSubmitRequirementInput());

    assertThat(projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void defaultCommitMessage() throws Exception {
    configSubmitRequirement(allProjects, "Foo");
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.delete = ImmutableList.of("Foo");
    gApi.projects().name(allProjects.get()).submitRequirements(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Update Submit Requirements");
  }

  @Test
  public void withCommitMessage() throws Exception {
    configSubmitRequirement(allProjects, "Foo");
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.commitMessage = "Batch Update SubmitRequirements";
    input.delete = ImmutableList.of("Foo");
    gApi.projects().name(allProjects.get()).submitRequirements(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo(input.commitMessage);
  }

  @Test
  public void commitMessageIsTrimmed() throws Exception {
    configSubmitRequirement(allProjects, "Foo");
    BatchSubmitRequirementInput input = new BatchSubmitRequirementInput();
    input.commitMessage = "Batch Update SubmitRequirements ";
    input.delete = ImmutableList.of("Foo");
    gApi.projects().name(allProjects.get()).submitRequirements(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Batch Update SubmitRequirements");
  }
}
