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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.CherryPick.Input;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

class CherryPick implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final Provider<CherryPickChange> cherryPickChange;
  private final ChangeJson json;

  static class Input {
    String message;
    String destination;
  }

  @Inject
  CherryPick(Provider<ReviewDb> dbProvider,
      Provider<CherryPickChange> cherryPickChange,
      ChangeJson json) {
    this.dbProvider = dbProvider;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  public Object apply(RevisionResource revision, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final ChangeControl control = revision.getControl();

    if (input.message == null || input.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (input.destination == null || input.destination.trim().isEmpty()) {
      throw new BadRequestException("destination must be non-empty");
    }

    ReviewDb db = dbProvider.get();
    if (!control.isVisible(db)) {
      throw new AuthException("Cherry pick not permitted");
    }

    String refName = input.destination;
    if (!refName.startsWith("refs/")) {
      refName = "refs/heads/" + input.destination;
    }

    RefControl refControl = control.getProjectControl().controlForRef(refName);
    if (!refControl.canUpload()) {
      throw new AuthException("Not allowed to cherry pick "
          + revision.getChange().getId().toString() + " to "
          + input.destination);
    }

    final PatchSet.Id patchSetId = revision.getPatchSet().getId();
    try {
      Change.Id cherryPickedChangeId = cherryPickChange.get().cherryPick(
          patchSetId, input.message,
          input.destination, refControl);
      return json.format(cherryPickedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (MergeException  e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    return new UiAction.Description()
      .setLabel("Cherry Pick")
      .setTitle("Cherry pick change to a different branch")
      .setVisible(resource.getControl().getProjectControl().canUpload());
  }
}
