// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.gerrit.server.project.ProjectState.INHERITED_FROM_GLOBAL;
import static com.google.gerrit.server.project.ProjectState.INHERITED_FROM_PARENT;
import static com.google.gerrit.server.project.ProjectState.OVERRIDDEN_BY_GLOBAL;
import static com.google.gerrit.server.project.ProjectState.OVERRIDDEN_BY_PARENT;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.api.projects.CommentLinkInput;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class ProjectIT extends AbstractDaemonTest {
  private static final String BUGZILLA = "bugzilla";
  private static final String BUGZILLA_LINK = "http://bugzilla.example.com/?id=$2";
  private static final String BUGZILLA_MATCH = "(bug\\\\s+#?)(\\\\d+)";
  private static final String JIRA = "jira";
  private static final String JIRA_LINK = "http://jira.example.com/?id=$2";
  private static final String JIRA_MATCH = "(jira\\\\s+#?)(\\\\d+)";

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(ProjectConfigEntry.class)
            .annotatedWith(Exports.named("test-plugin-key"))
            .toInstance(new ProjectConfigEntry("Test Plugin Config Item", true));
      }
    };
  }

  @Test
  public void createProject() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {
      String name = name("foo");
      assertThat(gApi.projects().create(name).get().name).isEqualTo(name);

      RevCommit head = getRemoteHead(name, RefNames.REFS_CONFIG);
      eventRecorder.assertRefUpdatedEvents(name, RefNames.REFS_CONFIG, null, head);

      eventRecorder.assertRefUpdatedEvents(name, "refs/heads/master", new String[] {});
      projectIndexedCounter.assertReindexOf(name);
    }
  }

  @Test
  public void createProjectWithInitialBranches() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {

      String name = name("foo");
      ProjectInput input = new ProjectInput();
      input.name = name;
      input.createEmptyCommit = true;
      input.branches = ImmutableList.of("master", "foo");
      assertThat(gApi.projects().create(input).get().name).isEqualTo(name);
      assertThat(
              gApi.projects().name(name).branches().get().stream().map(b -> b.ref).collect(toSet()))
          .containsExactly("refs/heads/foo", "refs/heads/master", "HEAD", RefNames.REFS_CONFIG);

      RevCommit head = getRemoteHead(name, RefNames.REFS_CONFIG);
      eventRecorder.assertRefUpdatedEvents(name, RefNames.REFS_CONFIG, null, head);

      head = getRemoteHead(name, "refs/heads/foo");
      eventRecorder.assertRefUpdatedEvents(name, "refs/heads/foo", null, head);

      head = getRemoteHead(name, "refs/heads/master");
      eventRecorder.assertRefUpdatedEvents(name, "refs/heads/master", null, head);

      projectIndexedCounter.assertReindexOf(name);
    }
  }

  @Test
  public void createProjectWithGitSuffix() throws Exception {
    String name = name("foo");
    assertThat(gApi.projects().create(name + ".git").get().name).isEqualTo(name);

    RevCommit head = getRemoteHead(name, RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(name, RefNames.REFS_CONFIG, null, head);

    eventRecorder.assertRefUpdatedEvents(name, "refs/heads/master", new String[] {});
  }

  @Test
  public void createProjectWithInitialCommit() throws Exception {
    String name = name("foo");
    ProjectInput input = new ProjectInput();
    input.name = name;
    input.createEmptyCommit = true;
    assertThat(gApi.projects().create(input).get().name).isEqualTo(name);

    RevCommit head = getRemoteHead(name, RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(name, RefNames.REFS_CONFIG, null, head);

    head = getRemoteHead(name, "refs/heads/master");
    eventRecorder.assertRefUpdatedEvents(name, "refs/heads/master", null, head);
  }

  @Test
  public void createProjectWithPluginConfigs() throws Exception {
    String name = name("foo");
    ProjectInput input = new ProjectInput();
    input.name = name;
    input.description = "foo description";
    input.pluginConfigValues = newPluginConfigValues();
    ProjectInfo info = gApi.projects().create(input).get();
    assertThat(info.description).isEqualTo(input.description);
  }

  @Test
  public void createProjectWithMismatchedInput() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("foo");
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.projects().name("bar").create(in));
    assertThat(thrown).hasMessageThat().contains("name must match input.name");
  }

  @Test
  public void createProjectNoNameInInput() throws Exception {
    ProjectInput in = new ProjectInput();
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("input.name is required");
  }

  @Test
  public void createProjectDuplicate() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    gApi.projects().create(in);
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("Project already exists");
  }

  @Test
  public void createProjectWithNonExistingParent() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    in.parent = "non-existing";

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("Project Not Found: " + in.parent);
  }

  @Test
  public void createProjectWithSelfAsParentNotPossible() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    in.parent = in.name;

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("Project Not Found: " + in.parent);
  }

  @Test
  public void createProjectUnderAllUsersNotAllowed() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("foo");
    in.parent = allUsers.get();
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.projects().create(in));
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("Cannot inherit from '%s' project", allUsers.get()));
  }

  @Test
  public void createAndDeleteBranch() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {

      assertThat(hasHead(project, "foo")).isFalse();

      gApi.projects().name(project.get()).branch("foo").create(new BranchInput());
      assertThat(getRemoteHead(project.get(), "foo")).isNotNull();
      projectIndexedCounter.assertNoReindex();

      gApi.projects().name(project.get()).branch("foo").delete();
      assertThat(hasHead(project, "foo")).isFalse();
      projectIndexedCounter.assertNoReindex();
    }
  }

  @Test
  public void createAndDeleteBranchByPush() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {
      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.PUSH).ref("refs/*").group(adminGroupUuid()).force(true))
          .update();
      projectIndexedCounter.clear();

      assertThat(hasHead(project, "foo")).isFalse();

      PushOneCommit.Result r = pushTo("refs/heads/foo");
      r.assertOkStatus();
      assertThat(getRemoteHead(project.get(), "foo")).isEqualTo(r.getCommit());
      projectIndexedCounter.assertNoReindex();

      PushResult r2 = GitUtil.pushOne(testRepo, null, "refs/heads/foo", false, true, null);
      assertThat(r2.getRemoteUpdate("refs/heads/foo").getStatus()).isEqualTo(Status.OK);
      assertThat(hasHead(project, "foo")).isFalse();
      projectIndexedCounter.assertNoReindex();
    }
  }

  @Test
  public void descriptionChangeCausesRefUpdate() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(gApi.projects().name(project.get()).description()).isEmpty();
    DescriptionInput in = new DescriptionInput();
    in.description = "new project description";
    gApi.projects().name(project.get()).description(in);
    assertThat(gApi.projects().name(project.get()).description()).isEqualTo(in.description);

    RevCommit updatedHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(
        project.get(), RefNames.REFS_CONFIG, initialHead, updatedHead);
  }

  @Test
  public void descriptionIsDeletedWhenNotSpecified() throws Exception {
    assertThat(gApi.projects().name(project.get()).description()).isEmpty();
    DescriptionInput in = new DescriptionInput();
    in.description = "new project description";
    gApi.projects().name(project.get()).description(in);
    assertThat(gApi.projects().name(project.get()).description()).isEqualTo(in.description);
    in.description = null;
    gApi.projects().name(project.get()).description(in);
    assertThat(gApi.projects().name(project.get()).description()).isEmpty();
  }

  @Test
  public void configChangeCausesRefUpdate() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);

    ConfigInfo info = gApi.projects().name(project.get()).config();
    assertThat(info.defaultSubmitType.value).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    ConfigInput input = new ConfigInput();
    input.submitType = SubmitType.CHERRY_PICK;
    info = gApi.projects().name(project.get()).config(input);
    assertThat(info.defaultSubmitType.value).isEqualTo(SubmitType.CHERRY_PICK);
    info = gApi.projects().name(project.get()).config();
    assertThat(info.defaultSubmitType.value).isEqualTo(SubmitType.CHERRY_PICK);

    RevCommit updatedHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(
        project.get(), RefNames.REFS_CONFIG, initialHead, updatedHead);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void setConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    ConfigInfo info = gApi.projects().name(project.get()).config(input);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.useContributorAgreements.configuredValue)
        .isEqualTo(input.useContributorAgreements);
    assertThat(info.useContentMerge.configuredValue).isEqualTo(input.useContentMerge);
    assertThat(info.useSignedOffBy.configuredValue).isEqualTo(input.useSignedOffBy);
    assertThat(info.createNewChangeForAllNotInTarget.configuredValue)
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(info.requireChangeId.configuredValue).isEqualTo(input.requireChangeId);
    assertThat(info.rejectImplicitMerges.configuredValue).isEqualTo(input.rejectImplicitMerges);
    assertThat(info.enableReviewerByEmail.configuredValue).isEqualTo(input.enableReviewerByEmail);
    assertThat(info.createNewChangeForAllNotInTarget.configuredValue)
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo(input.maxObjectSizeLimit);
    assertThat(info.submitType).isEqualTo(input.submitType);
    assertThat(info.defaultSubmitType.value).isEqualTo(input.submitType);
    assertThat(info.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(info.defaultSubmitType.configuredValue).isEqualTo(input.submitType);
    assertThat(info.state).isEqualTo(input.state);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void setPartialConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    gApi.projects().name(project.get()).config(input);

    ConfigInput partialInput = new ConfigInput();
    partialInput.useContributorAgreements = InheritableBoolean.FALSE;
    ConfigInfo info = gApi.projects().name(project.get()).config(partialInput);

    assertThat(info.description).isNull();
    assertThat(info.useContributorAgreements.configuredValue)
        .isEqualTo(partialInput.useContributorAgreements);
    assertThat(info.useContentMerge.configuredValue).isEqualTo(input.useContentMerge);
    assertThat(info.useSignedOffBy.configuredValue).isEqualTo(input.useSignedOffBy);
    assertThat(info.createNewChangeForAllNotInTarget.configuredValue)
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(info.requireChangeId.configuredValue).isEqualTo(input.requireChangeId);
    assertThat(info.rejectImplicitMerges.configuredValue).isEqualTo(input.rejectImplicitMerges);
    assertThat(info.enableReviewerByEmail.configuredValue).isEqualTo(input.enableReviewerByEmail);
    assertThat(info.createNewChangeForAllNotInTarget.configuredValue)
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo(input.maxObjectSizeLimit);
    assertThat(info.submitType).isEqualTo(input.submitType);
    assertThat(info.defaultSubmitType.value).isEqualTo(input.submitType);
    assertThat(info.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(info.defaultSubmitType.configuredValue).isEqualTo(input.submitType);
    assertThat(info.state).isEqualTo(input.state);
  }

  @Test
  public void nonOwnerCannotSetConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).config(input));
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void setHead() throws Exception {
    assertThat(gApi.projects().name(project.get()).head()).isEqualTo("refs/heads/master");
    gApi.projects().name(project.get()).branch("test1").create(new BranchInput());
    gApi.projects().name(project.get()).branch("test2").create(new BranchInput());
    for (String head : new String[] {"test1", "refs/heads/test2"}) {
      gApi.projects().name(project.get()).head(head);
      assertThat(gApi.projects().name(project.get()).head()).isEqualTo(RefNames.fullName(head));
    }
  }

  @Test
  public void setHeadToNonexistentBranch() throws Exception {
    assertThrows(
        UnprocessableEntityException.class,
        () -> gApi.projects().name(project.get()).head("does-not-exist"));
  }

  @Test
  public void setHeadToSameBranch() throws Exception {
    gApi.projects().name(project.get()).branch("test").create(new BranchInput());
    for (String head : new String[] {"test", "refs/heads/test"}) {
      gApi.projects().name(project.get()).head(head);
      assertThat(gApi.projects().name(project.get()).head()).isEqualTo(RefNames.fullName(head));
    }
  }

  @Test
  public void setHeadNotAllowed() throws Exception {
    gApi.projects().name(project.get()).branch("test").create(new BranchInput());
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).head("test"));
    assertThat(thrown).hasMessageThat().contains("not permitted: set HEAD on refs/heads/test");
  }

  @Test
  public void nonActiveProjectCanBeMadeActive() throws Exception {
    for (ProjectState nonActiveState :
        ImmutableList.of(ProjectState.READ_ONLY, ProjectState.HIDDEN)) {
      // ACTIVE => NON_ACTIVE
      ConfigInput ci1 = new ConfigInput();
      ci1.state = nonActiveState;
      gApi.projects().name(project.get()).config(ci1);
      assertThat(gApi.projects().name(project.get()).config().state).isEqualTo(nonActiveState);
      // NON_ACTIVE => ACTIVE
      ConfigInput ci2 = new ConfigInput();
      ci2.state = ProjectState.ACTIVE;
      gApi.projects().name(project.get()).config(ci2);
      // ACTIVE is represented as null in the API
      assertThat(gApi.projects().name(project.get()).config().state).isNull();
    }
  }

  @Test
  public void nonActiveProjectCanBeMadeActiveByHostAdmin() throws Exception {
    // ACTIVE => HIDDEN
    ConfigInput ci1 = new ConfigInput();
    ci1.state = ProjectState.HIDDEN;
    gApi.projects().name(project.get()).config(ci1);
    assertThat(gApi.projects().name(project.get()).config().state).isEqualTo(ProjectState.HIDDEN);

    // Revoke OWNER permission for admin and block them from reading the project's refs
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            block(Permission.OWNER)
                .ref(RefNames.REFS + "*")
                .group(SystemGroupBackend.REGISTERED_USERS))
        .add(
            block(Permission.READ)
                .ref(RefNames.REFS + "*")
                .group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    // HIDDEN => ACTIVE
    ConfigInput ci2 = new ConfigInput();
    ci2.state = ProjectState.ACTIVE;
    gApi.projects().name(project.get()).config(ci2);
    // ACTIVE is represented as null in the API
    assertThat(gApi.projects().name(project.get()).config().state).isNull();
  }

  @Test
  public void reindexProject() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {

      projectOperations.newProject().parent(project).create();
      projectIndexedCounter.clear();

      gApi.projects().name(allProjects.get()).index(false);
      projectIndexedCounter.assertReindexOf(allProjects.get());
    }
  }

  @Test
  public void reindexProjectWithChildren() throws Exception {
    ProjectIndexedCounter projectIndexedCounter = new ProjectIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectIndexedCounter)) {

      Project.NameKey middle = projectOperations.newProject().parent(project).create();
      Project.NameKey leave = projectOperations.newProject().parent(middle).create();
      projectIndexedCounter.clear();

      gApi.projects().name(project.get()).index(true);
      projectIndexedCounter.assertReindexExactly(
          ImmutableMap.of(project.get(), 1L, middle.get(), 1L, leave.get(), 1L));
    }
  }

  @Test
  public void reindexChangesOfProject() throws Exception {
    Change.Id changeId1 = createChange().getChange().getId();
    Change.Id changeId2 = createChange().getChange().getId();

    ChangeIndexedListener changeIndexedListener = mock(ChangeIndexedListener.class);
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedListener)) {
      gApi.projects().name(project.get()).indexChanges();

      verify(changeIndexedListener, times(1))
          .onChangeScheduledForIndexing(project.get(), changeId1.get());
      verify(changeIndexedListener, times(1))
          .onChangeScheduledForIndexing(project.get(), changeId2.get());
    }
  }

  @Test
  public void maxObjectSizeIsNotSetByDefault() throws Exception {
    ConfigInfo info = getConfig();
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeCanBeSetAndCleared() throws Exception {
    // Set a value
    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    // Clear the value
    info = setMaxObjectSize("0");
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeIsInheritedFromParentProject() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary)
        .isEqualTo(String.format(INHERITED_FROM_PARENT, project));
  }

  @Test
  public void maxObjectSizeIsNotInheritedFromParentProject() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeOverridesParentProjectWhenNotSetOnParent() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("0");
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeOverridesParentProjectWhenLower() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeDoesNotOverrideParentProjectWhenHigher() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary)
        .isEqualTo(String.format(OVERRIDDEN_BY_PARENT, project));
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  public void maxObjectSizeIsInheritedFromGlobalConfig() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = getConfig();
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(INHERITED_FROM_GLOBAL);

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(INHERITED_FROM_GLOBAL);
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  public void maxObjectSizeOverridesGlobalConfigWhenLower() throws Exception {
    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "300k")
  public void inheritedMaxObjectSizeOverridesGlobalConfigWhenLower() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeDoesNotOverrideGlobalConfigWhenHigher() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();

    ConfigInfo info = setMaxObjectSize("300k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("300k");
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(OVERRIDDEN_BY_GLOBAL);

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(OVERRIDDEN_BY_GLOBAL);
  }

  @Test
  public void invalidMaxObjectSizeIsRejected() throws Exception {
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> setMaxObjectSize("100 foo"));
    assertThat(thrown).hasMessageThat().contains("100 foo");
  }

  @Test
  public void pluginConfigsReturnedWhenRefsMetaConfigReadable() throws Exception {
    ProjectConfigEntry entry = new ProjectConfigEntry("enabled", "true");
    try (Registration ignored =
        extensionRegistry.newRegistration().add(entry, "test-config-entry")) {
      // The admin can see refs/meta/config and hence has the READ_CONFIG permission.
      requestScopeOperations.setApiUser(admin.id());
      ConfigInfo configInfo = getConfig();
      assertThat(configInfo.pluginConfig).isNotNull();
      assertThat(configInfo.pluginConfig).isNotEmpty();
    }
  }

  @Test
  public void pluginConfigsNotReturnedWhenRefsMetaConfigNotReadable() throws Exception {
    ProjectConfigEntry entry = new ProjectConfigEntry("enabled", "true");
    try (Registration ignored =
        extensionRegistry.newRegistration().add(entry, "test-config-entry")) {
      // This user cannot see refs/meta/config and hence does not have the READ_CONFIG permission.
      requestScopeOperations.setApiUser(user.id());
      ConfigInfo configInfo = getConfig();
      assertThat(configInfo.pluginConfig).isNull();
    }
  }

  @Test
  public void noCommentlinksByDefault() throws Exception {
    assertThat(getConfig().commentlinks).isEmpty();
  }

  @Test
  @GerritConfig(name = "commentlink.bugzilla.match", value = BUGZILLA_MATCH)
  @GerritConfig(name = "commentlink.bugzilla.link", value = BUGZILLA_LINK)
  @GerritConfig(name = "commentlink.jira.match", value = JIRA_MATCH)
  @GerritConfig(name = "commentlink.jira.link", value = JIRA_LINK)
  public void projectConfigUsesCommentlinksFromGlobalConfig() throws Exception {
    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(getConfig(), expected);
  }

  @Test
  public void projectConfigUsesLocallySetCommentlinks() throws Exception {
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK);
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(info, expected);
    assertCommentLinks(getConfig(), expected);
  }

  @Test
  @GerritConfig(name = "commentlink.bugzilla.match", value = BUGZILLA_MATCH)
  @GerritConfig(name = "commentlink.bugzilla.link", value = BUGZILLA_LINK)
  public void projectConfigUsesCommentLinksFromGlobalAndLocal() throws Exception {
    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    assertCommentLinks(getConfig(), expected);

    ConfigInput input = new ConfigInput();
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));

    assertCommentLinks(info, expected);
    assertCommentLinks(getConfig(), expected);
  }

  @Test
  @GerritConfig(name = "commentlink.bugzilla.match", value = BUGZILLA_MATCH)
  @GerritConfig(name = "commentlink.bugzilla.link", value = BUGZILLA_LINK)
  public void localCommentLinkOverridesGlobalConfig() throws Exception {
    String otherLink = "https://other.example.com";
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, otherLink);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, otherLink));

    ConfigInfo info = setConfig(project, input);
    assertCommentLinks(info, expected);
    assertCommentLinks(getConfig(), expected);
  }

  @Test
  public void localCommentLinksAreInheritedFromParent() throws Exception {
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK);
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(info, expected);

    Project.NameKey child = projectOperations.newProject().parent(project).create();
    assertCommentLinks(getConfig(child), expected);
  }

  @Test
  public void localCommentLinkOverridesParentCommentLink() throws Exception {
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK);
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(info, expected);

    Project.NameKey child = projectOperations.newProject().parent(project).create();

    String otherLink = "https://other.example.com";
    input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, otherLink);
    info = setConfig(child, input);

    expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, otherLink));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));

    assertCommentLinks(getConfig(child), expected);
  }

  @Test
  public void updateExistingCommentLink() throws Exception {
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK);
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(info, expected);

    String otherLink = "https://other.example.com";
    input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, otherLink);
    info = setConfig(project, input);

    expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, otherLink));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(getConfig(project), expected);
  }

  @Test
  public void removeCommentLink() throws Exception {
    ConfigInput input = new ConfigInput();
    addCommentLink(input, BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK);
    addCommentLink(input, JIRA, JIRA_MATCH, JIRA_LINK);
    ConfigInfo info = setConfig(project, input);

    Map<String, CommentLinkInfo> expected = new HashMap<>();
    expected.put(BUGZILLA, commentLinkInfo(BUGZILLA, BUGZILLA_MATCH, BUGZILLA_LINK));
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(info, expected);

    input = new ConfigInput();
    addCommentLink(input, BUGZILLA, null);
    info = setConfig(project, input);

    expected = new HashMap<>();
    expected.put(JIRA, commentLinkInfo(JIRA, JIRA_MATCH, JIRA_LINK));
    assertCommentLinks(getConfig(project), expected);
  }

  @Test
  public void cannotPushLabelDefinitionWithDuplicateValues() throws Exception {
    Config cfg = new Config();
    cfg.fromText(projectOperations.project(allProjects).getConfig().toText());
    cfg.setStringList(
        "label",
        "Code-Review",
        "value",
        ImmutableList.of("+1 LGTM", "1 LGTM", "0 No Value", "-1 Looks Bad"));

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects);
    GitUtil.fetch(repo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    repo.reset(RefNames.REFS_CONFIG);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), repo, "Subject", "project.config", cfg.toText())
            .to(RefNames.REFS_CONFIG);
    r.assertErrorStatus("invalid project configuration");
    r.assertMessage("project.config: duplicate value \"1 lgtm\" for label \"code-review\"");
  }

  @Test
  public void getProjectThatHasLabelDefinitionWithDuplicateValues() throws Exception {
    // Update the definition of the Code-Review label so that it has the value "+1 LGTM" twice.
    // This update bypasses all validation checks so that the duplicate label value doesn't get
    // rejected.
    projectOperations
        .project(allProjects)
        .forInvalidation()
        .addProjectConfigUpdater(
            cfg ->
                cfg.setStringList(
                    "label",
                    "Code-Review",
                    "value",
                    ImmutableList.of("+1 LGTM", "1 LGTM", "0 No Value", "-1 Looks Bad")))
        .invalidate();

    // Verify that project info can be retrieved and that the label value "+1 LGTM" appears only
    // once.
    ProjectInfo projectInfo = gApi.projects().name(allProjects.get()).get();
    assertThat(projectInfo.labels.keySet()).containsExactly("Code-Review");
    assertThat(projectInfo.labels.get("Code-Review").values)
        .containsExactly("+1", "LGTM", " 0", "No Value", "-1", "Looks Bad");
  }

  @Test
  public void getProjectThatHasInvalidProjectConfig() throws Exception {
    projectOperations
        .project(allProjects)
        .forInvalidation()
        .makeProjectConfigInvalid()
        .invalidate();

    // We must test this via the REST API since ExceptionHook is not invoked from the Java API.
    RestResponse r = adminRestSession.get("/projects/" + allProjects.get());
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains(
            String.format(
                "Invalid config file %s in project %s in branch %s in commit %s: "
                    + "Bad entry delimiter\n"
                    + "Please contact the project owner.",
                ProjectConfig.PROJECT_CONFIG,
                allProjects.get(),
                RefNames.REFS_CONFIG,
                projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).name()));
  }

  private CommentLinkInfo commentLinkInfo(String name, String match, String link) {
    CommentLinkInfo info = new CommentLinkInfo();
    info.name = name;
    info.match = match;
    info.link = link;
    return info;
  }

  private void assertCommentLinks(ConfigInfo actual, Map<String, CommentLinkInfo> expected) {
    assertThat(actual.commentlinks).containsExactlyEntriesIn(expected);
  }

  private void addCommentLink(ConfigInput configInput, String name, String match, String link) {
    CommentLinkInput commentLinkInput = new CommentLinkInput();
    commentLinkInput.match = match;
    commentLinkInput.link = link;
    addCommentLink(configInput, name, commentLinkInput);
  }

  private void addCommentLink(
      ConfigInput configInput, String name, CommentLinkInput commentLinkInput) {
    if (configInput.commentLinks == null) {
      configInput.commentLinks = new HashMap<>();
    }
    configInput.commentLinks.put(name, commentLinkInput);
  }

  private ConfigInfo setConfig(Project.NameKey name, ConfigInput input) throws Exception {
    return gApi.projects().name(name.get()).config(input);
  }

  private ConfigInfo getConfig(Project.NameKey name) throws Exception {
    return gApi.projects().name(name.get()).config();
  }

  private ConfigInfo getConfig() throws Exception {
    return getConfig(project);
  }

  private ConfigInput createTestConfigInput() {
    ConfigInput input = new ConfigInput();
    input.description = "some description";
    input.useContributorAgreements = InheritableBoolean.TRUE;
    input.useContentMerge = InheritableBoolean.TRUE;
    input.useSignedOffBy = InheritableBoolean.TRUE;
    input.createNewChangeForAllNotInTarget = InheritableBoolean.TRUE;
    input.requireChangeId = InheritableBoolean.TRUE;
    input.rejectImplicitMerges = InheritableBoolean.TRUE;
    input.enableReviewerByEmail = InheritableBoolean.TRUE;
    input.createNewChangeForAllNotInTarget = InheritableBoolean.TRUE;
    input.maxObjectSizeLimit = "5m";
    input.submitType = SubmitType.CHERRY_PICK;
    input.state = ProjectState.HIDDEN;
    return input;
  }

  private ConfigInfo setMaxObjectSize(String value) throws Exception {
    return setMaxObjectSize(project, value);
  }

  private ConfigInfo setMaxObjectSize(Project.NameKey name, String value) throws Exception {
    ConfigInput input = new ConfigInput();
    input.maxObjectSizeLimit = value;
    return setConfig(name, input);
  }

  private static Map<String, Map<String, ConfigValue>> newPluginConfigValues() {
    Map<String, Map<String, ConfigValue>> pluginConfigValues = new HashMap<>();
    Map<String, ConfigValue> configValues = new HashMap<>();
    ConfigValue value = new ConfigValue();
    value.value = "true";
    configValues.put("test-plugin-key", value);
    pluginConfigValues.put("gerrit", configValues);
    return pluginConfigValues;
  }

  private static class ProjectIndexedCounter implements ProjectIndexedListener {
    private final AtomicLongMap<String> countsByProject = AtomicLongMap.create();

    @Override
    public void onProjectIndexed(String project) {
      countsByProject.incrementAndGet(project);
    }

    void clear() {
      countsByProject.clear();
    }

    void assertReindexOf(String projectName) {
      assertReindexOf(projectName, 1);
    }

    void assertReindexOf(String projectName, long expectedCount) {
      assertThat(countsByProject.asMap()).containsExactly(projectName, expectedCount);
      clear();
    }

    void assertNoReindex() {
      assertThat(countsByProject.asMap()).isEmpty();
    }

    void assertReindexExactly(ImmutableMap<String, Long> expected) {
      assertThat(countsByProject.asMap()).containsExactlyEntriesIn(expected);
      clear();
    }
  }

  protected RevCommit getRemoteHead(String project, String branch) throws Exception {
    return projectOperations.project(Project.nameKey(project)).getHead(branch);
  }

  boolean hasHead(Project.NameKey k, String b) {
    return projectOperations.project(k).hasHead(b);
  }
}
