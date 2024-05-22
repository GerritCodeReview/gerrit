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

import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigChangeCreator;
import com.google.gerrit.server.update.UpdateException;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutConfigReview implements RestModifyView<ProjectResource, ConfigInput> {
  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final PutConfig putConfig;

  @Inject
  PutConfigReview(RepoMetaDataUpdater repoMetaDataUpdater, PutConfig putConfig) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.putConfig = putConfig;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, ConfigInput input)
      throws PermissionBackendException, IOException, ConfigInvalidException, UpdateException,
          RestApiException {
    try (ConfigChangeCreator creator =
        repoMetaDataUpdater.configChangeCreator(
            rsrc.getNameKey(), input.commitMessage, "Review config change")) {
      putConfig.updateConfig(rsrc.getProjectState(), creator.getConfig(), input);
      return creator.createChange();
    }
  }
}
