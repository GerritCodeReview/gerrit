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

import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigChangeCreator;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostLabelsReview implements RestModifyView<ProjectResource, BatchLabelInput> {

  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final PostLabels postLabels;

  @Inject
  PostLabelsReview(RepoMetaDataUpdater repoMetaDataUpdater, PostLabels postLabels) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.postLabels = postLabels;
  }

  @Override
  public Response<ChangeInfo> apply(ProjectResource rsrc, BatchLabelInput input)
      throws PermissionBackendException,
          IOException,
          ConfigInvalidException,
          UpdateException,
          RestApiException {
    if (input == null) {
      input = new BatchLabelInput();
    }

    if (input.create != null) {
      for (LabelDefinitionInput labelDefinitionInput : input.create) {
        if (labelDefinitionInput.function == null) {
          labelDefinitionInput.function = LabelFunction.NO_OP.getFunctionName();
        }
        LabelDefinitionInputValidator.validate(labelDefinitionInput.name, labelDefinitionInput);
      }
    }
    if (input.update != null) {
      for (Map.Entry<String, LabelDefinitionInput> updateEntry : input.update.entrySet()) {
        LabelDefinitionInputValidator.validate(updateEntry.getKey(), updateEntry.getValue());
      }
    }

    try (ConfigChangeCreator creator =
        repoMetaDataUpdater.configChangeCreator(
            rsrc.getNameKey(), input.commitMessage, "Review labels change")) {
      ProjectConfig config = creator.getConfig();
      var unused = postLabels.updateProjectConfig(config, input);
      return creator.createChange();
    }
  }
}
