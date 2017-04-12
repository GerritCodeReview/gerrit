// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class PutConfigIT extends AbstractDaemonTest {
  @Test
  public void putConfigNotFound() throws Exception {
    // inexistent project
    RestResponse r = userRestSession.put("/projects/inexistentProject/config", new ConfigInput());
    r.assertNotFound();
    r.consume();
    // existent project but user not owner
    r = userRestSession.put("/projects/" + project.get() + "/config", new ConfigInput());
    r.assertNotFound();
    r.consume();
  }

  @Test
  public void putConfig() throws Exception {
    ConfigInput input = createTestConfigInput();
    putConfig(input);
    Project updatedProject = projectCache.get(project).getProject();
    assertThat(updatedProject.getDescription()).isEqualTo(input.description);
    assertThat(updatedProject.getUseContributorAgreements())
        .isEqualTo(input.useContributorAgreements);
    assertThat(updatedProject.getUseContentMerge()).isEqualTo(input.useContentMerge);
    assertThat(updatedProject.getUseSignedOffBy()).isEqualTo(input.useSignedOffBy);
    assertThat(updatedProject.getCreateNewChangeForAllNotInTarget())
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(updatedProject.getRequireChangeID()).isEqualTo(input.requireChangeId);
    assertThat(updatedProject.getRejectImplicitMerges()).isEqualTo(input.rejectImplicitMerges);
    assertThat(updatedProject.getEnableReviewerByEmail()).isEqualTo(input.enableReviewerByEmail);
    assertThat(updatedProject.getCreateNewChangeForAllNotInTarget())
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(updatedProject.getMaxObjectSizeLimit()).isEqualTo(input.maxObjectSizeLimit);
    assertThat(updatedProject.getSubmitType()).isEqualTo(input.submitType);
    assertThat(updatedProject.getState()).isEqualTo(input.state);
  }

  @Test
  public void putConfigPartialInput() throws Exception {
    ConfigInput input = createTestConfigInput();
    putConfig(input);

    ConfigInput partialInput = new ConfigInput();
    partialInput.useContributorAgreements = InheritableBoolean.FALSE;
    putConfig(partialInput);
    Project updatedProject = projectCache.get(project).getProject();
    assertThat(updatedProject.getUseContributorAgreements())
        .isEqualTo(partialInput.useContributorAgreements);
    assertThat(updatedProject.getUseContentMerge()).isEqualTo(input.useContentMerge);
    assertThat(updatedProject.getUseSignedOffBy()).isEqualTo(input.useSignedOffBy);
    assertThat(updatedProject.getCreateNewChangeForAllNotInTarget())
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(updatedProject.getRequireChangeID()).isEqualTo(input.requireChangeId);
    assertThat(updatedProject.getRejectImplicitMerges()).isEqualTo(input.rejectImplicitMerges);
    assertThat(updatedProject.getEnableReviewerByEmail()).isEqualTo(input.enableReviewerByEmail);
    assertThat(updatedProject.getCreateNewChangeForAllNotInTarget())
        .isEqualTo(input.createNewChangeForAllNotInTarget);
    assertThat(updatedProject.getMaxObjectSizeLimit()).isEqualTo(input.maxObjectSizeLimit);
    assertThat(updatedProject.getSubmitType()).isEqualTo(input.submitType);
    assertThat(updatedProject.getState()).isEqualTo(input.state);
  }

  @Test
  public void descriptionIsDeletedWhenNotSpecified() throws Exception {
    ConfigInput input = new ConfigInput();
    input.description = "some description";
    putConfig(input);
    assertThat(projectCache.get(project).getProject().getDescription())
        .isEqualTo(input.description);

    putConfig(new ConfigInput());
    assertThat(projectCache.get(project).getProject().getDescription()).isEmpty();
  }

  private void putConfig(ConfigInput input) throws Exception {
    RestResponse r = adminRestSession.put("/projects/" + project.get() + "/config", input);
    r.assertOK();
    r.consume();
  }

  private ConfigInput createTestConfigInput() {
    ConfigInput input = new ConfigInput();
    input.description = "some description";
    input.useContributorAgreements = InheritableBoolean.TRUE;
    input.useContentMerge = InheritableBoolean.TRUE;
    input.useSignedOffBy = InheritableBoolean.TRUE;
    input.createNewChangeForAllNotInTarget = InheritableBoolean.TRUE;
    input.requireChangeId = InheritableBoolean.TRUE;
    input.rejectImplicitMerges = InheritableBoolean.TRUE;
    input.enableReviewerByEmail = InheritableBoolean.TRUE;
    input.createNewChangeForAllNotInTarget = InheritableBoolean.TRUE;
    input.maxObjectSizeLimit = "5m";
    input.submitType = SubmitType.CHERRY_PICK;
    input.state = ProjectState.HIDDEN;
    return input;
  }
}
