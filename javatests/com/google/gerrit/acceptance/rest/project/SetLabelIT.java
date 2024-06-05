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
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class SetLabelIT extends AbstractDaemonTest {
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
                    .label(LabelId.CODE_REVIEW)
                    .update(new LabelDefinitionInput()));
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
                    .label(LabelId.CODE_REVIEW)
                    .update(new LabelDefinitionInput()));
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void updateName() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "Foo-Review";

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.name).isEqualTo(input.name);

    assertThat(gApi.projects().name(allProjects.get()).label("Foo-Review").get()).isNotNull();
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get());
  }

  @Test
  public void updateDescription() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.description = "Code review label description";

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.description).isEqualTo("Code review label description");

    input.description = "";
    updatedLabel = gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.description).isNull();
  }

  @Test
  public void nameIsTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = " Foo-Review ";

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.name).isEqualTo("Foo-Review");

    assertThat(gApi.projects().name(allProjects.get()).label("Foo-Review").get()).isNotNull();
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get());
  }

  @Test
  public void cannotSetEmptyName() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("name cannot be empty");
  }

  @Test
  public void cannotSetInvalidName() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "INVALID_NAME";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("invalid name: " + input.name);
  }

  @Test
  public void cannotSetNameIfNameClashes() throws Exception {
    configLabel("Foo-Review", LabelFunction.NO_OP);
    configLabel("Bar-Review", LabelFunction.NO_OP);

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "Bar-Review";

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).label("Foo-Review").update(input));
    assertThat(thrown).hasMessageThat().contains("name " + input.name + " already in use");
  }

  @Test
  public void cannotSetNameIfNameConflicts() throws Exception {
    configLabel("Foo-Review", LabelFunction.NO_OP);
    configLabel("Bar-Review", LabelFunction.NO_OP);

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = "bar-review";

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).label("Foo-Review").update(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("name bar-review conflicts with existing label Bar-Review");
  }

  @Test
  public void updateFunction() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = LabelFunction.NO_OP.getFunctionName();

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.function).isEqualTo(input.function);

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().function)
        .isEqualTo(input.function);
  }

  @Test
  public void functionIsTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = " " + LabelFunction.NO_OP.getFunctionName() + " ";

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.function).isEqualTo(LabelFunction.NO_OP.getFunctionName());

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().function)
        .isEqualTo(LabelFunction.NO_OP.getFunctionName());
  }

  @Test
  public void cannotSetEmptyFunction() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = "";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("function cannot be empty");
  }

  @Test
  public void cannotSetUnknownFunction() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = "UnknownFunction";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("unknown function: " + input.function);
  }

  @Test
  public void cannotSetEmptyValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("values cannot be empty");
  }

  @Test
  public void updateValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    // Positive values can be specified as '<value>' or '+<value>'.
    input.values =
        ImmutableMap.of(
            "2",
            "Looks Very Good",
            "+1",
            "Looks Good",
            "0",
            "Don't Know",
            "-1",
            "Looks Bad",
            "-2",
            "Looks Very Bad");

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.values)
        .containsExactly(
            "+2", "Looks Very Good",
            "+1", "Looks Good",
            " 0", "Don't Know",
            "-1", "Looks Bad",
            "-2", "Looks Very Bad");

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().values)
        .containsExactly(
            "+2", "Looks Very Good",
            "+1", "Looks Good",
            " 0", "Don't Know",
            "-1", "Looks Bad",
            "-2", "Looks Very Bad");
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

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.values)
        .containsExactly(
            "+2", "Looks Very Good",
            "+1", "Looks Good",
            " 0", "Don't Know",
            "-1", "Looks Bad",
            "-2", "Looks Very Bad");

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().values)
        .containsExactly(
            "+2", "Looks Very Good",
            "+1", "Looks Good",
            " 0", "Don't Know",
            "-1", "Looks Bad",
            "-2", "Looks Very Bad");
  }

  @Test
  public void cannotSetInvalidValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("invalidValue", "description");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("invalid value: invalidValue");
  }

  @Test
  public void cannotSetValueWithEmptyDescription() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("description for value '+1' cannot be empty");
  }

  @Test
  public void cannotSetDuplicateValues() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    // Positive values can be specified as '<value>' or '+<value>'.
    input.values =
        ImmutableMap.of(
            "+1", "Looks Good", "1", "Looks Good", "0", "Don't Know", "-1", "Looks Bad");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("duplicate value: 1");
  }

  @Test
  public void updateDefaultValue() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.defaultValue = 1;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.defaultValue).isEqualTo(input.defaultValue);

    assertThat(
            gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().defaultValue)
        .isEqualTo(input.defaultValue);
  }

  @Test
  public void cannotSetInvalidDefaultValue() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.defaultValue = 5;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("invalid default value: " + input.defaultValue);
  }

  @Test
  public void updateBranches() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    // Branches can be full ref, ref pattern or regular expression.
    input.branches =
        ImmutableList.of("refs/heads/master", "refs/heads/foo/*", "^refs/heads/stable-.*");

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.branches).containsExactlyElementsIn(input.branches);

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .containsExactlyElementsIn(input.branches);
  }

  @Test
  public void branchesAreTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.branches =
        ImmutableList.of(" refs/heads/master ", " refs/heads/foo/* ", " ^refs/heads/stable-.* ");

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.branches)
        .containsExactly("refs/heads/master", "refs/heads/foo/*", "^refs/heads/stable-.*");

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .containsExactly("refs/heads/master", "refs/heads/foo/*", "^refs/heads/stable-.*");
  }

  @Test
  public void emptyBranchesAreIgnored() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.branches = ImmutableList.of("refs/heads/master", "", " ");

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.branches).containsExactly("refs/heads/master");

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .containsExactly("refs/heads/master");
  }

  @Test
  public void branchesCanBeUnset() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.branches = ImmutableList.of("refs/heads/master");
    gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .isNotNull();

    input.branches = ImmutableList.of();
    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.branches).isNull();
    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .isNull();
  }

  @Test
  public void cannotSetInvalidBranch() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.branches = ImmutableList.of("refs heads master");

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(thrown).hasMessageThat().contains("invalid branch: refs heads master");
  }

  @Test
  public void branchesAreAutomaticallyPrefixedWithRefsHeads() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.branches = ImmutableList.of("master", "refs/meta/config");

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(updatedLabel.branches).containsExactly("refs/heads/master", "refs/meta/config");

    assertThat(gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get().branches)
        .containsExactly("refs/heads/master", "refs/meta/config");
  }

  @Test
  public void setCanOverride() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("foo", lt -> lt.setCanOverride(false));
      u.save();
    }
    assertThat(gApi.projects().name(project.get()).label("foo").get().canOverride).isNull();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.canOverride = true;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.canOverride).isTrue();

    assertThat(gApi.projects().name(project.get()).label("foo").get().canOverride).isTrue();
  }

  @Test
  public void unsetCanOverride() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().canOverride).isTrue();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.canOverride = false;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.canOverride).isNull();

    assertThat(gApi.projects().name(project.get()).label("foo").get().canOverride).isNull();
  }

  @Test
  public void setCopyCondition() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().copyCondition).isNull();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyCondition = "is:MAX";

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.copyCondition).isEqualTo("is:MAX");
  }

  @Test
  public void setCopyConditionPerformsGroupVisibilityCheckWhenUserInPredicateIsUsed()
      throws Exception {
    String administratorsUUID = gApi.groups().query("name:Administrators").get().get(0).id;
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().copyCondition).isNull();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyCondition = "uploaderin:" + administratorsUUID;
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    // User can't see admin group
    requestScopeOperations.setApiUser(user.id());
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("foo").update(input));
    assertThat(thrown).hasMessageThat().contains("Group " + administratorsUUID + " not found");

    // Admin can see admin group
    requestScopeOperations.setApiUser(admin.id());
    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.copyCondition).isEqualTo(input.copyCondition);
  }

  @Test
  public void setInvalidCopyCondition() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().copyCondition).isNull();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyCondition = "foo:::bar";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).label("foo").update(input));
    assertThat(thrown).hasMessageThat().contains("unable to parse copy condition");
  }

  @Test
  public void unsetCopyCondition() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("foo", lt -> lt.setCopyCondition("is:MAX"));
      u.save();
    }
    assertThat(gApi.projects().name(project.get()).label("foo").get().copyCondition)
        .isEqualTo("is:MAX");

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.unsetCopyCondition = true;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.copyCondition).isNull();

    assertThat(gApi.projects().name(project.get()).label("foo").get().copyCondition).isNull();
  }

  @Test
  public void unsetAllowPostSubmit() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().allowPostSubmit).isTrue();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.allowPostSubmit = false;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.allowPostSubmit).isNull();

    assertThat(gApi.projects().name(project.get()).label("foo").get().allowPostSubmit).isNull();
  }

  @Test
  public void setIgnoreSelfApproval() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    assertThat(gApi.projects().name(project.get()).label("foo").get().ignoreSelfApproval).isNull();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.ignoreSelfApproval).isTrue();

    assertThat(gApi.projects().name(project.get()).label("foo").get().ignoreSelfApproval).isTrue();
  }

  @Test
  public void unsetIgnoreSelfApproval() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("foo", lt -> lt.setIgnoreSelfApproval(true));
      u.save();
    }
    assertThat(gApi.projects().name(project.get()).label("foo").get().ignoreSelfApproval).isTrue();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = false;

    LabelDefinitionInfo updatedLabel =
        gApi.projects().name(project.get()).label("foo").update(input);
    assertThat(updatedLabel.ignoreSelfApproval).isNull();

    assertThat(gApi.projects().name(project.get()).label("foo").get().ignoreSelfApproval).isNull();
  }

  @Test
  public void noOpUpdate() throws Exception {
    RevCommit refsMetaConfigHead =
        projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG);

    LabelDefinitionInfo updatedLabel =
        gApi.projects()
            .name(allProjects.get())
            .label(LabelId.CODE_REVIEW)
            .update(new LabelDefinitionInput());
    LabelAssert.assertCodeReviewLabel(updatedLabel);

    LabelAssert.assertCodeReviewLabel(
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get());

    assertThat(projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void defaultCommitMessage() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = LabelFunction.NO_OP.getFunctionName();
    gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Update label");
  }

  @Test
  public void withCommitMessage() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = LabelFunction.NO_OP.getFunctionName();
    input.commitMessage = "Set NoOp function";
    gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo(input.commitMessage);
  }

  @Test
  public void commitMessageIsTrimmed() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = LabelFunction.NO_OP.getFunctionName();
    input.commitMessage = " Set NoOp function ";
    gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input);
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Set NoOp function");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_setLabelRejected() {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = LabelFunction.NO_OP.getFunctionName();
    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).update(input));
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }
}
