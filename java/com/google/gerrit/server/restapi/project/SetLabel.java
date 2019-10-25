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
import com.google.common.primitives.Shorts;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.RefPattern;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetLabel implements RestModifyView<LabelResource, LabelDefinitionInput> {
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public SetLabel(
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
  }

  @Override
  public Response<LabelDefinitionInfo> apply(LabelResource rsrc, LabelDefinitionInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          PermissionBackendException, IOException, ConfigInvalidException {
    permissionBackend
        .currentUser()
        .project(rsrc.getProject().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new LabelDefinitionInput();
    }

    LabelType labelType = rsrc.getLabelType();

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProject().getNameKey())) {
      boolean dirty = false;

      ProjectConfig config = projectConfigFactory.read(md);
      config.getLabelSections().remove(labelType.getName());

      if (input.name != null) {
        String newName = input.name.trim();
        if (newName.isEmpty()) {
          throw new BadRequestException("name cannot be empty");
        }
        if (!newName.equals(labelType.getName())) {
          if (config.getLabelSections().containsKey(newName)) {
            throw new ResourceConflictException("name " + newName + " already in use");
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
        String newFunctionName = input.function.trim();
        if (newFunctionName.isEmpty()) {
          throw new BadRequestException("function cannot be empty");
        }
        Optional<LabelFunction> newFunction = LabelFunction.parse(newFunctionName);
        if (!newFunction.isPresent()) {
          throw new BadRequestException("unknown function: " + input.function);
        }
        labelType.setFunction(newFunction.get());
        dirty = true;
      }

      if (input.values != null) {
        if (input.values.isEmpty()) {
          throw new BadRequestException("values cannot be empty");
        }

        List<LabelValue> newValues = new ArrayList<>();
        for (Entry<String, String> e : input.values.entrySet()) {
          short value;
          try {
            value = Shorts.checkedCast(PermissionRule.parseInt(e.getKey().trim()));
          } catch (NumberFormatException ex) {
            throw new BadRequestException("invalid value: " + e.getKey(), ex);
          }
          String valueDescription = e.getValue().trim();
          if (valueDescription.isEmpty()) {
            throw new BadRequestException(
                "description for value '" + e.getKey() + "' cannot be empty");
          }
          newValues.add(new LabelValue(value, valueDescription));
        }
        labelType.setValues(newValues);
        dirty = true;
      }

      if (input.defaultValue != null) {
        if (labelType.getValue(input.defaultValue) == null) {
          throw new BadRequestException("invalid default value: " + input.defaultValue);
        }
        labelType.setDefaultValue(input.defaultValue);
        dirty = true;
      }

      if (input.branches != null) {
        List<String> newBranches = new ArrayList<>();
        for (String branch : input.branches) {
          String newBranch = branch.trim();
          if (newBranch.isEmpty()) {
            continue;
          }
          if (!RefPattern.isRE(newBranch) && !newBranch.startsWith(RefNames.REFS)) {
            newBranch = RefNames.REFS_HEADS + newBranch;
          }
          try {
            RefPattern.validate(newBranch);
          } catch (InvalidNameException e) {
            throw new BadRequestException("invalid branch: " + branch, e);
          }
          newBranches.add(newBranch);
        }
        labelType.setRefPatterns(newBranches);
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

      if (input.allowPostSubmit != null) {
        labelType.setAllowPostSubmit(input.allowPostSubmit);
        dirty = true;
      }

      if (input.ignoreSelfApproval != null) {
        labelType.setIgnoreSelfApproval(input.ignoreSelfApproval);
        dirty = true;
      }

      if (dirty) {
        config.getLabelSections().put(labelType.getName(), labelType);

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
}
