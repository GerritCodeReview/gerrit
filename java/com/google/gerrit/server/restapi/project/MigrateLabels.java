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
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;

@Singleton
public class MigrateLabels implements RestModifyView<ProjectResource, MigrateLabelsInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement;
  private final PermissionBackend permissionBackend;

  @Inject
  MigrateLabels(
      MigrateLabelFunctionsToSubmitRequirement migrateLabelFunctionsToSubmitRequirement,
      PermissionBackend permissionBackend) {
    this.migrateLabelFunctionsToSubmitRequirement = migrateLabelFunctionsToSubmitRequirement;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<MigrateLabelsInfo> apply(ProjectResource rsrc, MigrateLabelsInput input)
      throws Exception {
    Project.NameKey project = rsrc.getNameKey();
    permissionBackend.currentUser().project(project).check(ProjectPermission.WRITE_CONFIG);
    MigrateLabelFunctionsToSubmitRequirement.Status status =
        migrateLabelFunctionsToSubmitRequirement.executeMigration(project, new LoggingUpdateUI());

    MigrateLabelsInfo info = new MigrateLabelsInfo();
    info.status = status;
    return Response.ok(info);
  }

  public static class LoggingUpdateUI implements UpdateUI {

    @Override
    public void message(String message) {
      logger.atInfo().log(message);
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
