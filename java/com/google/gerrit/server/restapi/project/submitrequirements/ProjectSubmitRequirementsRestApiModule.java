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

    /**
     * List submit requirements in a project {@code GET /projects/<project-id>/submit_requirements}.
     */
    child(PROJECT_KIND, "submit_requirements").to(SubmitRequirementsCollection.class);

    /**
     * Create submit requirements in a project {@code POST
     * /projects/<project-id>/submit_requirements}.
     */
    postOnCollection(SUBMIT_REQUIREMENT_KIND).to(PostSubmitRequirements.class);
    /**
     * Create submit requirement in a project {@code PUT
     * /projects/<project-id>/submit_requirements/<submit_requirement-id>}.
     */
    create(SUBMIT_REQUIREMENT_KIND).to(CreateSubmitRequirement.class);
    /**
     * Update submit requirement in a project {@code PUT
     * /projects/<project-id>/submit_requirements/<submit_requirement-id>}.
     */
    put(SUBMIT_REQUIREMENT_KIND).to(UpdateSubmitRequirement.class);
    /**
     * Get submit requirement in a project {@code GET
     * /projects/<project-id>/submit_requirements/<submit_requirement-id>}.
     */
    get(SUBMIT_REQUIREMENT_KIND).to(GetSubmitRequirement.class);
    /**
     * Delete submit requirement in a project {@code DELETE
     * /projects/<project-id>/submit_requirements/<submit_requirement-id>}.
     */
    delete(SUBMIT_REQUIREMENT_KIND).to(DeleteSubmitRequirement.class);

    /**
     * Create submit requirement change for review in a project {@code POST
     * /projects/<project-id>/submit_requirements:review}.
     */
    post(PROJECT_KIND, "submit_requirements:review").to(PostSubmitRequirementsReview.class);
  }
}
