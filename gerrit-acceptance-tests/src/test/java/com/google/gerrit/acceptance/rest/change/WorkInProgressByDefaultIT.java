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
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public class WorkInProgressByDefaultIT extends AbstractDaemonTest {
  private Project.NameKey project1;
  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    project1 = createProject("project-1");
    project2 = createProject("project-2", project1);
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultDisabled() throws Exception {
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project2.get(), "master", "empty change")).get();
    assertThat(info.workInProgress).isNull();
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultEnabled() throws Exception {
    setWorkInProgressByDefault(project2, InheritableBoolean.TRUE);
    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().workInProgress).isEqualTo(true);
  }

  @Test
  public void createChangeBypassWorkInProgressByDefaultEnabled() throws Exception {
    setWorkInProgressByDefault(project2, InheritableBoolean.TRUE);
    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    input.workInProgress = false;
    assertThat(gApi.changes().create(input).get().workInProgress).isNull();
  }

  @Test
  public void createChangeWithWorkInProgressByDefaultInherited() throws Exception {
    setWorkInProgressByDefault(project1, InheritableBoolean.TRUE);
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project2.get(), "master", "empty change")).get();
    assertThat(info.workInProgress).isTrue();
  }

  @Test
  public void pushWithWorkInProgressByDefaultEnabled() throws Exception {
    setWorkInProgressByDefault(project2, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isWorkInProgress()).isEqualTo(true);
  }

  @Test
  public void pushBypassWorkInProgressByDefaultEnabled() throws Exception {
    setWorkInProgressByDefault(project2, InheritableBoolean.TRUE);
    assertThat(
            createChange(project2, "refs/for/master%ready").getChange().change().isWorkInProgress())
        .isEqualTo(false);
  }

  @Test
  public void pushWithWorkInProgressByDefaultDisabled() throws Exception {
    assertThat(createChange(project2).getChange().change().isWorkInProgress()).isEqualTo(false);
  }

  @Test
  public void pushBypassWorkInProgressByDefaultInherited() throws Exception {
    setWorkInProgressByDefault(project1, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isWorkInProgress()).isEqualTo(true);
  }

  private void setWorkInProgressByDefault(Project.NameKey p, InheritableBoolean v)
      throws Exception {
    ConfigInput input = new ConfigInput();
    input.workInProgressByDefault = v;
    gApi.projects().name(p.get()).config(input);
  }

  private PushOneCommit.Result createChange(Project.NameKey p) throws Exception {
    return createChange(p, "refs/for/master");
  }

  private PushOneCommit.Result createChange(Project.NameKey p, String r) throws Exception {
    TestRepository<InMemoryRepository> testRepo = cloneProject(p);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result result = push.to(r);
    result.assertOkStatus();
    return result;
  }
}
