// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;

public class ReadChangeDataOp implements BatchUpdateOp {
  private Change change;
  private ChangeData changeData;

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException {
    change = ctx.getChange();
    return false;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    changeData = ctx.getChangeData(change);
  }

  public ChangeData getChangeData() {
    checkState(changeData != null, "cannot retrieve changeData, op has not been executed yet");
    return changeData;
  }
}
