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

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.LabelApi;
import com.google.gerrit.extensions.common.InputWithCommitMessage;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.CreateLabel;
import com.google.gerrit.server.restapi.project.DeleteLabel;
import com.google.gerrit.server.restapi.project.GetLabel;
import com.google.gerrit.server.restapi.project.LabelsCollection;
import com.google.gerrit.server.restapi.project.SetLabel;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class LabelApiImpl implements LabelApi {
  interface Factory {
    LabelApiImpl create(ProjectResource project, String label);
  }

  private final LabelsCollection labels;
  private final CreateLabel createLabel;
  private final GetLabel getLabel;
  private final SetLabel setLabel;
  private final DeleteLabel deleteLabel;
  private final ProjectCache projectCache;
  private final String label;

  private ProjectResource project;

  @Inject
  LabelApiImpl(
      LabelsCollection labels,
      CreateLabel createLabel,
      GetLabel getLabel,
      SetLabel setLabel,
      DeleteLabel deleteLabel,
      ProjectCache projectCache,
      @Assisted ProjectResource project,
      @Assisted String label) {
    this.labels = labels;
    this.createLabel = createLabel;
    this.getLabel = getLabel;
    this.setLabel = setLabel;
    this.deleteLabel = deleteLabel;
    this.projectCache = projectCache;
    this.project = project;
    this.label = label;
  }

  @Override
  public LabelApi create(LabelDefinitionInput input) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = createLabel.apply(project, IdString.fromDecoded(label), input);

      // recreate project resource because project state was updated by creating the new label and
      // needs to be reloaded
      project =
          new ProjectResource(
              projectCache
                  .get(project.getNameKey())
                  .orElseThrow(illegalState(project.getNameKey())),
              project.getUser());
      return this;
    } catch (Exception e) {
      throw asRestApiException("Cannot create branch", e);
    }
  }

  @Override
  public LabelDefinitionInfo get() throws RestApiException {
    try {
      return getLabel.apply(resource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get label", e);
    }
  }

  @Override
  public LabelDefinitionInfo update(LabelDefinitionInput input) throws RestApiException {
    try {
      return setLabel.apply(resource(), input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot update label", e);
    }
  }

  @Override
  public void delete(@Nullable String commitMessage) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = deleteLabel.apply(resource(), new InputWithCommitMessage(commitMessage));
    } catch (Exception e) {
      throw asRestApiException("Cannot delete label", e);
    }
  }

  private LabelResource resource() throws RestApiException, PermissionBackendException {
    return labels.parse(project, IdString.fromDecoded(label));
  }
}
