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

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
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
  private final Provider<ReviewDb> dbProvider;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;

  @Inject
  CherryPick(
      RetryHelper retryHelper,
      Provider<ReviewDb> dbProvider,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource revision, CherryPickInput input)
      throws OrmException, IOException, UpdateException, RestApiException {
    final ChangeControl control = revision.getControl();
    input.parent = input.parent == null ? 1 : input.parent;

    if (input.message == null || input.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (input.destination == null || input.destination.trim().isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    if (!control.isVisible(dbProvider.get())) {
      throw new AuthException("Cherry pick not permitted");
    }

    ProjectControl projectControl = control.getProjectControl();
    Capable capable = projectControl.canPushToAtLeastOneRef();
    if (capable != Capable.OK) {
      throw new AuthException(capable.getMessage());
    }

    RefControl refControl = projectControl.controlForRef(RefNames.fullName(input.destination));
    if (!refControl.canUpload()) {
      throw new AuthException(
          "Not allowed to cherry pick "
              + revision.getChange().getId().toString()
              + " to "
              + input.destination);
    }

    try {
      Change.Id cherryPickedChangeId =
          cherryPickChange.cherryPick(
              updateFactory, revision.getChange(), revision.getPatchSet(), input, refControl);
      return json.noOptions().format(revision.getProject(), cherryPickedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException | NoSuchChangeException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    return new UiAction.Description()
        .setLabel("Cherry Pick")
        .setTitle("Cherry pick change to a different branch")
        .setVisible(resource.getControl().getProjectControl().canUpload() && resource.isCurrent());
  }
}
