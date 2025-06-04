// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project.submitrequirements;

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.google.gerrit.server.project.SubmitRequirementResource.SUBMIT_REQUIREMENT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ProjectSubmitRequirementsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), SUBMIT_REQUIREMENT_KIND);

    child(PROJECT_KIND, "submit_requirements").to(SubmitRequirementsCollection.class);
    create(SUBMIT_REQUIREMENT_KIND).to(CreateSubmitRequirement.class);
    put(SUBMIT_REQUIREMENT_KIND).to(UpdateSubmitRequirement.class);
    get(SUBMIT_REQUIREMENT_KIND).to(GetSubmitRequirement.class);
    delete(SUBMIT_REQUIREMENT_KIND).to(DeleteSubmitRequirement.class);
    postOnCollection(SUBMIT_REQUIREMENT_KIND).to(PostSubmitRequirements.class);
    post(PROJECT_KIND, "submit_requirements:review").to(PostSubmitRequirementsReview.class);
  }
}
