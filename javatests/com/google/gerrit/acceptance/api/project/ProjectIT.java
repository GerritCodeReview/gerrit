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
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest {
  @Inject private DynamicSet<ProjectIndexedListener> projectIndexedListeners;

  private ProjectIndexedCounter projectIndexedCounter;
  private RegistrationHandle projectIndexedCounterHandle;

  @Before
  public void addProjectIndexedCounter() {
    projectIndexedCounter = new ProjectIndexedCounter();
    projectIndexedCounterHandle = projectIndexedListeners.add(projectIndexedCounter);
  }

  @After
  public void removeProjectIndexedCounter() {
    if (projectIndexedCounterHandle != null) {
      projectIndexedCounterHandle.remove();
    }
  }

  @Test
  public void createProject() throws Exception {
    String name = name("foo");
    assertThat(gApi.projects().create(name).get().name).isEqualTo(name);

    RevCommit head = getRemoteHead(name, RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(name, RefNames.REFS_CONFIG, null, head);

    eventRecorder.assertRefUpdatedEvents(name, "refs/heads/master", new String[] {});
    projectIndexedCounter.assertReindexOf(name);
  }

  @Test
  public void createProjectWithInitialBranches() throws Exception {
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
  public void createProjectWithMismatchedInput() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("foo");
    exception.expect(BadRequestException.class);
    exception.expectMessage("name must match input.name");
    gApi.projects().name("bar").create(in);
  }

  @Test
  public void createProjectNoNameInInput() throws Exception {
    ProjectInput in = new ProjectInput();
    exception.expect(BadRequestException.class);
    exception.expectMessage("input.name is required");
    gApi.projects().create(in);
  }

  @Test
  public void createProjectDuplicate() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    gApi.projects().create(in);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Project already exists");
    gApi.projects().create(in);
  }

  @Test
  public void createAndDeleteBranch() throws Exception {
    assertThat(getRemoteHead(project.get(), "foo")).isNull();

    gApi.projects().name(project.get()).branch("foo").create(new BranchInput());
    assertThat(getRemoteHead(project.get(), "foo")).isNotNull();
    projectIndexedCounter.assertNoReindex();

    gApi.projects().name(project.get()).branch("foo").delete();
    assertThat(getRemoteHead(project.get(), "foo")).isNull();
    projectIndexedCounter.assertNoReindex();
  }

  @Test
  public void createAndDeleteBranchByPush() throws Exception {
    grant(project, "refs/*", Permission.PUSH, true);
    projectIndexedCounter.clear();

    assertThat(getRemoteHead(project.get(), "foo")).isNull();

    PushOneCommit.Result r = pushTo("refs/heads/foo");
    r.assertOkStatus();
    assertThat(getRemoteHead(project.get(), "foo")).isEqualTo(r.getCommit());
    projectIndexedCounter.assertNoReindex();

    PushResult r2 = GitUtil.pushOne(testRepo, null, "refs/heads/foo", false, true, null);
    assertThat(r2.getRemoteUpdate("refs/heads/foo").getStatus()).isEqualTo(Status.OK);
    assertThat(getRemoteHead(project.get(), "foo")).isNull();
    projectIndexedCounter.assertNoReindex();
  }

  @Test
  public void descriptionChangeCausesRefUpdate() throws Exception {
    RevCommit initialHead = getRemoteHead(project, RefNames.REFS_CONFIG);
    assertThat(gApi.projects().name(project.get()).description()).isEmpty();
    DescriptionInput in = new DescriptionInput();
    in.description = "new project description";
    gApi.projects().name(project.get()).description(in);
    assertThat(gApi.projects().name(project.get()).description()).isEqualTo(in.description);

    RevCommit updatedHead = getRemoteHead(project, RefNames.REFS_CONFIG);
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
    RevCommit initialHead = getRemoteHead(project, RefNames.REFS_CONFIG);

    ConfigInfo info = gApi.projects().name(project.get()).config();
    assertThat(info.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    ConfigInput input = new ConfigInput();
    input.submitType = SubmitType.CHERRY_PICK;
    info = gApi.projects().name(project.get()).config(input);
    assertThat(info.submitType).isEqualTo(SubmitType.CHERRY_PICK);
    info = gApi.projects().name(project.get()).config();
    assertThat(info.submitType).isEqualTo(SubmitType.CHERRY_PICK);

    RevCommit updatedHead = getRemoteHead(project, RefNames.REFS_CONFIG);
    eventRecorder.assertRefUpdatedEvents(
        project.get(), RefNames.REFS_CONFIG, initialHead, updatedHead);
  }

  @Test
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
    assertThat(info.state).isEqualTo(input.state);
  }

  @Test
  public void setPartialConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    ConfigInfo info = gApi.projects().name(project.get()).config(input);

    ConfigInput partialInput = new ConfigInput();
    partialInput.useContributorAgreements = InheritableBoolean.FALSE;
    info = gApi.projects().name(project.get()).config(partialInput);

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
    assertThat(info.state).isEqualTo(input.state);
  }

  @Test
  public void nonOwnerCannotSetConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("write config not permitted");
    gApi.projects().name(project.get()).config(input);
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
    exception.expect(UnprocessableEntityException.class);
    gApi.projects().name(project.get()).head("does-not-exist");
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
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("set head not permitted");
    gApi.projects().name(project.get()).head("test");
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

  private static class ProjectIndexedCounter implements ProjectIndexedListener {
    private final AtomicLongMap<String> countsByProject = AtomicLongMap.create();

    @Override
    public void onProjectIndexed(String project) {
      countsByProject.incrementAndGet(project);
    }

    void clear() {
      countsByProject.clear();
    }

    long getCount(String projectName) {
      return countsByProject.get(projectName);
    }

    void assertReindexOf(String projectName) {
      assertReindexOf(projectName, 1);
    }

    void assertReindexOf(String projectName, int expectedCount) {
      assertThat(getCount(projectName)).isEqualTo(expectedCount);
      assertThat(countsByProject).hasSize(1);
      clear();
    }

    void assertNoReindex() {
      assertThat(countsByProject).isEmpty();
    }
  }
}
