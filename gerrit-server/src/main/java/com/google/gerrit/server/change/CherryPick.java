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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.CherryPick.Input;
import com.google.gerrit.server.changedetail.CherryPickChange;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Inject;

class CherryPick implements RestModifyView<RevisionResource, Input> {
  private final CherryPickChange cherryPickChange;
  private final ChangeJson json;

  static class Input {
    String message;
    String destination;
  }

  @Inject
  CherryPick(CherryPickChange cherryPickChange, ChangeJson json) {
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(RevisionResource revision, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final Change.Id changeId = revision.getChange().getId();
    final ChangeControl control = revision.getControl();
    if (!control.getProjectControl().controlForRef(input.destination)
        .canUpload()) {
      throw new AuthException("Not allowed to cherry pick "
          + changeId.toString() + " to " + input.destination);
    }

    final PatchSet.Id patchSetId = revision.getPatchSet().getId();
    Change.Id cherryPickedChangeId =
        cherryPickChange.cherryPick(patchSetId, input.message,
            input.destination);

    return json.format(cherryPickedChangeId);
  }
}
