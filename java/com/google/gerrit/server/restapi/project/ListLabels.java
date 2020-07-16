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
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.kohsuke.args4j.Option;

public class ListLabels implements RestReadView<ProjectResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;

  @Inject
  public ListLabels(Provider<CurrentUser> user, PermissionBackend permissionBackend) {
    this.user = user;
    this.permissionBackend = permissionBackend;
  }

  @Option(name = "--inherited", usage = "to include inherited label definitions")
  private boolean inherited;

  public ListLabels withInherited(boolean inherited) {
    this.inherited = inherited;
    return this;
  }

  @Override
  public Response<List<LabelDefinitionInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (inherited) {
      List<LabelDefinitionInfo> allLabels = new ArrayList<>();
      for (ProjectState projectState : rsrc.getProjectState().treeInOrder()) {
        try {
          permissionBackend
              .currentUser()
              .project(projectState.getNameKey())
              .check(ProjectPermission.READ_CONFIG);
        } catch (AuthException e) {
          throw new AuthException(projectState.getNameKey() + ": " + e.getMessage(), e);
        }
        allLabels.addAll(listLabels(projectState));
      }
      return Response.ok(allLabels);
    }

    permissionBackend.currentUser().project(rsrc.getNameKey()).check(ProjectPermission.READ_CONFIG);
    return Response.ok(listLabels(rsrc.getProjectState()));
  }

  private List<LabelDefinitionInfo> listLabels(ProjectState projectState) {
    Collection<LabelType> labelTypes = projectState.getConfig().getLabelSections().values();
    List<LabelDefinitionInfo> labels = new ArrayList<>(labelTypes.size());
    for (LabelType labelType : labelTypes) {
      labels.add(LabelDefinitionJson.format(projectState.getNameKey(), labelType));
    }
    labels.sort(Comparator.comparing(l -> l.name));
    return labels;
  }
}
