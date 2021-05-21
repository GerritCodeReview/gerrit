// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteSubmitRequirement implements RestModifyView<SubmitRequirementResource, Input> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public DeleteSubmitRequirement(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
  }

  @Override
  public Response<?> apply(SubmitRequirementResource rsrc, Input input) throws Exception {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getProject().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProject().getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      if (!deleteSubmitRequirement(config, rsrc.getSubmitRequirement().name())) {
        // This code is unreachable because the exception is thrown when rsrc was parsed
        throw new ResourceNotFoundException(
            String.format(
                "Submit requirement '%s' not found",
                IdString.fromDecoded(rsrc.getSubmitRequirement().name())));
      }

      md.setMessage("Delete submit requirement");
      config.commit(md);
    }

    projectCache.evict(rsrc.getProject().getProjectState().getProject());

    return Response.none();
  }

  /**
   * Delete the given submit requirement from the project config.
   *
   * @param config the project config from which the label should be deleted
   * @param srName the name of the submit requirement that should be deleted
   * @return {@code true} if the label was deleted, {@code false} if the label was not found
   */
  public boolean deleteSubmitRequirement(ProjectConfig config, String srName) {
    if (!config.getSubmitRequirementSections().containsKey(srName)) {
      return false;
    }
    config.getSubmitRequirementSections().remove(srName);
    return true;
  }
}
