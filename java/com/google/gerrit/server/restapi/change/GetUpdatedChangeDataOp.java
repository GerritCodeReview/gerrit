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

/**
 * {@link BatchUpdateOp} that gets the updated change data from the {@link PostUpdateContext} and
 * makes it available outside the {@link com.google.gerrit.server.update.BatchUpdate}.
 *
 * <p>The updated change data is already cached in the {@link PostUpdateContext} when it is created
 * during the change indexing so that retrieving it here adds no extra cost. REST endpoints that
 * need the updated change data (e.g. to include data from it into the response) should get the
 * updated change data via this op, rather than re-reading the updated change on their own (as that
 * may trigger expensive operations such as re-evaluating the submit requirements).
 */
public class GetUpdatedChangeDataOp implements BatchUpdateOp {
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

  /**
   * Returns the updated change data.
   *
   * @throws IllegalStateException thrown if invoked before this {@code PostReviewOp} has been
   *     executed
   */
  public ChangeData getChangeData() {
    checkState(changeData != null, "cannot retrieve changeData, op has not been executed yet");
    return changeData;
  }
}
