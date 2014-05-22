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

import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest  {

  @Test
  public void createProjectFoo() throws RestApiException {
    String name = "foo";
    assertEquals(name,
        gApi.projects()
            .name(name)
            .create()
            .get()
            .name);
  }

  @Test(expected = RestApiException.class)
  public void createProjectFooBar() throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = "foo";
    gApi.projects()
        .name("bar")
        .create(in);
  }

  @Test(expected = ResourceConflictException.class)
  public void createProjectDuplicate() throws RestApiException {
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
  public void createBranch() throws GitAPIException,
      IOException, RestApiException {
    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());
  }

  @Test
  public void listProjects() throws Exception {
    List<ProjectInfo> initialProjects = gApi.projects().list().get();

    createProject(sshSession, "foo");
    createProject(sshSession, "bar");

    List<ProjectInfo> results = gApi.projects().list().get();
    assertEquals(initialProjects.size() + 2, results.size());
  }

  @Test
  public void listProjectsDescription() throws Exception {
    List<ProjectInfo> projectsWithDescription = gApi.projects().list()
        .withDescription(true)
        .get();
    assertNotNull(projectsWithDescription.get(0).description);

    List<ProjectInfo> projectsWithoutDescription = gApi.projects().list()
        .withDescription(false)
        .get();
    assertNull(projectsWithoutDescription.get(0).description);
  }

  @Test
  public void listProjectsNotMatchingPrefix() throws Exception {
    List<ProjectInfo> initialProjects = gApi.projects().list()
        .withPrefix("fox")
        .get();
    assertEquals(0, initialProjects.size());
  }

  @Test
  public void listProjectsMatchingPrefix() throws Exception {
    createProject(sshSession, "foo");
    List<ProjectInfo> initialProjects = gApi.projects().list()
        .withPrefix("fo")
        .get();
    assertEquals(1, initialProjects.size());
  }

  @Test
  public void listProjectsLimit() throws Exception {
    List<ProjectInfo> initialProjects = gApi.projects().list()
        .withLimit(1)
        .get();
    assertEquals(1, initialProjects.size());
  }

  @Test
  public void listProjectsStart() throws Exception {
    List<ProjectInfo> initialProjects = gApi.projects().list()
        .withStart(1)
        .get();
    assertEquals(1, initialProjects.size());
  }
}
