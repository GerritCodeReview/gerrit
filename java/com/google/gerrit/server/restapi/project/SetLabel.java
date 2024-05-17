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
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetLabel implements RestModifyView<LabelResource, LabelDefinitionInput> {
  private final ApprovalQueryBuilder approvalQueryBuilder;
  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  public SetLabel(
      ApprovalQueryBuilder approvalQueryBuilder, RepoMetaDataUpdater repoMetaDataUpdater) {
    this.approvalQueryBuilder = approvalQueryBuilder;
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<LabelDefinitionInfo> apply(LabelResource rsrc, LabelDefinitionInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          PermissionBackendException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new LabelDefinitionInput();
    }

    LabelType labelType = rsrc.getLabelType();

    try (var configUpdater =
        repoMetaDataUpdater.configUpdater(
            rsrc.getProject().getNameKey(), input.commitMessage, "Update label")) {
      ProjectConfig config = configUpdater.getConfig();

      if (updateLabel(config, labelType, input)) {
        String newName = Strings.nullToEmpty(input.name).trim();
        labelType =
            config.getLabelSections().get(newName.isEmpty() ? labelType.getName() : newName);
        configUpdater.commitConfigUpdate();
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
  @SuppressWarnings("deprecation")
  public boolean updateLabel(ProjectConfig config, LabelType labelType, LabelDefinitionInput input)
      throws BadRequestException, ResourceConflictException {
    boolean dirty = false;
    LabelType.Builder labelTypeBuilder = labelType.toBuilder();

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
          LabelType.checkName(newName);
        } catch (IllegalArgumentException e) {
          throw new BadRequestException("invalid name: " + input.name, e);
        }

        labelTypeBuilder.setName(newName);
        dirty = true;
      }
    }

    if (input.description != null) {
      String description = Strings.emptyToNull(input.description.trim());
      labelTypeBuilder.setDescription(Optional.ofNullable(description));
      dirty = true;
    }

    if (input.function != null) {
      if (input.function.trim().isEmpty()) {
        throw new BadRequestException("function cannot be empty");
      }
      labelTypeBuilder.setFunction(LabelDefinitionInputParser.parseFunction(input.function));
      dirty = true;
    }

    if (input.values != null) {
      if (input.values.isEmpty()) {
        throw new BadRequestException("values cannot be empty");
      }
      labelTypeBuilder.setValues(LabelDefinitionInputParser.parseValues(input.values));
      dirty = true;
    }

    if (input.defaultValue != null) {
      labelTypeBuilder.setDefaultValue(
          LabelDefinitionInputParser.parseDefaultValue(labelTypeBuilder, input.defaultValue));
      dirty = true;
    }

    if (input.branches != null) {
      labelTypeBuilder.setRefPatterns(LabelDefinitionInputParser.parseBranches(input.branches));
      dirty = true;
    }

    if (input.canOverride != null) {
      labelTypeBuilder.setCanOverride(input.canOverride);
      dirty = true;
    }

    input.copyCondition = Strings.emptyToNull(input.copyCondition);
    if (input.copyCondition != null) {
      try {
        @SuppressWarnings("unused")
        var unused = approvalQueryBuilder.parse(input.copyCondition);
      } catch (QueryParseException e) {
        throw new BadRequestException(
            "unable to parse copy condition. got: " + input.copyCondition + ". " + e.getMessage(),
            e);
      }
      labelTypeBuilder.setCopyCondition(input.copyCondition);
      dirty = true;
      if (Boolean.TRUE.equals(input.unsetCopyCondition)) {
        throw new BadRequestException("can't set and unset copyCondition in the same request");
      }
    }
    if (Boolean.TRUE.equals(input.unsetCopyCondition)) {
      labelTypeBuilder.setCopyCondition(null);
      dirty = true;
    }

    if (input.allowPostSubmit != null) {
      labelTypeBuilder.setAllowPostSubmit(input.allowPostSubmit);
      dirty = true;
    }

    if (input.ignoreSelfApproval != null) {
      labelTypeBuilder.setIgnoreSelfApproval(input.ignoreSelfApproval);
      dirty = true;
    }

    config.getLabelSections().remove(labelType.getName());
    config.upsertLabelType(labelTypeBuilder.build());

    return dirty;
  }
}
