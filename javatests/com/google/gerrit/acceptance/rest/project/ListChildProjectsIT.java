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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;

@NoHttpd
public class ListChildProjectsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void listChildrenOfNonExistingProject_NotFound() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(name("non-existing")).child("children"));
    assertThat(thrown).hasMessageThat().contains("non-existing");
  }

  @Test
  public void listNoChildren() throws Exception {
    assertThatNameList(gApi.projects().name(project.get()).children()).isEmpty();
  }

  @Test
  public void listChildren() throws Exception {
    Project.NameKey child1 = projectOperations.newProject().create();
    Project.NameKey child1_1 = projectOperations.newProject().parent(child1).create();
    Project.NameKey child1_2 = projectOperations.newProject().parent(child1).create();

    assertThatNameList(gApi.projects().name(child1.get()).children()).isInOrder();
    assertThatNameList(gApi.projects().name(child1.get()).children())
        .containsExactly(child1_1, child1_2);
  }

  @Test
  public void listChildrenWithLimit() throws Exception {
    String prefix = RandomStringUtils.randomAlphabetic(8);
    Project.NameKey child1 = projectOperations.newProject().name(prefix + "p1").create();
    Project.NameKey child1_1 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.1").create();
    projectOperations.newProject().parent(child1).name(prefix + "p1.2").create();

    assertThatNameList(gApi.projects().name(child1.get()).children(1)).containsExactly(child1_1);
  }

  @Test
  public void listChildrenRecursively() throws Exception {
    String prefix = RandomStringUtils.randomAlphabetic(8);
    Project.NameKey child1 = projectOperations.newProject().name(prefix + "p1").create();
    Project.NameKey child1_1 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.1").create();
    Project.NameKey child1_2 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.2").create();
    Project.NameKey child1_1_1 =
        projectOperations.newProject().parent(child1_1).name(prefix + "p1.1.1").create();
    Project.NameKey child1_1_1_1 =
        projectOperations.newProject().parent(child1_1_1).name(prefix + "p1.1.1.1").create();

    assertThatNameList(gApi.projects().name(child1.get()).children(true))
        .containsExactly(child1_1, child1_1_1, child1_1_1_1, child1_2)
        .inOrder();
  }
}
