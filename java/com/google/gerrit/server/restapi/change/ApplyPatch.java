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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ApplyPatch
    implements RestModifyView<ChangeResource, ApplyPatchPatchSetInput>, UiAction<ChangeResource> {

  private final PermissionBackend permissionBackend;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;

  @Inject
  ApplyPatch(
      PermissionBackend permissionBackend,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchPatchSetInput input)
      throws AuthException, IOException, ResourceConflictException, PermissionBackendException {
    NameKey project = rsrc.getProject();
    contributorAgreements.check(project, rsrc.getUser());

    BranchNameKey destBranch = rsrc.getChange().getDest();
    permissionBackend
        .currentUser()
        .project(project)
        .ref(destBranch.branch())
        .check(RefPermission.CREATE_CHANGE);
    projectCache.get(project).orElseThrow(illegalState(rsrc.getProject())).checkStatePermitsWrite();

    throw new NotImplementedException("ApplyPatch is not yet implemented.");
  }

  @Override
  public Description getDescription(ChangeResource rsrc) throws Exception {
    boolean projectStatePermitsWrite =
        projectCache.get(rsrc.getProject()).map(ProjectState::statePermitsWrite).orElse(false);
    return new Description()
        .setLabel("Apply patch")
        .setTitle("Applies the supplied patch into the current change.")
        .setVisible(
            and(
                projectStatePermitsWrite,
                permissionBackend
                    .currentUser()
                    .project(rsrc.getProject())
                    .testCond(ProjectPermission.CREATE_CHANGE)));
  }
}
