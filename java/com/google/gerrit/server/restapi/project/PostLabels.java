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
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** REST endpoint that allows to add, update and delete label definitions in a batch. */
@Singleton
public class PostLabels
    implements RestCollectionModifyView<ProjectResource, LabelResource, BatchLabelInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final DeleteLabel deleteLabel;
  private final CreateLabel createLabel;
  private final SetLabel setLabel;
  private final ProjectCache projectCache;

  @Inject
  public PostLabels(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      DeleteLabel deleteLabel,
      CreateLabel createLabel,
      SetLabel setLabel,
      ProjectCache projectCache) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.deleteLabel = deleteLabel;
    this.createLabel = createLabel;
    this.setLabel = setLabel;
    this.projectCache = projectCache;
  }

  @Override
  public Response<?> apply(ProjectResource rsrc, BatchLabelInput input)
      throws AuthException, UnprocessableEntityException, PermissionBackendException, IOException,
          ConfigInvalidException, BadRequestException, ResourceConflictException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new BatchLabelInput();
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getNameKey())) {
      boolean dirty = false;

      ProjectConfig config = projectConfigFactory.read(md);

      if (input.delete != null && !input.delete.isEmpty()) {
        for (String labelName : input.delete) {
          if (!deleteLabel.deleteLabel(config, labelName.trim())) {
            throw new UnprocessableEntityException(String.format("label %s not found", labelName));
          }
        }
        dirty = true;
      }

      if (input.create != null && !input.create.isEmpty()) {
        for (LabelDefinitionInput labelInput : input.create) {
          if (labelInput.name == null || labelInput.name.trim().isEmpty()) {
            throw new BadRequestException("label name is required for new label");
          }
          if (labelInput.commitMessage != null) {
            throw new BadRequestException("commit message on label definition input not supported");
          }
          createLabel.createLabel(config, labelInput.name.trim(), labelInput);
        }
        dirty = true;
      }

      if (input.update != null && !input.update.isEmpty()) {
        for (Entry<String, LabelDefinitionInput> e : input.update.entrySet()) {
          LabelType labelType = config.getLabelSections().get(e.getKey().trim());
          if (labelType == null) {
            throw new UnprocessableEntityException(String.format("label %s not found", e.getKey()));
          }
          if (e.getValue().commitMessage != null) {
            throw new BadRequestException("commit message on label definition input not supported");
          }
          setLabel.updateLabel(config, labelType, e.getValue());
        }
        dirty = true;
      }

      if (input.commitMessage != null) {
        md.setMessage(Strings.emptyToNull(input.commitMessage.trim()));
      } else {
        md.setMessage("Update labels");
      }

      if (dirty) {
        config.commit(md);
        projectCache.evict(rsrc.getProjectState().getProject());
      }
    }

    return Response.ok("");
  }
}
