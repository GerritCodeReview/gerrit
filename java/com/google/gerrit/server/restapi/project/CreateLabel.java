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
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.index.query.QueryParseException;
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
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateLabel
    implements RestCollectionCreateView<ProjectResource, LabelResource, LabelDefinitionInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final ApprovalQueryBuilder approvalQueryBuilder;

  @Inject
  public CreateLabel(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache,
      ApprovalQueryBuilder approvalQueryBuilder) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
    this.approvalQueryBuilder = approvalQueryBuilder;
  }

  @Override
  public Response<LabelDefinitionInfo> apply(
      ProjectResource rsrc, IdString id, LabelDefinitionInput input)
      throws AuthException,
          BadRequestException,
          ResourceConflictException,
          PermissionBackendException,
          IOException,
          ConfigInvalidException {
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

      LabelType labelType = createLabel(config, id.get(), input);

      if (input.commitMessage != null) {
        md.setMessage(Strings.emptyToNull(input.commitMessage.trim()));
      } else {
        md.setMessage("Update label");
      }

      config.commit(md);

      projectCache.evictAndReindex(rsrc.getProjectState().getProject());

      return Response.created(LabelDefinitionJson.format(rsrc.getNameKey(), labelType));
    }
  }

  /**
   * Creates a new label.
   *
   * @param config the project config
   * @param label the name of the new label
   * @param input the input that describes the new label
   * @return the created label type
   * @throws BadRequestException if there was invalid data in the input
   * @throws ResourceConflictException if the label cannot be created due to a conflict
   */
  @SuppressWarnings("deprecation")
  public LabelType createLabel(ProjectConfig config, String label, LabelDefinitionInput input)
      throws BadRequestException, ResourceConflictException {
    if (config.getLabelSections().containsKey(label)) {
      throw new ResourceConflictException(String.format("label %s already exists", label));
    }

    for (String labelName : config.getLabelSections().keySet()) {
      if (labelName.equalsIgnoreCase(label)) {
        throw new ResourceConflictException(
            String.format("label %s conflicts with existing label %s", label, labelName));
      }
    }

    if (input.values == null || input.values.isEmpty()) {
      throw new BadRequestException("values are required");
    }

    try {
      LabelType.checkName(label);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("invalid name: " + label, e);
    }

    List<LabelValue> values = LabelDefinitionInputParser.parseValues(input.values);
    LabelType.Builder labelType = LabelType.builder(LabelType.checkName(label), values);

    if (input.description != null) {
      String description = Strings.emptyToNull(input.description.trim());
      labelType.setDescription(Optional.ofNullable(description));
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

    if (input.copyCondition != null) {
      try {
        approvalQueryBuilder.parse(input.copyCondition);
      } catch (QueryParseException e) {
        throw new BadRequestException(
            "unable to parse copy condition. got: " + input.copyCondition + ". " + e.getMessage(),
            e);
      }
      if (Boolean.TRUE.equals(input.unsetCopyCondition)) {
        throw new BadRequestException("can't set and unset copyCondition in the same request");
      }
      labelType.setCopyCondition(Strings.emptyToNull(input.copyCondition));
    }
    if (Boolean.TRUE.equals(input.unsetCopyCondition)) {
      labelType.setCopyCondition(null);
    }

    if (input.allowPostSubmit != null) {
      labelType.setAllowPostSubmit(input.allowPostSubmit);
    }

    if (input.ignoreSelfApproval != null) {
      labelType.setIgnoreSelfApproval(input.ignoreSelfApproval);
    }

    LabelType lt = labelType.build();
    config.upsertLabelType(lt);

    return lt;
  }
}
