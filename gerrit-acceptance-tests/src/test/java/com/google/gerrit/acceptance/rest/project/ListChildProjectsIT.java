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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertThatNameList;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;

import org.junit.Test;

@NoHttpd
public class ListChildProjectsIT extends AbstractDaemonTest {

  @Inject
  private AllUsersName allUsers;

  @Test
  public void listChildrenOfNonExistingProject_NotFound() throws Exception {
    try {
      gApi.projects().name("non-existing").child("children");
    } catch (ResourceNotFoundException e) {
      assertThat(e.getMessage()).contains("non-existing");
    }
  }

  @Test
  public void listNoChildren() throws Exception {
    assertThatNameList(gApi.projects().name(allProjects.get()).children())
        .containsExactly(allUsers, project).inOrder();
  }

  @Test
  public void listChildren() throws Exception {
    Project.NameKey child1 = new Project.NameKey("p1");
    createProject(child1.get());
    Project.NameKey child2 = new Project.NameKey("p2");
    createProject(child2.get());
    Project.NameKey child1_1 = new Project.NameKey("p1.1");
    createProject(child1_1.get(), child1);

    assertThatNameList(gApi.projects().name(allProjects.get()).children())
        .containsExactly(allUsers, project, child1, child2).inOrder();
    assertThatNameList(gApi.projects().name(child1.get()).children())
        .containsExactly(child1_1);
  }

  @Test
  public void listChildrenRecursively() throws Exception {
    Project.NameKey child1 = new Project.NameKey("p1");
    createProject(child1.get());
    createProject("p2");
    Project.NameKey child1_1 = new Project.NameKey("p1.1");
    createProject(child1_1.get(), child1);
    Project.NameKey child1_2 = new Project.NameKey("p1.2");
    createProject(child1_2.get(), child1);
    Project.NameKey child1_1_1 = new Project.NameKey("p1.1.1");
    createProject(child1_1_1.get(), child1_1);
    Project.NameKey child1_1_1_1 = new Project.NameKey("p1.1.1.1");
    createProject(child1_1_1_1.get(), child1_1_1);

    assertThatNameList(gApi.projects().name(child1.get()).children(true))
        .containsExactly(child1_1, child1_1_1, child1_1_1_1, child1_2)
        .inOrder();
  }
}
