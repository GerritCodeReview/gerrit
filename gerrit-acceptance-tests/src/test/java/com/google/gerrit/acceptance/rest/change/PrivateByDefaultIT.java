// Copyright (C) 2017 The Android Open Source Project
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

public class PrivateByDefaultIT extends AbstractDaemonTest {
  private Project.NameKey project1;
  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    project1 = createProject("project-1");
    project2 = createProject("project-2", project1);
    setPrivateByDefault(project1, InheritableBoolean.FALSE);
  }

  @Test
  public void createChangeWithPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().isPrivate).isEqualTo(true);
  }

  @Test
  public void createChangeBypassPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    input.isPrivate = false;
    assertThat(gApi.changes().create(input).get().isPrivate).isNull();
  }

  @Test
  public void createChangeWithPrivateByDefaultDisabled() throws Exception {
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project2.get(), "master", "empty change")).get();
    assertThat(info.isPrivate).isNull();
  }

  @Test
  public void createChangeWithPrivateByDefaultInherited() throws Exception {
    setPrivateByDefault(project1, InheritableBoolean.TRUE);
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project2.get(), "master", "empty change")).get();
    assertThat(info.isPrivate).isTrue();
  }

  @Test
  public void pushWithPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isPrivate()).isEqualTo(true);
  }

  @Test
  public void pushBypassPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    assertThat(
            createChange(project2, "refs/for/master%remove-private")
                .getChange()
                .change()
                .isPrivate())
        .isEqualTo(false);
  }

  @Test
  public void pushWithPrivateByDefaultDisabled() throws Exception {
    assertThat(createChange(project2).getChange().change().isPrivate()).isEqualTo(false);
  }

  @Test
  public void pushBypassPrivateByDefaultInherited() throws Exception {
    setPrivateByDefault(project1, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isPrivate()).isEqualTo(true);
  }

  private void setPrivateByDefault(Project.NameKey proj, InheritableBoolean value)
      throws Exception {
    ConfigInput input = new ConfigInput();
    input.privateByDefault = value;
    gApi.projects().name(proj.get()).config(input);
  }

  private PushOneCommit.Result createChange(Project.NameKey proj) throws Exception {
    return createChange(proj, "refs/for/master");
  }

  private PushOneCommit.Result createChange(Project.NameKey proj, String ref) throws Exception {
    TestRepository<InMemoryRepository> testRepo = cloneProject(proj);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }
}
