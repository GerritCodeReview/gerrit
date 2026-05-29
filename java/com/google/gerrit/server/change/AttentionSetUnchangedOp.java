// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;

/**
 * Ensures that the attention set will not be changed, thus blocks {@link RemoveFromAttentionSetOp}
 * and {@link AddToAttentionSetOp} and updates in {@link ChangeUpdate}.
 */
public class AttentionSetUnchangedOp implements BatchUpdateOp {

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    update.ignoreFurtherAttentionSetUpdates();
    return true;
  }
}
