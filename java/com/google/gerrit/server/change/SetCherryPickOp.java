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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SetCherryPickOp implements BatchUpdateOp {

  public interface Factory {
    SetCherryPickOp create(PatchSet.Id cherryPickOf);
  }

  private final PatchSet.Id newCherryPickOf;

  @Inject
  SetCherryPickOp(@Assisted PatchSet.Id newCherryPickOf) {
    this.newCherryPickOf = newCherryPickOf;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    Change change = ctx.getChange();
    if (newCherryPickOf.equals(change.getCherryPickOf())) {
      return false;
    }

    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    update.setCherryPickOf(newCherryPickOf.getCommaSeparatedChangeAndPatchSetId());
    return true;
  }
}
