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
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateLabel
    implements RestCollectionCreateView<ProjectResource, LabelResource, LabelDefinitionInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public CreateLabel(
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
  public Response<LabelDefinitionInfo> apply(
      ProjectResource rsrc, IdString id, LabelDefinitionInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          PermissionBackendException, IOException, ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new LabelDefinitionInput();
    }

    if (input.name != null && !input.name.equals(id.get())) {
      throw new BadRequestException("name in input must match name in URL");
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      if (config.getLabelSections().containsKey(id.get())) {
        throw new ResourceConflictException("label " + id.get() + " already exists");
      }

      if (input.values == null || input.values.isEmpty()) {
        throw new BadRequestException("values are required");
      }

      List<LabelValue> values = LabelDefinitionInputParser.parseValues(input.values);

      LabelType labelType;
      try {
        labelType = new LabelType(id.get(), values);
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("invalid name: " + id.get(), e);
      }

      if (input.function != null && !input.function.trim().isEmpty()) {
        labelType.setFunction(LabelDefinitionInputParser.parseFunction(input.function));
      } else {
        labelType.setFunction(LabelFunction.MAX_WITH_BLOCK);
      }

      if (input.defaultValue != null) {
        labelType.setDefaultValue(
            LabelDefinitionInputParser.parseDefaultValue(labelType, input.defaultValue));
      }

      if (input.branches != null) {
        labelType.setRefPatterns(LabelDefinitionInputParser.parseBranches(input.branches));
      }

      if (input.canOverride != null) {
        labelType.setCanOverride(input.canOverride);
      }

      if (input.copyAnyScore != null) {
        labelType.setCopyAnyScore(input.copyAnyScore);
      }

      if (input.copyMinScore != null) {
        labelType.setCopyMinScore(input.copyMinScore);
      }

      if (input.copyMaxScore != null) {
        labelType.setCopyMaxScore(input.copyMaxScore);
      }

      if (input.copyAllScoresIfNoChange != null) {
        labelType.setCopyAllScoresIfNoChange(input.copyAllScoresIfNoChange);
      }

      if (input.copyAllScoresIfNoCodeChange != null) {
        labelType.setCopyAllScoresIfNoCodeChange(input.copyAllScoresIfNoCodeChange);
      }

      if (input.copyAllScoresOnTrivialRebase != null) {
        labelType.setCopyAllScoresOnTrivialRebase(input.copyAllScoresOnTrivialRebase);
      }

      if (input.copyAllScoresOnMergeFirstParentUpdate != null) {
        labelType.setCopyAllScoresOnMergeFirstParentUpdate(
            input.copyAllScoresOnMergeFirstParentUpdate);
      }

      if (input.allowPostSubmit != null) {
        labelType.setAllowPostSubmit(input.allowPostSubmit);
      }

      if (input.ignoreSelfApproval != null) {
        labelType.setIgnoreSelfApproval(input.ignoreSelfApproval);
      }

      if (input.commitMessage != null) {
        md.setMessage(Strings.emptyToNull(input.commitMessage.trim()));
      } else {
        md.setMessage("Update label");
      }

      config.getLabelSections().put(labelType.getName(), labelType);
      config.commit(md);

      projectCache.evict(rsrc.getProjectState().getProject());

      return Response.created(LabelDefinitionJson.format(rsrc.getNameKey(), labelType));
    }
  }
}
