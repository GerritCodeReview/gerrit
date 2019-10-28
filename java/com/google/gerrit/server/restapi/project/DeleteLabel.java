// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.InputWithCommitMessage;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteLabel implements RestModifyView<LabelResource, InputWithCommitMessage> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public DeleteLabel(
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
  public Response<?> apply(LabelResource rsrc, InputWithCommitMessage input)
      throws AuthException, ResourceNotFoundException, PermissionBackendException, IOException,
          ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getProject().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new InputWithCommitMessage();
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProject().getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      if (!config.getLabelSections().containsKey(rsrc.getLabelType().getName())) {
        throw new ResourceNotFoundException(IdString.fromDecoded(rsrc.getLabelType().getName()));
      }

      config.getLabelSections().remove(rsrc.getLabelType().getName());

      if (input.commitMessage != null) {
        md.setMessage(Strings.emptyToNull(input.commitMessage.trim()));
      } else {
        md.setMessage("Delete label");
      }

      config.commit(md);
    }

    projectCache.evict(rsrc.getProject().getProjectState().getProject());

    return Response.none();
  }
}
