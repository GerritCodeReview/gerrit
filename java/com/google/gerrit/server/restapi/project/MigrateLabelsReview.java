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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.server.restapi.project.MigrateLabelFunctionsToSubmitRequirement.Status.MIGRATED;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigChangeCreator;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MigrateLabelsReview implements RestModifyView<ProjectResource, MigrateLabelsInput> {

  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement;

  @Inject
  MigrateLabelsReview(
      RepoMetaDataUpdater repoMetaDataUpdater,
      MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.migrateLabelFunctionsToSubmitRequirement = migrateLabelFunctionsToSubmitRequirement;
  }

  @Override
  public Response<MigrateLabelsReviewInfo> apply(ProjectResource rsrc, MigrateLabelsInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    try (ConfigChangeCreator creator =
        repoMetaDataUpdater.configChangeCreator(
            rsrc.getNameKey(), null, MigrateLabelFunctionsToSubmitRequirement.COMMIT_MSG)) {
      MigrateLabelFunctionsToSubmitRequirement.Status status =
          migrateLabelFunctionsToSubmitRequirement.updateConfig(
              rsrc.getProjectState().getNameKey(),
              creator.getConfig(),
              new MigrateLabels.LoggingUpdateUI());
      if (status == MIGRATED) {
        return Response.ok(new MigrateLabelsReviewInfo(MIGRATED, creator.createChange().value()));
      }
      return Response.ok(new MigrateLabelsReviewInfo(status));
    }
  }
}
