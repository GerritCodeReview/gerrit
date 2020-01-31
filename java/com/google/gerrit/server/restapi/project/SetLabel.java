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
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetLabel implements RestModifyView<LabelResource, LabelDefinitionInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public SetLabel(
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
  public Response<LabelDefinitionInfo> apply(LabelResource rsrc, LabelDefinitionInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          PermissionBackendException, IOException, ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getProject().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new LabelDefinitionInput();
    }

    LabelType labelType = rsrc.getLabelType();

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProject().getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      if (updateLabel(config, labelType, input)) {
        if (input.commitMessage != null) {
          md.setMessage(Strings.emptyToNull(input.commitMessage.trim()));
        } else {
          md.setMessage("Update label");
        }

        config.commit(md);
        projectCache.evict(rsrc.getProject().getProjectState().getProject());
      }
    }
    return Response.ok(LabelDefinitionJson.format(rsrc.getProject().getNameKey(), labelType));
  }

  /**
   * Updates the given label.
   *
   * @param config the project config
   * @param labelType the label type that should be updated
   * @param input the input that describes the label update
   * @return whether the label type was modified
   * @throws BadRequestException if there was invalid data in the input
   * @throws ResourceConflictException if the update cannot be applied due to a conflict
   */
  public boolean updateLabel(ProjectConfig config, LabelType labelType, LabelDefinitionInput input)
      throws BadRequestException, ResourceConflictException {
    boolean dirty = false;

    config.getLabelSections().remove(labelType.getName());

    if (input.name != null) {
      String newName = input.name.trim();
      if (newName.isEmpty()) {
        throw new BadRequestException("name cannot be empty");
      }
      if (!newName.equals(labelType.getName())) {
        if (config.getLabelSections().containsKey(newName)) {
          throw new ResourceConflictException(String.format("name %s already in use", newName));
        }

        for (String labelName : config.getLabelSections().keySet()) {
          if (labelName.equalsIgnoreCase(newName)) {
            throw new ResourceConflictException(
                String.format("name %s conflicts with existing label %s", newName, labelName));
          }
        }

        try {
          labelType.setName(newName);
        } catch (IllegalArgumentException e) {
          throw new BadRequestException("invalid name: " + input.name, e);
        }
        dirty = true;
      }
    }

    if (input.function != null) {
      if (input.function.trim().isEmpty()) {
        throw new BadRequestException("function cannot be empty");
      }
      labelType.setFunction(LabelDefinitionInputParser.parseFunction(input.function));
      dirty = true;
    }

    if (input.values != null) {
      if (input.values.isEmpty()) {
        throw new BadRequestException("values cannot be empty");
      }
      labelType.setValues(LabelDefinitionInputParser.parseValues(input.values));
      dirty = true;
    }

    if (input.defaultValue != null) {
      labelType.setDefaultValue(
          LabelDefinitionInputParser.parseDefaultValue(labelType, input.defaultValue));
      dirty = true;
    }

    if (input.branches != null) {
      labelType.setRefPatterns(LabelDefinitionInputParser.parseBranches(input.branches));
      dirty = true;
    }

    if (input.canOverride != null) {
      labelType.setCanOverride(input.canOverride);
      dirty = true;
    }

    if (input.copyAnyScore != null) {
      labelType.setCopyAnyScore(input.copyAnyScore);
      dirty = true;
    }

    if (input.copyMinScore != null) {
      labelType.setCopyMinScore(input.copyMinScore);
      dirty = true;
    }

    if (input.copyMaxScore != null) {
      labelType.setCopyMaxScore(input.copyMaxScore);
      dirty = true;
    }

    if (input.copyAllScoresIfNoChange != null) {
      labelType.setCopyAllScoresIfNoChange(input.copyAllScoresIfNoChange);
    }

    if (input.copyAllScoresIfNoCodeChange != null) {
      labelType.setCopyAllScoresIfNoCodeChange(input.copyAllScoresIfNoCodeChange);
      dirty = true;
    }

    if (input.copyAllScoresOnTrivialRebase != null) {
      labelType.setCopyAllScoresOnTrivialRebase(input.copyAllScoresOnTrivialRebase);
      dirty = true;
    }

    if (input.copyAllScoresOnMergeFirstParentUpdate != null) {
      labelType.setCopyAllScoresOnMergeFirstParentUpdate(
          input.copyAllScoresOnMergeFirstParentUpdate);
      dirty = true;
    }

    if (input.copyValues != null) {
      labelType.setCopyValues(input.copyValues);
      dirty = true;
    }

    if (input.allowPostSubmit != null) {
      labelType.setAllowPostSubmit(input.allowPostSubmit);
      dirty = true;
    }

    if (input.ignoreSelfApproval != null) {
      labelType.setIgnoreSelfApproval(input.ignoreSelfApproval);
      dirty = true;
    }

    config.getLabelSections().put(labelType.getName(), labelType);

    return dirty;
  }
}
