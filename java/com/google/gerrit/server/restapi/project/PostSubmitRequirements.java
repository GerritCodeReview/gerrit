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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.common.BatchSubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.RepoMetaDataUpdater;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Provider;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PostSubmitRequirements
    extends AbstractPostCollection<
        IdString, SubmitRequirementResource, SubmitRequirementInput, BatchSubmitRequirementInput> {
  CreateSubmitRequirement createSubmitRequirement;
  DeleteSubmitRequirement deleteSubmitRequirement;
  UpdateSubmitRequirement updateSubmitRequirement;

  @Inject
  public PostSubmitRequirements(
      RepoMetaDataUpdater updater,
      Provider<CurrentUser> user,
      CreateSubmitRequirement createSubmitRequirement,
      DeleteSubmitRequirement deleteSubmitRequirement,
      UpdateSubmitRequirement updateSubmitRequirement) {
    super(updater, user);
    this.createSubmitRequirement = createSubmitRequirement;
    this.deleteSubmitRequirement = deleteSubmitRequirement;
    this.updateSubmitRequirement = updateSubmitRequirement;
  }

  @Override
  public String defaultCommitMessage() {
    return "Update Submit Requirements";
  }

  @Override
  protected boolean updateItem(ProjectConfig config, String name, SubmitRequirementInput input)
      throws BadRequestException, UnprocessableEntityException {
    // The name and input.name can be different - the item should be renamed.
    if (config.getSubmitRequirementSections().remove(name) == null) {
      throw new UnprocessableEntityException(
          String.format("Submit requirement %s not found", name));
    }
    var unused = updateSubmitRequirement.updateSubmitRequirement(config, input.name, input);
    return true;
  }

  @Override
  protected void createItem(ProjectConfig config, SubmitRequirementInput input)
      throws BadRequestException, ResourceConflictException {
    var unused = createSubmitRequirement.createSubmitRequirement(config, input.name, input);
  }

  @Override
  protected void deleteItem(ProjectConfig config, String name) throws UnprocessableEntityException {
    if (!deleteSubmitRequirement.deleteSubmitRequirement(config, name)) {
      throw new UnprocessableEntityException(
          String.format("Submit requirement %s not found", name));
    }
  }
}
