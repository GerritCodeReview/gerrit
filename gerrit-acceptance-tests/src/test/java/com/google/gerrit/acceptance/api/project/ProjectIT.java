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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest  {

  @Test
  public void createProjectFoo() throws Exception {
    String name = name("foo");
    assertThat(name).isEqualTo(
        gApi.projects()
            .create(name)
            .get()
            .name);
  }

  @Test
  public void createProjectFooWithGitSuffix() throws Exception {
    String name = name("foo");
    assertThat(name).isEqualTo(
        gApi.projects()
            .create(name + ".git")
            .get()
            .name);
  }

  @Test
  public void createProjectWithMismatchedInput() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("foo");
    exception.expect(BadRequestException.class);
    exception.expectMessage("name must match input.name");
    gApi.projects()
        .name("bar")
        .create(in);
  }

  @Test
  public void createProjectDuplicate() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    gApi.projects()
        .create(in);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Project already exists");
    gApi.projects()
        .create(in);
  }

  @Test
  public void createBranch() throws Exception {
    allow(Permission.READ, ANONYMOUS_USERS, "refs/*");
    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());
  }

  @Test
  public void description() throws Exception {
    RevCommit initialHead = getRemoteHead(project, "refs/meta/config");
    assertThat(gApi.projects()
            .name(project.get())
            .description())
        .isEmpty();
    DescriptionInput in = new DescriptionInput();
    in.description = "new project description";
    gApi.projects()
        .name(project.get())
        .description(in);
    assertThat(gApi.projects()
            .name(project.get())
            .description())
        .isEqualTo(in.description);

    RevCommit updatedHead = getRemoteHead(project, "refs/meta/config");
    eventRecorder.assertRefUpdatedEvents(project.get(), "refs/meta/config",
        initialHead, updatedHead);
  }

  @Test
  public void config() throws Exception {
    RevCommit initialHead = getRemoteHead(project, "refs/meta/config");

    ConfigInfo info = gApi.projects().name(project.get()).config();
    assertThat(info.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    ConfigInput input = new ConfigInput();
    input.submitType = SubmitType.CHERRY_PICK;
    info = gApi.projects().name(project.get()).config(input);
    assertThat(info.submitType).isEqualTo(SubmitType.CHERRY_PICK);
    info = gApi.projects().name(project.get()).config();
    assertThat(info.submitType).isEqualTo(SubmitType.CHERRY_PICK);

    RevCommit updatedHead = getRemoteHead(project, "refs/meta/config");
    eventRecorder.assertRefUpdatedEvents(project.get(), "refs/meta/config",
        initialHead, updatedHead);
  }
}
