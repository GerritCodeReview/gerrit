// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.kohsuke.args4j.Option;

/** List submit requirements in a project. */
public class ListSubmitRequirements implements RestReadView<ProjectResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;

  @Inject
  public ListSubmitRequirements(Provider<CurrentUser> user, PermissionBackend permissionBackend) {
    this.user = user;
    this.permissionBackend = permissionBackend;
  }

  @Option(name = "--inherited", usage = "to include inherited submit requirements")
  private boolean inherited;

  public ListSubmitRequirements withInherited(boolean inherited) {
    this.inherited = inherited;
    return this;
  }

  @Override
  public Response<List<SubmitRequirementInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (inherited) {
      List<SubmitRequirementInfo> allSubmitRequirements = new ArrayList<>();
      for (ProjectState projectState : rsrc.getProjectState().treeInOrder()) {
        try {
          permissionBackend
              .currentUser()
              .project(projectState.getNameKey())
              .check(ProjectPermission.READ_CONFIG);
        } catch (AuthException e) {
          throw new AuthException(projectState.getNameKey() + ": " + e.getMessage(), e);
        }
        allSubmitRequirements.addAll(listSubmitRequirements(projectState));
      }
      return Response.ok(allSubmitRequirements);
    }

    permissionBackend.currentUser().project(rsrc.getNameKey()).check(ProjectPermission.READ_CONFIG);
    return Response.ok(listSubmitRequirements(rsrc.getProjectState()));
  }

  private List<SubmitRequirementInfo> listSubmitRequirements(ProjectState projectState) {
    Collection<SubmitRequirement> submitRequirements =
        projectState.getConfig().getSubmitRequirementSections().values();
    List<SubmitRequirementInfo> infos = new ArrayList<>(submitRequirements.size());
    submitRequirements.stream().forEach(sr -> infos.add(SubmitRequirementJson.format(sr)));
    return infos;
  }
}
