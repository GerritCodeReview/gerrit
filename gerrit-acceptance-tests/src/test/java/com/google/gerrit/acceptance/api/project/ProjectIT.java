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
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.junit.Test;

import java.util.List;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest  {

  @Test
  public void createProjectFoo() throws Exception {
    String name = "foo";
    assertThat(name).isEqualTo(
        gApi.projects()
            .name(name)
            .create()
            .get()
            .name);
  }

  @Test
  public void createProjectFooWithGitSuffix() throws Exception {
    String name = "foo";
    assertThat(name).isEqualTo(
        gApi.projects()
            .name(name + ".git")
            .create()
            .get()
            .name);
  }

  @Test(expected = RestApiException.class)
  public void createProjectFooBar() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = "foo";
    gApi.projects()
        .name("bar")
        .create(in);
  }

  @Test(expected = ResourceConflictException.class)
  public void createProjectDuplicate() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = "baz";
    gApi.projects()
        .name("baz")
        .create(in);
    gApi.projects()
        .name("baz")
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
  public void listProjects() throws Exception {
    List<ProjectInfo> initialProjects = gApi.projects().list().get();

    gApi.projects().name("foo").create();
    gApi.projects().name("bar").create();

    List<ProjectInfo> allProjects = gApi.projects().list().get();
    assertThat(allProjects).hasSize(initialProjects.size() + 2);

    List<ProjectInfo> projectsWithDescription = gApi.projects().list()
        .withDescription(true)
        .get();
    assertThat(projectsWithDescription.get(0).description).isNotNull();

    List<ProjectInfo> projectsWithoutDescription = gApi.projects().list()
        .withDescription(false)
        .get();
    assertThat(projectsWithoutDescription.get(0).description).isNull();

    List<ProjectInfo> noMatchingProjects = gApi.projects().list()
        .withPrefix("fox")
        .get();
    assertThat(noMatchingProjects).isEmpty();

    List<ProjectInfo> matchingProject = gApi.projects().list()
        .withPrefix("fo")
        .get();
    assertThat(matchingProject).hasSize(1);

    List<ProjectInfo> limitOneProject = gApi.projects().list()
        .withLimit(1)
        .get();
    assertThat(limitOneProject).hasSize(1);

    List<ProjectInfo> startAtOneProjects = gApi.projects().list()
        .withStart(1)
        .get();
    assertThat(startAtOneProjects).hasSize(allProjects.size() - 1);
  }
}
