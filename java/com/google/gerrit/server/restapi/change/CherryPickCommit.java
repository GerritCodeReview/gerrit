// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.CherryPickChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CherryPickCommit implements RestModifyView<CommitResource, CherryPickInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;
  private final ContributorAgreementsChecker contributorAgreements;

  @Inject
  CherryPickCommit(
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      ContributorAgreementsChecker contributorAgreements) {
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
    this.contributorAgreements = contributorAgreements;
  }

  @Override
  public Response<CherryPickChangeInfo> apply(CommitResource rsrc, CherryPickInput input)
      throws IOException, UpdateException, RestApiException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException {
    String destination = Strings.nullToEmpty(input.destination).trim();
    input.parent = input.parent == null ? 1 : input.parent;
    Project.NameKey projectName = rsrc.getProjectState().getNameKey();

    if (destination.isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    String refName = RefNames.fullName(destination);
    contributorAgreements.check(projectName, user.get());
    permissionBackend
        .currentUser()
        .project(projectName)
        .ref(refName)
        .check(RefPermission.CREATE_CHANGE);
    rsrc.getProjectState().checkStatePermitsWrite();

    try {
      CherryPickChange.Result cherryPickResult =
          cherryPickChange.cherryPick(
              null,
              projectName,
              rsrc.getCommit(),
              input,
              BranchNameKey.create(rsrc.getProjectState().getNameKey(), refName));
      CherryPickChangeInfo changeInfo =
          json.noOptions()
              .format(projectName, cherryPickResult.changeId(), CherryPickChangeInfo::new);
      changeInfo.containsGitConflicts =
          !cherryPickResult.filesWithGitConflicts().isEmpty() ? true : null;
      return Response.ok(changeInfo);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
