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

package com.google.gerrit.server.args4j;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectHandlerTest {
  @Mock Setter<ProjectState> projectStateSetter;
  @Mock OptionDef optionDef;
  @Mock ProjectCache projectCache;
  @Mock PermissionBackend permissionBackend;
  @Mock Parameters parameters;

  @Test
  public void parseArgumentsRejectsRepeatedGitSuffixProjectName() throws Exception {
    when(parameters.getParameter(0)).thenReturn("project.git.git");

    ProjectHandler handler =
        new ProjectHandler(
            projectCache,
            permissionBackend,
            new CmdLineParser(new Object()),
            optionDef,
            projectStateSetter);

    CmdLineException thrown =
        assertThrows(CmdLineException.class, () -> handler.parseArguments(parameters));

    assertThat(thrown).hasMessageThat().contains("Invalid project name project.git.git");
    verifyNoInteractions(projectCache, permissionBackend);
  }
}
