// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.extensions.api.projects.SubmitRequirementApi;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.gerrit.server.restapi.project.CreateSubmitRequirement;
import com.google.gerrit.server.restapi.project.GetSubmitRequirement;
import com.google.gerrit.server.restapi.project.SubmitRequirementsCollection;
import com.google.gerrit.server.restapi.project.UpdateSubmitRequirement;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SubmitRequirementApiImpl implements SubmitRequirementApi {
  interface Factory {
    SubmitRequirementApiImpl create(ProjectResource project, String name);
  }

  private final SubmitRequirementsCollection submitRequirements;
  private final CreateSubmitRequirement createSubmitRequirement;
  private final UpdateSubmitRequirement updateSubmitRequirement;
  private final GetSubmitRequirement getSubmitRequirement;
  private final String name;
  private final ProjectCache projectCache;

  private ProjectResource project;

  @Inject
  SubmitRequirementApiImpl(
      SubmitRequirementsCollection submitRequirements,
      CreateSubmitRequirement createSubmitRequirement,
      UpdateSubmitRequirement updateSubmitRequirement,
      GetSubmitRequirement getSubmitRequirement,
      ProjectCache projectCache,
      @Assisted ProjectResource project,
      @Assisted String name) {
    this.submitRequirements = submitRequirements;
    this.createSubmitRequirement = createSubmitRequirement;
    this.updateSubmitRequirement = updateSubmitRequirement;
    this.getSubmitRequirement = getSubmitRequirement;
    this.projectCache = projectCache;
    this.project = project;
    this.name = name;
  }

  @Override
  public SubmitRequirementApi create(SubmitRequirementInput input) throws RestApiException {
    try {
      createSubmitRequirement.apply(project, IdString.fromDecoded(name), input);

      // recreate project resource because project state was updated
      project =
          new ProjectResource(
              projectCache
                  .get(project.getNameKey())
                  .orElseThrow(illegalState(project.getNameKey())),
              project.getUser());

      return this;
    } catch (Exception e) {
      throw asRestApiException("Cannot create submit requirement", e);
    }
  }

  @Override
  public SubmitRequirementInfo get() throws RestApiException {
    try {
      return getSubmitRequirement.apply(resource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit requirement", e);
    }
  }

  @Override
  public SubmitRequirementInfo update(SubmitRequirementInput input) throws RestApiException {
    try {
      return updateSubmitRequirement.apply(resource(), input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot update submit requirement", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    /** TODO(ghareeb): implement */
  }

  private SubmitRequirementResource resource() throws RestApiException, PermissionBackendException {
    return submitRequirements.parse(project, IdString.fromDecoded(name));
  }
}
