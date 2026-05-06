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
import static org.mockito.Mockito.mock;

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementTemplateResource;
import org.junit.Test;

public class GetSubmitRequirementTemplateTest {
  @Test
  public void formatsTemplateFromResource() {
    Project.NameKey sourceProject = Project.nameKey("All-Projects");
    SubmitRequirement template =
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=MAX"))
            .setAllowOverrideInChildProjects(true)
            .build();

    SubmitRequirementTemplateResource resource =
        new SubmitRequirementTemplateResource(mock(ProjectResource.class), sourceProject, template);

    Response<SubmitRequirementInfo> response = new GetSubmitRequirementTemplate().apply(resource);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.value().name).isEqualTo("Code-Review");
    assertThat(response.value().projectName).isEqualTo(sourceProject.get());
    assertThat(response.value().submittabilityExpression).isEqualTo("label:Code-Review=MAX");
  }
}
