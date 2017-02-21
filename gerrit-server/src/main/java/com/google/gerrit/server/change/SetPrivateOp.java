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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.notedb.ChangeUpdate;

public class SetPrivateOp extends BatchUpdate.Op {
  private boolean isPrivate;

  SetPrivateOp(boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  @Override
  public boolean updateChange(BatchUpdate.ChangeContext ctx) {
    Change change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    change.setPrivate(isPrivate);
    change.setLastUpdatedOn(ctx.getWhen());
    update.setPrivate(isPrivate);
    return true;
  }
}
