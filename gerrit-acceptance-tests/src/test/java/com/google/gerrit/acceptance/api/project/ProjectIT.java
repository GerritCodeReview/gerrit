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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest  {

  @Test
  public void createProjectFoo() throws RestApiException {
    gApi.projects()
        .name("foo")
        .create();
  }

  @Test(expected = RestApiException.class)
  public void createProjectFooBar() throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = "bar";
    gApi.projects()
        .name("foo")
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
}
