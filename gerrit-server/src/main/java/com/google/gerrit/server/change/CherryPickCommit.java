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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class CherryPickCommit
    extends RetryingRestModifyView<CommitResource, CherryPickInput, ChangeInfo> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;

  @Inject
  CherryPickCommit(
      RetryHelper retryHelper,
      Provider<CurrentUser> user,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      PermissionBackend permissionBackend) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  public ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, CommitResource rsrc, CherryPickInput input)
      throws OrmException, IOException, UpdateException, RestApiException,
          PermissionBackendException {
    RevCommit commit = rsrc.getCommit();
    String message = Strings.nullToEmpty(input.message).trim();
    input.message = message.isEmpty() ? commit.getFullMessage() : message;
    String destination = Strings.nullToEmpty(input.destination).trim();
    input.parent = input.parent == null ? 1 : input.parent;
    Project.NameKey projectName = rsrc.getProject().getProject().getNameKey();

    if (destination.isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    String refName = RefNames.fullName(destination);
    CreateChange.checkValidCLA(rsrc.getProject());
    permissionBackend
        .user(user)
        .project(projectName)
        .ref(refName)
        .check(RefPermission.CREATE_CHANGE);

    try {
      Change.Id cherryPickedChangeId =
          cherryPickChange.cherryPick(
              updateFactory,
              null,
              null,
              null,
              null,
              projectName,
              commit,
              input,
              rsrc.getProject().controlForRef(refName));
      return json.noOptions().format(projectName, cherryPickedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
