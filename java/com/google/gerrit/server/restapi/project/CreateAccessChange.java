// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigChangeCreator;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateAccessChange implements RestModifyView<ProjectResource, ProjectAccessInput> {
  private final SetAccessUtil setAccess;
  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  CreateAccessChange(SetAccessUtil accessUtil, RepoMetaDataUpdater repoMetaDataUpdater) {
    this.setAccess = accessUtil;
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, ProjectAccessInput input)
      throws PermissionBackendException, IOException, ConfigInvalidException, UpdateException,
          RestApiException {
    ImmutableList<AccessSection> removals =
        setAccess.getAccessSections(input.remove, /* rejectNonResolvableGroups= */ false);
    ImmutableList<AccessSection> additions =
        setAccess.getAccessSections(input.add, /* rejectNonResolvableGroups= */ true);

    Project.NameKey newParentProjectName =
        input.parent == null ? null : Project.nameKey(input.parent);
    try (ConfigChangeCreator creator =
        repoMetaDataUpdater.configChangeCreator(
            rsrc.getNameKey(), input.message, "Review access change")) {
      ProjectConfig config = creator.getConfig();
      setAccess.validateChanges(config, removals, additions);
      setAccess.applyChanges(config, removals, additions);
      try {
        setAccess.setParentName(
            rsrc.getUser().asIdentifiedUser(),
            config,
            rsrc.getNameKey(),
            newParentProjectName,
            false);
      } catch (AuthException e) {
        throw new IllegalStateException(e);
      }
      return creator.createChange();
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.toString());
    }
  }
}
