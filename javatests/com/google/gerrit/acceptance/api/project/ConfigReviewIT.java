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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class ConfigReviewIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  private Project.NameKey defaultMessageProject;
  private Project.NameKey customMessageProject;

  @Before
  public void setUp() throws Exception {
    defaultMessageProject = projectOperations.newProject().create();
    customMessageProject = projectOperations.newProject().create();
  }

  @Test
  public void createConfigChangeWithDefaultMessage() throws Exception {
    ConfigInput in = new ConfigInput();
    in.description = "Test project description";

    ChangeInfo changeInfo = gApi.projects().name(defaultMessageProject.get()).configReview(in);

    assertThat(changeInfo.subject).isEqualTo("Review config change");
    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getString("project", null, "description"))
        .isEqualTo("Test project description");
  }

  @Test
  public void createConfigChangeWithCustomMessage() throws Exception {
    ConfigInput in = new ConfigInput();
    in.description = "Test project description";
    String customMessage = "test custom message";
    in.commitMessage = customMessage;

    ChangeInfo changeInfo = gApi.projects().name(customMessageProject.get()).configReview(in);

    assertThat(changeInfo.subject).isEqualTo(customMessage);
    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getString("project", null, "description"))
        .isEqualTo("Test project description");
  }
}
