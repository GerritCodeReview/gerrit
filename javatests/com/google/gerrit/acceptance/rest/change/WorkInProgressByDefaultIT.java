// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

public class WorkInProgressByDefaultIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void createChangeWithWorkInProgressByDefaultForProjectDisabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project.get(), "master", "empty change")).get();
    assertThat(info.workInProgress).isNull();
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultForProjectEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForProject(project);
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().workInProgress).isTrue();
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultForUserEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForUser();
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().workInProgress).isTrue();
  }

  @Test
  public void createChangeBypassWorkInProgressByDefaultForProjectEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForProject(project);
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    input.workInProgress = false;
    assertThat(gApi.changes().create(input).get().workInProgress).isNull();
  }

  @Test
  public void createChangeBypassWorkInProgressByDefaultForUserEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForUser();
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    input.workInProgress = false;
    assertThat(gApi.changes().create(input).get().workInProgress).isNull();
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultForProjectInherited() throws Exception {
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject = projectOperations.newProject().parent(parentProject).create();
    setWorkInProgressByDefaultForProject(parentProject);
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(childProject.get(), "master", "empty change")).get();
    assertThat(info.workInProgress).isTrue();
  }

  @Test
  public void pushWithWorkInProgressByDefaultForProjectEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForProject(project);
    assertThat(createChange(project).getChange().change().isWorkInProgress()).isTrue();
  }

  @Test
  public void pushWithWorkInProgressByDefaultForUserEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForUser();
    assertThat(createChange(project).getChange().change().isWorkInProgress()).isTrue();
  }

  @Test
  public void pushBypassWorkInProgressByDefaultForProjectEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForProject(project);
    assertThat(
            createChange(project, "refs/for/master%ready").getChange().change().isWorkInProgress())
        .isFalse();
  }

  @Test
  public void pushBypassWorkInProgressByDefaultForUserEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    setWorkInProgressByDefaultForUser();
    assertThat(
            createChange(project, "refs/for/master%ready").getChange().change().isWorkInProgress())
        .isFalse();
  }

  @Test
  public void pushWithWorkInProgressByDefaultForProjectDisabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    assertThat(createChange(project).getChange().change().isWorkInProgress()).isFalse();
  }

  @Test
  public void pushWorkInProgressByDefaultForProjectInherited() throws Exception {
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject = projectOperations.newProject().parent(parentProject).create();
    setWorkInProgressByDefaultForProject(parentProject);
    assertThat(createChange(childProject).getChange().change().isWorkInProgress()).isTrue();
  }

  @Test
  public void pushNewPatchSetWithWorkInProgressByDefaultForUserEnabled() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();

    // Create change.
    TestRepository<InMemoryRepository> testRepo = cloneProject(project);
    PushOneCommit.Result result =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master");
    result.assertOkStatus();

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().workInProgress).isNull();

    setWorkInProgressByDefaultForUser();

    // Create new patch set on existing change, this shouldn't mark the change as WIP.
    result = pushFactory.create(admin.newIdent(), testRepo, changeId).to("refs/for/master");
    result.assertOkStatus();
    assertThat(gApi.changes().id(changeId).get().workInProgress).isNull();
  }

  @Test
  public void pushNewPatchSetAndNewChangeAtOnceWithWorkInProgressByDefaultForUserEnabled()
      throws Exception {
    Project.NameKey project = projectOperations.newProject().create();

    // Create change.
    TestRepository<InMemoryRepository> testRepo = cloneProject(project);
    RevCommit initialHead = getHead(testRepo.getRepository(), "HEAD");
    RevCommit commit1a =
        testRepo.commit().parent(initialHead).message("Change 1").insertChangeId().create();
    String changeId1 = GitUtil.getChangeId(testRepo, commit1a).get();
    testRepo.reset(commit1a);
    PushResult result = pushHead(testRepo, "refs/for/master", false);
    assertPushOk(result, "refs/for/master");
    assertThat(gApi.changes().id(changeId1).get().workInProgress).isNull();

    setWorkInProgressByDefaultForUser();

    // Clone the repo again. The test connection keeps an AccountState internally, so we need to
    // create a new connection after changing account properties.
    PatchSet.Id ps1OfChange1 =
        PatchSet.id(Change.id(gApi.changes().id(changeId1).get()._number), 1);
    testRepo = cloneProject(project);
    testRepo.git().fetch().setRefSpecs(RefNames.patchSetRef(ps1OfChange1) + ":c1").call();
    testRepo.reset("c1");

    // Create a new patch set on the existing change and in the same push create a new successor
    // change.
    RevCommit commit1b = testRepo.amend(commit1a).create();
    testRepo.reset(commit1b);
    RevCommit commit2 =
        testRepo.commit().parent(commit1b).message("Change 2").insertChangeId().create();
    String changeId2 = GitUtil.getChangeId(testRepo, commit2).get();
    testRepo.reset(commit2);
    result = pushHead(testRepo, "refs/for/master", false);
    assertPushOk(result, "refs/for/master");

    // Check that the existing change (changeId1) is not marked as WIP, but only the newly created
    // change (changeId2).
    assertThat(gApi.changes().id(changeId1).get().workInProgress).isNull();
    assertThat(gApi.changes().id(changeId2).get().workInProgress).isTrue();
  }

  private void setWorkInProgressByDefaultForProject(Project.NameKey p) throws Exception {
    ConfigInput input = new ConfigInput();
    input.workInProgressByDefault = InheritableBoolean.TRUE;
    gApi.projects().name(p.get()).config(input);
  }

  private void setWorkInProgressByDefaultForUser() throws Exception {
    GeneralPreferencesInfo prefs = new GeneralPreferencesInfo();
    prefs.workInProgressByDefault = true;
    gApi.accounts().id(admin.id().get()).setPreferences(prefs);
    // Generate a new API scope. User preferences are stored in IdentifiedUser, so we need to flush
    // that entity.
    requestScopeOperations.resetCurrentApiUser();
  }

  private PushOneCommit.Result createChange(Project.NameKey p) throws Exception {
    return createChange(p, "refs/for/master");
  }

  private PushOneCommit.Result createChange(Project.NameKey p, String r) throws Exception {
    TestRepository<InMemoryRepository> testRepo = cloneProject(p);
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to(r);
    result.assertOkStatus();
    return result;
  }
}
