// Copyright (C) 2019 The Android Open Source Project
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
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
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
public class PostLabelsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(allProjects.get()).labels(new BatchLabelInput()));
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
            () -> gApi.projects().name(allProjects.get()).labels(new BatchLabelInput()));
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void deleteNonExistingLabel() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo");

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo not found");
  }

  @Test
  public void deleteLabels() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);
    configLabel("Bar", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).labels().get()).isNotEmpty();

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo", "Bar");
    gApi.projects().name(project.get()).labels(input);
    assertThat(gApi.projects().name(project.get()).labels().get()).isEmpty();
  }

  @Test
  public void deleteLabels_labelNamesAreTrimmed() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);
    configLabel("Bar", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).labels().get()).isNotEmpty();

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of(" Foo ", " Bar ");
    gApi.projects().name(project.get()).labels(input);
    assertThat(gApi.projects().name(project.get()).labels().get()).isEmpty();
  }

  @Test
  public void cannotDeleteTheSameLabelTwice() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo", "Foo");

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(project.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo not found");
  }

  @Test
  public void cannotCreateLabelWithNameThatIsAlreadyInUse() throws Exception {
    LabelDefinitionInput labelInput = new LabelDefinitionInput();
    labelInput.name = "Code-Review";
    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(labelInput);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Code-Review already exists");
  }

  @Test
  public void cannotCreateTwoLabelsWithTheSameName() throws Exception {
    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = "Foo";
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooInput, fooInput);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo already exists");
  }

  @Test
  public void cannotCreateTwoLabelsWithNamesThatAreTheSameAfterTrim() throws Exception {
    LabelDefinitionInput foo1Input = new LabelDefinitionInput();
    foo1Input.name = "Foo";
    foo1Input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput foo2Input = new LabelDefinitionInput();
    foo2Input.name = " Foo ";
    foo2Input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(foo1Input, foo2Input);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo already exists");
  }

  @Test
  public void cannotCreateTwoLabelsWithConflictingNames() throws Exception {
    LabelDefinitionInput foo1Input = new LabelDefinitionInput();
    foo1Input.name = "Foo";
    foo1Input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput foo2Input = new LabelDefinitionInput();
    foo2Input.name = "foo";
    foo2Input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(foo1Input, foo2Input);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label foo conflicts with existing label Foo");
  }

  @Test
  public void createLabels() throws Exception {
    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = "Foo";
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput barInput = new LabelDefinitionInput();
    barInput.name = "Bar";
    barInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooInput, barInput);

    gApi.projects().name(allProjects.get()).labels(input);
    assertThat(gApi.projects().name(allProjects.get()).label("Foo").get()).isNotNull();
    assertThat(gApi.projects().name(allProjects.get()).label("Bar").get()).isNotNull();
  }

  @Test
  public void createLabels_labelNamesAreTrimmed() throws Exception {
    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = " Foo ";
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput barInput = new LabelDefinitionInput();
    barInput.name = " Bar ";
    barInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooInput, barInput);

    gApi.projects().name(allProjects.get()).labels(input);
    assertThat(gApi.projects().name(allProjects.get()).label("Foo").get()).isNotNull();
    assertThat(gApi.projects().name(allProjects.get()).label("Bar").get()).isNotNull();
  }

  @Test
  public void cannotCreateLabelWithoutName() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(new LabelDefinitionInput());

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label name is required for new label");
  }

  @Test
  public void cannotSetCommitMessageOnLabelDefinitionInputForCreate() throws Exception {
    LabelDefinitionInput labelInput = new LabelDefinitionInput();
    labelInput.name = "Foo";
    labelInput.commitMessage = "Create Label Foo";

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(labelInput);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("commit message on label definition input not supported");
  }

  @Test
  public void updateNonExistingLabel() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.update = ImmutableMap.of("Foo", new LabelDefinitionInput());

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo not found");
  }

  @Test
  public void updateLabels() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);
    configLabel("Bar", LabelFunction.NO_OP);

    LabelDefinitionInput fooUpdate = new LabelDefinitionInput();
    fooUpdate.function = LabelFunction.MAX_WITH_BLOCK.getFunctionName();
    LabelDefinitionInput barUpdate = new LabelDefinitionInput();
    barUpdate.name = "Baz";

    BatchLabelInput input = new BatchLabelInput();
    input.update = ImmutableMap.of("Foo", fooUpdate, "Bar", barUpdate);

    gApi.projects().name(project.get()).labels(input);

    assertThat(gApi.projects().name(project.get()).label("Foo").get().function)
        .isEqualTo(fooUpdate.function);
    assertThat(gApi.projects().name(project.get()).label("Baz").get()).isNotNull();
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).label("Bar").get());
  }

  @Test
  public void updateLabels_labelNamesAreTrimmed() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);
    configLabel("Bar", LabelFunction.NO_OP);

    LabelDefinitionInput fooUpdate = new LabelDefinitionInput();
    fooUpdate.function = LabelFunction.MAX_WITH_BLOCK.getFunctionName();
    LabelDefinitionInput barUpdate = new LabelDefinitionInput();
    barUpdate.name = "Baz";

    BatchLabelInput input = new BatchLabelInput();
    input.update = ImmutableMap.of(" Foo ", fooUpdate, " Bar ", barUpdate);

    gApi.projects().name(project.get()).labels(input);

    assertThat(gApi.projects().name(project.get()).label("Foo").get().function)
        .isEqualTo(fooUpdate.function);
    assertThat(gApi.projects().name(project.get()).label("Baz").get()).isNotNull();
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).label("Bar").get());
  }

  @Test
  public void cannotSetCommitMessageOnLabelDefinitionInputForUpdate() throws Exception {
    LabelDefinitionInput labelInput = new LabelDefinitionInput();
    labelInput.commitMessage = "Update label";

    BatchLabelInput input = new BatchLabelInput();
    input.update = ImmutableMap.of("Code-Review", labelInput);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.projects().name(allProjects.get()).labels(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("commit message on label definition input not supported");
  }

  @Test
  public void deleteAndRecreateLabel() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);

    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = "Foo";
    fooInput.function = LabelFunction.MAX_NO_BLOCK.getFunctionName();
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo");
    input.create = ImmutableList.of(fooInput);

    gApi.projects().name(project.get()).labels(input);

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("Foo").get();
    assertThat(fooLabel.function).isEqualTo(fooInput.function);
  }

  @Test
  public void deleteRecreateAndUpdateLabel() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);

    LabelDefinitionInput fooCreateInput = new LabelDefinitionInput();
    fooCreateInput.name = "Foo";
    fooCreateInput.function = LabelFunction.MAX_NO_BLOCK.getFunctionName();
    fooCreateInput.values =
        ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput fooUpdateInput = new LabelDefinitionInput();
    fooUpdateInput.function = LabelFunction.ANY_WITH_BLOCK.getFunctionName();

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo");
    input.create = ImmutableList.of(fooCreateInput);
    input.update = ImmutableMap.of("Foo", fooUpdateInput);

    gApi.projects().name(project.get()).labels(input);

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("Foo").get();
    assertThat(fooLabel.function).isEqualTo(fooUpdateInput.function);
  }

  @Test
  public void cannotDeleteAndUpdateLabel() throws Exception {
    configLabel("Foo", LabelFunction.NO_OP);

    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.function = LabelFunction.MAX_NO_BLOCK.getFunctionName();

    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Foo");
    input.update = ImmutableMap.of("Foo", fooInput);

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(project.get()).labels(input));
    assertThat(thrown).hasMessageThat().contains("label Foo not found");
  }

  @Test
  public void createAndUpdateLabel() throws Exception {
    LabelDefinitionInput fooCreateInput = new LabelDefinitionInput();
    fooCreateInput.name = "Foo";
    fooCreateInput.function = LabelFunction.MAX_NO_BLOCK.getFunctionName();
    fooCreateInput.values =
        ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInput fooUpdateInput = new LabelDefinitionInput();
    fooUpdateInput.function = LabelFunction.ANY_WITH_BLOCK.getFunctionName();

    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooCreateInput);
    input.update = ImmutableMap.of("Foo", fooUpdateInput);

    gApi.projects().name(project.get()).labels(input);

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("Foo").get();
    assertThat(fooLabel.function).isEqualTo(fooUpdateInput.function);
  }

  @Test
  public void noOpUpdate() throws Exception {
    RevCommit refsMetaConfigHead =
        projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG);

    gApi.projects().name(allProjects.get()).labels(new BatchLabelInput());

    assertThat(projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void defaultCommitMessage() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.delete = ImmutableList.of("Code-Review");
    gApi.projects().name(allProjects.get()).labels(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Update labels");
  }

  @Test
  public void withCommitMessage() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.commitMessage = "Batch Update Labels";
    input.delete = ImmutableList.of("Code-Review");
    gApi.projects().name(allProjects.get()).labels(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo(input.commitMessage);
  }

  @Test
  public void commitMessageIsTrimmed() throws Exception {
    BatchLabelInput input = new BatchLabelInput();
    input.commitMessage = " Batch Update Labels ";
    input.delete = ImmutableList.of("Code-Review");
    gApi.projects().name(allProjects.get()).labels(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Batch Update Labels");
  }
}
