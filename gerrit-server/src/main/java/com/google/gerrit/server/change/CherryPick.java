// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class CherryPick
    extends RetryingRestModifyView<RevisionResource, CherryPickInput, ChangeInfo>
    implements UiAction<RevisionResource> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;

  @Inject
  CherryPick(
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      RetryHelper retryHelper,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  public ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, CherryPickInput input)
      throws OrmException, IOException, UpdateException, RestApiException,
          PermissionBackendException {
    input.parent = input.parent == null ? 1 : input.parent;
    if (input.message == null || input.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (input.destination == null || input.destination.trim().isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    String refName = RefNames.fullName(input.destination);
    CreateChange.checkValidCLA(rsrc.getControl().getProjectControl());
    permissionBackend
        .user(user)
        .project(rsrc.getChange().getProject())
        .ref(refName)
        .check(RefPermission.CREATE_CHANGE);

    try {
      Change.Id cherryPickedChangeId =
          cherryPickChange.cherryPick(
              updateFactory,
              rsrc.getChange(),
              rsrc.getPatchSet(),
              input,
              rsrc.getControl().getProjectControl().controlForRef(refName));
      return json.noOptions().format(rsrc.getProject(), cherryPickedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException | NoSuchChangeException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    return new UiAction.Description()
        .setLabel("Cherry Pick")
        .setTitle("Cherry pick change to a different branch")
        .setVisible(
            rsrc.isCurrent()
                && permissionBackend
                    .user(user)
                    .project(rsrc.getProject())
                    .testOrFalse(ProjectPermission.CREATE_CHANGE));
  }
}
