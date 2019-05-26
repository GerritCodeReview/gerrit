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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectInfo;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class GetChildProjectIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void getNonExistingChildProject_NotFound() throws Exception {
    assertChildNotFound(allProjects, "non-existing");
  }

  @Test
  public void getNonChildProject_NotFound() throws Exception {
    Project.NameKey p1 = projectOperations.newProject().create();
    Project.NameKey p2 = projectOperations.newProject().create();

    assertChildNotFound(p1, p2.get());
  }

  @Test
  public void getChildProject() throws Exception {
    Project.NameKey child = projectOperations.newProject().create();
    ProjectInfo childInfo = gApi.projects().name(allProjects.get()).child(child.get()).get();

    assertProjectInfo(projectCache.get(child).getProject(), childInfo);
  }

  @Test
  public void getGrandChildProject_NotFound() throws Exception {
    Project.NameKey child = projectOperations.newProject().create();
    Project.NameKey grandChild = projectOperations.newProject().parent(child).create();

    assertChildNotFound(allProjects, grandChild.get());
  }

  @Test
  public void getGrandChildProjectWithRecursiveFlag() throws Exception {
    Project.NameKey child = projectOperations.newProject().create();
    Project.NameKey grandChild = projectOperations.newProject().parent(child).create();

    ProjectInfo grandChildInfo =
        gApi.projects().name(allProjects.get()).child(grandChild.get()).get(true);
    assertProjectInfo(projectCache.get(grandChild).getProject(), grandChildInfo);
  }

  private void assertChildNotFound(Project.NameKey parent, String child) throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(parent.get()).child(child).get());
    assertThat(thrown).hasMessageThat().contains(child);
  }
}
