// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectsCollectionTest {
  @Mock Provider<ListProjects> list;
  @Mock Provider<QueryProjects> queryProjects;
  @Mock ProjectCache projectCache;
  @Mock PermissionBackend permissionBackend;
  @Mock Provider<CurrentUser> user;

  @Test
  public void parseRejectsRepeatedGitSuffixBeforeProjectLookup() {
    ProjectsCollection projects =
        new ProjectsCollection(
            DynamicMap.emptyMap(), list, queryProjects, projectCache, permissionBackend, user);

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> projects.parse("project.git.git"));

    assertThat(thrown).hasMessageThat().isEqualTo("Project Not Found: project.git.git");
    verifyNoInteractions(list, queryProjects, projectCache, permissionBackend, user);
  }
}
