// Copyright (C) 2024 The Android Open Source Project
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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import org.junit.Test;

public class PutDescriptionIT extends AbstractDaemonTest {
  @Test
  public void setDescription() throws Exception {
    DescriptionInput input = new DescriptionInput();
    input.description = "test project description";
    gApi.projects().name(project.get()).description(input);
    assertThat(gApi.projects().name(project.get()).description())
        .isEqualTo("test project description");
    assertLastCommitAuthorAndShortMessage(
        RefNames.REFS_CONFIG, "Administrator", "Update description");
  }

  @Test
  public void setDescriptionWithCustomCommitMessage() throws Exception {
    DescriptionInput input = new DescriptionInput();
    input.description = "test project description with test commit message";
    input.commitMessage = "test commit message";
    gApi.projects().name(project.get()).description(input);
    assertThat(gApi.projects().name(project.get()).description())
        .isEqualTo("test project description with test commit message");
    assertLastCommitAuthorAndShortMessage(
        RefNames.REFS_CONFIG, "Administrator", "test commit message");
  }

  @Test
  @GerritConfig(name = "gerrit.requireChangeForConfigUpdate", value = "true")
  public void requireChangeForConfigUpdate_setDescription() throws Exception {
    DescriptionInput input = new DescriptionInput();
    input.description = "test project description";
    MethodNotAllowedException e =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.projects().name(project.get()).description(input));
    assertThat(e.getMessage()).contains("Updating project config without review is disabled");
  }
}
