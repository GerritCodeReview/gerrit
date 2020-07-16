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
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class CreateLabelIT extends AbstractDaemonTest {
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
                    .name(project.get())
                    .label("Foo-Review")
                    .create(new LabelDefinitionInput()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .label("Foo-Review")
                    .create(new LabelDefinitionInput()));
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void cannotCreateLabelIfNameDoesntMatch() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "Foo";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Bar").create(input));
    assertThat(thrown).hasMessageThat().contains("name in input must match name in URL");
  }

  @Test
  public void cannotCreateLabelWithNameThatIsAlreadyInUse() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                gApi.projects()
                    .name(allProjects.get())
                    .label("Code-Review")
                    .create(new LabelDefinitionInput()));
    assertThat(thrown).hasMessageThat().contains("label Code-Review already exists");
  }

  @Test
  public void cannotCreateLabelWithNameThatConflicts() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                gApi.projects()
                    .name(allProjects.get())
                    .label("code-review")
                    .create(new LabelDefinitionInput()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("label code-review conflicts with existing label Code-Review");
  }

  @Test
  public void cannotCreateLabelWithInvalidName() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("INVALID_NAME").create(input));
    assertThat(thrown).hasMessageThat().contains("invalid name: INVALID_NAME");
  }

  @Test
  public void cannotCreateLabelWithoutValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("values are required");

    input.values = ImmutableMap.of();
    thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("values are required");
  }

  @Test
  public void cannotCreateLabelWithInvalidValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("invalidValue", "description");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("invalid value: invalidValue");
  }

  @Test
  public void cannotCreateLabelWithValuesThatHaveEmptyDescription() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("description for value '+1' cannot be empty");
  }

  @Test
  public void cannotCreateLabelWithDuplicateValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    // Positive values can be specified as '<value>' or '+<value>'.
    input.values =
        ImmutableMap.of(
            "+1", "Looks Good", "1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("duplicate value: 1");
  }

  @Test
  public void cannotCreateLabelWithInvalidDefaultValue() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");
    input.defaultValue = 5;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("invalid default value: " + input.defaultValue);
  }

  @Test
  public void cannotCreateLabelWithUnknownFunction() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");
    input.function = "UnknownFuction";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("unknown function: " + input.function);
  }

  @Test
  public void cannotCreateLabelWithInvalidBranch() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");
    input.branches = ImmutableList.of("refs heads master");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("Foo").create(input));
    assertThat(thrown).hasMessageThat().contains("invalid branch: refs heads master");
  }

  @Test
  public void createWithNameAndValuesOnly() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();

    assertThat(createdLabel.name).isEqualTo("Foo");
    assertThat(createdLabel.projectName).isEqualTo(project.get());
    assertThat(createdLabel.function).isEqualTo(LabelFunction.MAX_WITH_BLOCK.getFunctionName());
    assertThat(createdLabel.values).containsExactlyEntriesIn(input.values);
    assertThat(createdLabel.defaultValue).isEqualTo(0);
    assertThat(createdLabel.branches).isNull();
    assertThat(createdLabel.canOverride).isTrue();
    assertThat(createdLabel.copyAnyScore).isNull();
    assertThat(createdLabel.copyMinScore).isNull();
    assertThat(createdLabel.copyMaxScore).isNull();
    assertThat(createdLabel.copyAllScoresIfNoChange).isTrue();
    assertThat(createdLabel.copyAllScoresIfNoCodeChange).isNull();
    assertThat(createdLabel.copyAllScoresOnTrivialRebase).isNull();
    assertThat(createdLabel.copyAllScoresOnMergeFirstParentUpdate).isNull();
    assertThat(createdLabel.copyValues).isNull();
    assertThat(createdLabel.allowPostSubmit).isTrue();
    assertThat(createdLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void createWithFunction() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.function = LabelFunction.NO_OP.getFunctionName();

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();

    assertThat(createdLabel.function).isEqualTo(LabelFunction.NO_OP.getFunctionName());
  }

  @Test
  public void functionEmptyAfterTrim() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.function = " ";

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();

    assertThat(createdLabel.function).isEqualTo(LabelFunction.MAX_WITH_BLOCK.getFunctionName());
  }

  @Test
  public void valuesAndDescriptionsAreTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    // Positive values can be specified as '<value>' or '+<value>'.
    input.values =
        ImmutableMap.of(
            " 2 ",
            " Looks Very Good ",
            " +1 ",
            " Looks Good ",
            " 0 ",
            " Don't Know ",
            " -1 ",
            " Looks Bad ",
            " -2 ",
            " Looks Very Bad ");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();
    assertThat(createdLabel.values)
        .containsExactly(
            "+2", "Looks Very Good",
            "+1", "Looks Good",
            " 0", "Don't Know",
            "-1", "Looks Bad",
            "-2", "Looks Very Bad");
  }

  @Test
  public void createWithDefaultValue() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.defaultValue = 1;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();

    assertThat(createdLabel.defaultValue).isEqualTo(input.defaultValue);
  }

  @Test
  public void createWithBranches() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    // Branches can be full ref, ref pattern or regular expression.
    input.branches =
        ImmutableList.of("refs/heads/master", "refs/heads/foo/*", "^refs/heads/stable-.*");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();
    assertThat(createdLabel.branches).containsExactlyElementsIn(input.branches);
  }

  @Test
  public void branchesAreTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.branches =
        ImmutableList.of(" refs/heads/master ", " refs/heads/foo/* ", " ^refs/heads/stable-.* ");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();
    assertThat(createdLabel.branches)
        .containsExactly("refs/heads/master", "refs/heads/foo/*", "^refs/heads/stable-.*");
  }

  @Test
  public void emptyBranchesAreIgnored() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.branches = ImmutableList.of("refs/heads/master", "", " ");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();
    assertThat(createdLabel.branches).containsExactly("refs/heads/master");
  }

  @Test
  public void branchesAreAutomaticallyPrefixedWithRefsHeads() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.branches = ImmutableList.of("master", "refs/meta/config");

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("Foo").create(input).get();
    assertThat(createdLabel.branches).containsExactly("refs/heads/master", "refs/meta/config");
  }

  @Test
  public void createWithCanOverride() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.canOverride = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.canOverride).isTrue();
  }

  @Test
  public void createWithoutCanOverride() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.canOverride = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.canOverride).isNull();
  }

  @Test
  public void createWithCopyAnyScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAnyScore = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAnyScore).isTrue();
  }

  @Test
  public void createWithoutCopyAnyScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAnyScore = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAnyScore).isNull();
  }

  @Test
  public void createWithCopyMinScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyMinScore = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyMinScore).isTrue();
  }

  @Test
  public void createWithoutCopyMinScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyMinScore = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyMinScore).isNull();
  }

  @Test
  public void createWithCopyMaxScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyMaxScore = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyMaxScore).isTrue();
  }

  @Test
  public void createWithoutCopyMaxScore() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyMaxScore = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyMaxScore).isNull();
  }

  @Test
  public void createWithCopyAllScoresIfNoChange() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresIfNoChange = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresIfNoChange).isTrue();
  }

  @Test
  public void createWithoutCopyAllScoresIfNoChange() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresIfNoChange = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresIfNoChange).isNull();
  }

  @Test
  public void createWithCopyAllScoresIfNoCodeChange() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresIfNoCodeChange = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresIfNoCodeChange).isTrue();
  }

  @Test
  public void createWithoutCopyAllScoresIfNoCodeChange() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresIfNoCodeChange = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresIfNoCodeChange).isNull();
  }

  @Test
  public void createWithCopyAllScoresOnTrivialRebase() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresOnTrivialRebase = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresOnTrivialRebase).isTrue();
  }

  @Test
  public void createWithoutCopyAllScoresOnTrivialRebase() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresOnTrivialRebase = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresOnTrivialRebase).isNull();
  }

  @Test
  public void createWithCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresOnMergeFirstParentUpdate = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresOnMergeFirstParentUpdate).isTrue();
  }

  @Test
  public void createWithoutCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyAllScoresOnMergeFirstParentUpdate = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyAllScoresOnMergeFirstParentUpdate).isNull();
  }

  @Test
  public void createWithCopyValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.copyValues = ImmutableList.of((short) -1, (short) 1);

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.copyValues).containsExactly((short) -1, (short) 1).inOrder();
  }

  @Test
  public void createWithAllowPostSubmit() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.allowPostSubmit = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.allowPostSubmit).isTrue();
  }

  @Test
  public void createWithoutAllowPostSubmit() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.allowPostSubmit = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.allowPostSubmit).isNull();
  }

  @Test
  public void createWithIgnoreSelfApproval() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.ignoreSelfApproval = true;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.ignoreSelfApproval).isTrue();
  }

  @Test
  public void createWithoutIgnoreSelfApproval() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.ignoreSelfApproval = false;

    LabelDefinitionInfo createdLabel =
        gApi.projects().name(project.get()).label("foo").create(input).get();
    assertThat(createdLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void defaultCommitMessage() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    gApi.projects().name(project.get()).label("Foo").create(input);
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Update label");
  }

  @Test
  public void withCommitMessage() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.commitMessage = "Add Foo Label";
    gApi.projects().name(project.get()).label("Foo").create(input);
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo(input.commitMessage);
  }

  @Test
  public void commitMessageIsTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.commitMessage = " Add Foo Label ";
    gApi.projects().name(project.get()).label("Foo").create(input);
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Add Foo Label");
  }
}
