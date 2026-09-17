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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.MigrateLabelFunctionsToSubmitRequirement.Status;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.UpdateUI;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigChangeCreator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;

@Singleton
public class MigrateLabels implements RestModifyView<ProjectResource, MigrateLabelsInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement;
  private final PermissionBackend permissionBackend;

  @Inject
  MigrateLabels(
      RepoMetaDataUpdater repoMetaDataUpdater,
      MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement,
      PermissionBackend permissionBackend) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.migrateLabelFunctionsToSubmitRequirement = migrateLabelFunctionsToSubmitRequirement;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<MigrateLabelsInfo> apply(ProjectResource rsrc, MigrateLabelsInput input)
      throws Exception {
    Status status = execute(rsrc, ExecutionMode.DIRECT).status();
    MigrateLabelsInfo info = new MigrateLabelsInfo();
    info.status = status;
    return Response.ok(info);
  }

  MigrationResult execute(ProjectResource rsrc, ExecutionMode mode) throws Exception {
    Project.NameKey project = rsrc.getNameKey();
    return switch (mode) {
      case DIRECT -> executeDirect(project);
      case REVIEW -> executeReview(project, rsrc);
    };
  }

  private MigrationResult executeDirect(Project.NameKey project) throws Exception {
    permissionBackend.currentUser().project(project).check(ProjectPermission.WRITE_CONFIG);
    Status status =
        migrateLabelFunctionsToSubmitRequirement.executeMigration(project, new LoggingUpdateUI());
    return new MigrationResult(status, null);
  }

  private MigrationResult executeReview(Project.NameKey project, ProjectResource rsrc)
      throws Exception {
    try (ConfigChangeCreator creator =
        repoMetaDataUpdater.configChangeCreator(
            project, null, MigrateLabelFunctionsToSubmitRequirement.COMMIT_MSG)) {
      Status status =
          migrateLabelFunctionsToSubmitRequirement.updateConfig(
              rsrc.getProjectState().getNameKey(), creator.getConfig(), new LoggingUpdateUI());
      if (status == Status.MIGRATED) {
        return new MigrationResult(status, creator.createChange().value());
      }
      return new MigrationResult(status, null);
    }
  }

  record MigrationResult(Status status, ChangeInfo change) {}

  enum ExecutionMode {
    DIRECT,
    REVIEW
  }

  public static class LoggingUpdateUI implements UpdateUI {
    @Override
    public void message(String message) {
      logger.atInfo().log("%s", message);
    }

    @Override
    public boolean yesno(boolean defaultValue, String message) {
      return false;
    }

    @Override
    public void waitForUser() {}

    @Override
    public String readString(String defaultValue, Set<String> allowedValues, String message) {
      return null;
    }

    @Override
    public boolean isBatch() {
      return false;
    }
  }
}
