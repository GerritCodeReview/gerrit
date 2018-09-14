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
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class PrivateByDefaultIT extends AbstractDaemonTest {
  private Project.NameKey project1;
  private Project.NameKey project2;
  @Inject private ProjectOperations projectOperations;

  @Before
  public void setUp() throws Exception {
    project1 = projectOperations.newProject().create();
    project2 = projectOperations.newProject().parent(project1).create();
    setPrivateByDefault(project1, InheritableBoolean.FALSE);
  }

  @Test
  public void createChangeWithPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().isPrivate).isTrue();
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
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void createChangeWithPrivateByDefaultAndDisablePrivateChangesTrue() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);

    ChangeInput input = new ChangeInput(project2.get(), "master", "empty change");
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("private changes are disabled");
    gApi.changes().create(input);
  }

  @Test
  public void pushWithPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isPrivate()).isTrue();
  }

  @Test
  public void pushBypassPrivateByDefaultEnabled() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);
    assertThat(
            createChange(project2, "refs/for/master%remove-private")
                .getChange()
                .change()
                .isPrivate())
        .isFalse();
  }

  @Test
  public void pushWithPrivateByDefaultDisabled() throws Exception {
    assertThat(createChange(project2).getChange().change().isPrivate()).isFalse();
  }

  @Test
  public void pushBypassPrivateByDefaultInherited() throws Exception {
    setPrivateByDefault(project1, InheritableBoolean.TRUE);
    assertThat(createChange(project2).getChange().change().isPrivate()).isTrue();
  }

  @Test
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void pushPrivatesWithPrivateByDefaultAndDisablePrivateChangesTrue() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);

    TestRepository<InMemoryRepository> testRepo = cloneProject(project2);
    PushOneCommit.Result result =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master%private");
    result.assertErrorStatus();
  }

  @Test
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void pushDraftsWithPrivateByDefaultAndDisablePrivateChangesTrue() throws Exception {
    setPrivateByDefault(project2, InheritableBoolean.TRUE);

    RevCommit initialHead = getRemoteHead(project2, "master");
    TestRepository<InMemoryRepository> testRepo = cloneProject(project2);
    PushOneCommit.Result result =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master%draft");
    result.assertErrorStatus();

    testRepo.reset(initialHead);
    result = pushFactory.create(admin.newIdent(), testRepo).to("refs/drafts/master");
    result.assertErrorStatus();
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
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }
}
