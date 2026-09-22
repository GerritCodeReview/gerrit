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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import org.junit.Test;

public class ProjectsCollectionIT extends AbstractDaemonTest {
  @Inject private ProjectsCollection projects;

  @Test
  public void projectsCollectionParseRejectsRepeatedGitSuffix() {
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class, () -> projects.parse(project.get() + ".git.git"));

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Project Not Found: " + project.get() + ".git.git");
  }
}
