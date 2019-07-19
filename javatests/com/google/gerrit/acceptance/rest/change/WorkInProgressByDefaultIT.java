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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class WorkInProgressByDefaultIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

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

  private void setWorkInProgressByDefaultForProject(Project.NameKey p) throws Exception {
    ConfigInput input = new ConfigInput();
    input.workInProgressByDefault = InheritableBoolean.TRUE;
    gApi.projects().name(p.get()).config(input);
  }

  private void setWorkInProgressByDefaultForUser() throws Exception {
    GeneralPreferencesInfo prefs = gApi.accounts().id(admin.id().get()).getPreferences();
    prefs.workInProgressByDefault = true;
    gApi.accounts().id(admin.id().get()).setPreferences(prefs);
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
