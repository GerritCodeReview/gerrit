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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class SubmitRequirementTemplateResource implements RestResource {
  public static final TypeLiteral<RestView<SubmitRequirementTemplateResource>>
      SUBMIT_REQUIREMENT_TEMPLATE_KIND = new TypeLiteral<>() {};

  private final ProjectResource project;
  private final Project.NameKey sourceProject;
  private final SubmitRequirement submitRequirementTemplate;

  public SubmitRequirementTemplateResource(
      ProjectResource project,
      Project.NameKey sourceProject,
      SubmitRequirement submitRequirementTemplate) {
    this.project = project;
    this.sourceProject = sourceProject;
    this.submitRequirementTemplate = submitRequirementTemplate;
  }

  public ProjectResource getProject() {
    return project;
  }

  public Project.NameKey getSourceProject() {
    return sourceProject;
  }

  public SubmitRequirement getSubmitRequirementTemplate() {
    return submitRequirementTemplate;
  }
}
