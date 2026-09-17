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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.UpdateUI;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigUpdater;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** REST-facing wrapper for the shared label-function migration logic. */
public class MigrateLabelFunctionsToSubmitRequirement
    extends com.google.gerrit.server.project.MigrateLabelFunctionsToSubmitRequirement {
  public static final String COMMIT_MSG =
      com.google.gerrit.server.project.MigrateLabelFunctionsToSubmitRequirement.COMMIT_MSG;

  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  public MigrateLabelFunctionsToSubmitRequirement(
      RepoMetaDataUpdater repoMetaDataUpdater, GitRepositoryManager repoManager) {
    super(repoManager);
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  public Status executeMigration(Project.NameKey project, UpdateUI ui)
      throws IOException,
          ConfigInvalidException,
          MethodNotAllowedException,
          PermissionBackendException {
    try (ConfigUpdater updater =
        repoMetaDataUpdater.configUpdaterWithoutPermissionsCheck(project, null, COMMIT_MSG)) {
      Status result = updateConfig(project, updater.getConfig(), ui);
      if (result == Status.MIGRATED) {
        updater.commitConfigUpdate();
      }
      return result;
    }
  }
}
