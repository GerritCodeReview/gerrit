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

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** REST endpoint that allows to add, update and delete label definitions in a batch. */
@Singleton
public class PostLabels
    extends AbstractPostCollection<String, LabelResource, LabelDefinitionInput, BatchLabelInput> {
  private final DeleteLabel deleteLabel;
  private final CreateLabel createLabel;
  private final SetLabel setLabel;

  @Inject
  public PostLabels(
      Provider<CurrentUser> user,
      DeleteLabel deleteLabel,
      CreateLabel createLabel,
      SetLabel setLabel,
      RepoMetaDataUpdater updater) {
    super(updater, user);
    this.deleteLabel = deleteLabel;
    this.createLabel = createLabel;
    this.setLabel = setLabel;
  }

  @Override
  public String defaultCommitMessage() {
    return "Update labels";
  }

  @Override
  protected boolean updateItem(ProjectConfig config, String name, LabelDefinitionInput resource)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException {
    LabelType labelType = config.getLabelSections().get(name);
    if (labelType == null) {
      throw new UnprocessableEntityException(String.format("label %s not found", name));
    }
    if (resource.commitMessage != null) {
      throw new BadRequestException("commit message on label definition input not supported");
    }

    return setLabel.updateLabel(config, labelType, resource);
  }

  @Override
  protected void createItem(ProjectConfig config, LabelDefinitionInput labelInput)
      throws BadRequestException, ResourceConflictException {
    if (labelInput.name == null || labelInput.name.trim().isEmpty()) {
      throw new BadRequestException("label name is required for new label");
    }
    if (labelInput.commitMessage != null) {
      throw new BadRequestException("commit message on label definition input not supported");
    }
    @SuppressWarnings("unused")
    var unused = createLabel.createLabel(config, labelInput.name.trim(), labelInput);
  }

  @Override
  protected void deleteItem(ProjectConfig config, String name) throws UnprocessableEntityException {
    if (!deleteLabel.deleteLabel(config, name)) {
      throw new UnprocessableEntityException(String.format("label %s not found", name));
    }
  }
}
