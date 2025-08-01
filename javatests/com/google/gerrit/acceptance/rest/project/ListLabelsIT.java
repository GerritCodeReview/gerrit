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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
public class ListLabelsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).labels().get());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).labels().get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void noLabels() throws Exception {
    assertThat(gApi.projects().name(project.get()).labels().get()).isEmpty();
  }

  @Test
  public void allProjectsLabels() throws Exception {
    List<LabelDefinitionInfo> labels = gApi.projects().name(allProjects.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly(LabelId.CODE_REVIEW);

    LabelDefinitionInfo codeReviewLabel = Iterables.getOnlyElement(labels);
    LabelAssert.assertCodeReviewLabel(codeReviewLabel);
  }

  @Test
  public void labelsAreSortedByName() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    configLabel("bar", LabelFunction.NO_OP);
    configLabel("baz", LabelFunction.NO_OP);

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("bar", "baz", "foo").inOrder();
  }

  @Test
  public void labelWithDefaultValue() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set default value
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("foo", labelType -> labelType.setDefaultValue((short) 1));
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.defaultValue).isEqualTo(1);
  }

  @Test
  public void labelWithDescription() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "foo", labelType -> labelType.setDescription(Optional.of("foo label description")));
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.description).isEqualTo("foo label description");
  }

  @Test
  public void labelLimitedToBranches() throws Exception {
    configLabel(
        "foo", LabelFunction.NO_OP, ImmutableList.of("refs/heads/master", "^refs/heads/stable-.*"));

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.branches).containsExactly("refs/heads/master", "^refs/heads/stable-.*");
  }

  @Test
  public void labelWithoutRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // unset rules which are enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "foo",
              labelType -> {
                labelType.setCanOverride(false);
                labelType.setAllowPostSubmit(false);
              });
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.canOverride).isNull();
    assertThat(fooLabel.allowPostSubmit).isNull();
    assertThat(fooLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void labelWithAllRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set rules which are not enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "foo",
              labelType -> {
                labelType.setCopyCondition("is:MIN OR is:MAX");
                labelType.setIgnoreSelfApproval(true);
              });
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.canOverride).isTrue();
    assertThat(fooLabel.copyCondition).isEqualTo("is:MIN OR is:MAX");
    assertThat(fooLabel.allowPostSubmit).isTrue();
    assertThat(fooLabel.ignoreSelfApproval).isTrue();
  }

  @Test
  public void withInheritedLabelsNotAllowed() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // can list labels without inheritance
    @SuppressWarnings("unused")
    var unused = gApi.projects().name(project.get()).labels().get();

    // cannot list labels with inheritance
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(project.get()).labels().withInherited(true).get());
    assertThat(thrown)
        .hasMessageThat()
        .contains("All-Projects: read refs/meta/config not permitted");
  }

  @Test
  public void inheritedLabelsOnly() throws Exception {
    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withInherited(true).get();
    assertThat(labelNames(labels)).containsExactly(LabelId.CODE_REVIEW);

    LabelDefinitionInfo codeReviewLabel = Iterables.getOnlyElement(labels);
    LabelAssert.assertCodeReviewLabel(codeReviewLabel);
  }

  @Test
  public void withInheritedLabels() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    configLabel("bar", LabelFunction.NO_OP);
    configLabel("baz", LabelFunction.NO_OP);

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withInherited(true).get();
    assertThat(labelNames(labels))
        .containsExactly(LabelId.CODE_REVIEW, "bar", "baz", "foo")
        .inOrder();

    LabelAssert.assertCodeReviewLabel(labels.get(0));
    assertThat(labels.get(1).name).isEqualTo("bar");
    assertThat(labels.get(1).projectName).isEqualTo(project.get());
    assertThat(labels.get(2).name).isEqualTo("baz");
    assertThat(labels.get(2).projectName).isEqualTo(project.get());
    assertThat(labels.get(3).name).isEqualTo("foo");
    assertThat(labels.get(3).projectName).isEqualTo(project.get());
  }

  @Test
  public void withInheritedLabelsIncludesGlobalLabel() throws Exception {
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
      List<LabelDefinitionInfo> labels =
          gApi.projects().name(project.get()).labels().withInherited(true).get();
      assertThat(labelNames(labels))
          .containsExactly(globalLabelType.getName(), LabelId.CODE_REVIEW)
          .inOrder();
      assertThat(labels.get(0).projectName).isNull();
      LabelAssert.assertCodeReviewLabel(labels.get(1));
    }
  }

  @Test
  public void withInheritedLabelsAndOverriddenLabel() throws Exception {
    configLabel(LabelId.CODE_REVIEW, LabelFunction.NO_OP);

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withInherited(true).get();
    assertThat(labelNames(labels)).containsExactly(LabelId.CODE_REVIEW, LabelId.CODE_REVIEW);

    LabelAssert.assertCodeReviewLabel(labels.get(0));
    assertThat(labels.get(1).name).isEqualTo(LabelId.CODE_REVIEW);
    assertThat(labels.get(1).projectName).isEqualTo(project.get());
    assertThat(labels.get(1).function).isEqualTo(LabelFunction.NO_OP.getFunctionName());
  }

  @Test
  public void withInheritedLabelsFromMultipleParents() throws Exception {
    configLabel(project, "foo", LabelFunction.NO_OP);

    Project.NameKey childProject =
        projectOperations.newProject().name("child").parent(project).create();
    configLabel(childProject, "bar", LabelFunction.NO_OP);

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(childProject.get()).labels().withInherited(true).get();
    assertThat(labelNames(labels)).containsExactly(LabelId.CODE_REVIEW, "foo", "bar").inOrder();

    LabelAssert.assertCodeReviewLabel(labels.get(0));
    assertThat(labels.get(1).name).isEqualTo("foo");
    assertThat(labels.get(1).projectName).isEqualTo(project.get());
    assertThat(labels.get(2).name).isEqualTo("bar");
    assertThat(labels.get(2).projectName).isEqualTo(childProject.get());
  }

  @Test
  public void voteableOnRefNotFound() throws Exception {
    // Grant permission to read refs/meta/config
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // This should now throw ResourceConflictException since the branch doesn't exist
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .labels()
                    .withVoteableOnRef("non-existing")
                    .get());
    assertThat(thrown).hasMessageThat().contains("ref \"refs/heads/non-existing\" not found");
  }

  @Test
  public void voteableOnRef() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    configLabel("bar", LabelFunction.NO_OP);

    // Grant permissions to read config and vote on 'foo' label with full range (-2 to +2)
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .add(allowLabel("foo").ref("refs/heads/master").group(REGISTERED_USERS).range(-2, 2))
        .update();

    requestScopeOperations.setApiUser(user.id());

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withVoteableOnRef("refs/heads/master").get();

    assertThat(labelNames(labels)).containsExactly("foo");
  }

  @Test
  public void voteableOnRefForChangeOwners() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    configLabel("bar", LabelFunction.NO_OP);

    // Grant permissions to read config and vote on 'foo' label with full range (-2 to +2)
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .add(allowLabel("foo").ref("refs/heads/master").group(CHANGE_OWNER).range(-2, 2))
        .update();

    requestScopeOperations.setApiUser(user.id());

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withVoteableOnRef("refs/heads/master").get();

    assertThat(labelNames(labels)).containsExactly("foo");
  }

  @Test
  public void voteableOnRefRespectsBranchRestrictions() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .add(allow(Permission.CREATE).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    // Create a label that's only valid on master
    configLabel("foo", LabelFunction.NO_OP, ImmutableList.of("refs/heads/master"));

    // Grant permission to vote on 'foo' label on all branches
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("foo").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, 2))
        .update();

    requestScopeOperations.setApiUser(user.id());

    List<LabelDefinitionInfo> labels =
        gApi.projects().name(project.get()).labels().withVoteableOnRef("refs/heads/master").get();
    assertThat(labelNames(labels)).containsExactly("foo");

    createBranch(BranchNameKey.create(project, "refs/heads/develop"));
    labels =
        gApi.projects().name(project.get()).labels().withVoteableOnRef("refs/heads/develop").get();
    assertThat(labels).isEmpty();
  }

  private static List<String> labelNames(List<LabelDefinitionInfo> labels) {
    return labels.stream().map(l -> l.name).collect(toList());
  }
}
