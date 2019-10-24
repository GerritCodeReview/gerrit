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

import static java.util.stream.Collectors.toMap;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Singleton
public class ListLabels implements RestReadView<ProjectResource> {
  private final PermissionBackend permissionBackend;

  @Inject
  public ListLabels(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<List<LabelDefinitionInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException {
    permissionBackend.currentUser().project(rsrc.getNameKey()).check(ProjectPermission.READ_CONFIG);

    Collection<LabelType> labelTypes =
        rsrc.getProjectState().getConfig().getLabelSections().values();
    List<LabelDefinitionInfo> labels = new ArrayList<>(labelTypes.size());
    for (LabelType labelType : labelTypes) {
      LabelDefinitionInfo label = new LabelDefinitionInfo();
      label.name = labelType.getName();
      label.function = labelType.getFunction().getFunctionName();
      label.values =
          labelType.getValues().stream()
              .collect(toMap(LabelValue::formatValue, LabelValue::getText));
      label.defaultValue = labelType.getDefaultValue();
      label.branches = labelType.getRefPatterns() != null ? labelType.getRefPatterns() : null;
      label.canOverride = toBoolean(labelType.canOverride());
      label.copyAnyScore = toBoolean(labelType.isCopyAnyScore());
      label.copyMinScore = toBoolean(labelType.isCopyMinScore());
      label.copyMaxScore = toBoolean(labelType.isCopyMaxScore());
      label.copyAllScoresIfNoChange = toBoolean(labelType.isCopyAllScoresIfNoChange());
      label.copyAllScoresIfNoCodeChange = toBoolean(labelType.isCopyAllScoresIfNoCodeChange());
      label.copyAllScoresOnTrivialRebase = toBoolean(labelType.isCopyAllScoresOnTrivialRebase());
      label.copyAllScoresOnMergeFirstParentUpdate =
          toBoolean(labelType.isCopyAllScoresOnMergeFirstParentUpdate());
      label.allowPostSubmit = toBoolean(labelType.allowPostSubmit());
      label.ignoreSelfApproval = toBoolean(labelType.ignoreSelfApproval());
      labels.add(label);
    }
    labels.sort(Comparator.comparing(l -> l.name));
    return Response.ok(labels);
  }

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }
}
