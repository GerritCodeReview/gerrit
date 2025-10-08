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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeleteSubmitRequirement implements RestModifyView<SubmitRequirementResource, Input> {
  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  public DeleteSubmitRequirement(RepoMetaDataUpdater repoMetaDataUpdater) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<?> apply(SubmitRequirementResource rsrc, Input input) throws Exception {
    try (var configUpdater =
        repoMetaDataUpdater.configUpdater(
            rsrc.getProject().getNameKey(), null, "Delete submit requirement")) {
      ProjectConfig config = configUpdater.getConfig();

      if (!deleteSubmitRequirement(config, rsrc.getSubmitRequirement().name())) {
        // This code is unreachable because the exception is thrown when rsrc was parsed
        throw new ResourceNotFoundException(
            String.format(
                "Submit requirement '%s' not found",
                IdString.fromDecoded(rsrc.getSubmitRequirement().name())));
      }

      configUpdater.commitConfigUpdate();
    }

    return Response.none();
  }

  /**
   * Delete the given submit requirement from the project config.
   *
   * @param config the project config from which the submit-requirement should be deleted
   * @param srName the name of the submit requirement that should be deleted
   * @return {@code true} if the submit-requirement was deleted, {@code false} if the
   *     submit-requirement was not found
   */
  public boolean deleteSubmitRequirement(ProjectConfig config, String srName) {
    if (!config.getSubmitRequirementSections().containsKey(srName)) {
      return false;
    }
    config.getSubmitRequirementSections().remove(srName);
    return true;
  }
}
