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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class LabelsReviewIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void createLabelsChangeWithDefaultMessage() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = "Foo";
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooInput);

    ChangeInfo changeInfo = gApi.projects().name(testProject.get()).labelsReview(input);

    assertThat(changeInfo.subject).isEqualTo("Review labels change");
    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getStringList("label", "Foo", "value"))
        .asList()
        .containsExactly("+1 Looks Good", "0 Don't Know", "-1 Looks Bad");
  }

  @Test
  public void createLabelsChangeWithCustomMessage() throws Exception {
    Project.NameKey testProject = projectOperations.newProject().create();
    LabelDefinitionInput fooInput = new LabelDefinitionInput();
    fooInput.name = "Foo";
    fooInput.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(fooInput);
    String customMessage = "test custom message";
    input.commitMessage = customMessage;

    ChangeInfo changeInfo = gApi.projects().name(testProject.get()).labelsReview(input);

    assertThat(changeInfo.subject).isEqualTo(customMessage);
    Config config = new Config();
    config.fromText(
        gApi.changes()
            .id(changeInfo.changeId)
            .revision(1)
            .file("project.config")
            .content()
            .asString());
    assertThat(config.getStringList("label", "Foo", "value"))
        .asList()
        .containsExactly("+1 Looks Good", "0 Don't Know", "-1 Looks Bad");
  }
}
