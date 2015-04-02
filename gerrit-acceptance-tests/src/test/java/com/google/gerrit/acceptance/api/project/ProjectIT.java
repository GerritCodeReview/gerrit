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
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.PutDescriptionInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;

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

  @Test(expected = RestApiException.class)
  public void createProjectFooBar() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("foo");
    gApi.projects()
        .name("bar")
        .create(in);
  }

  @Test(expected = ResourceConflictException.class)
  public void createProjectDuplicate() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("baz");
    gApi.projects()
        .create(in);
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
    assertThat(gApi.projects()
            .name(project.get())
            .description())
        .isEmpty();
    PutDescriptionInput in = new PutDescriptionInput();
    in.description = "new project description";
    gApi.projects()
        .name(project.get())
        .description(in);
    assertThat(gApi.projects()
            .name(project.get())
            .description())
        .isEqualTo(in.description);
  }
}
