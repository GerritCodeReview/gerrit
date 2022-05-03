// Copyright (C) 2022 The Android Open Source Project
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
import com.google.gerrit.server.mail.send.ChangeNoLongerSubmittableSender;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.ChangeNoLongerSubmittableEmail;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A {@link BatchUpdateOp} that triggers an asynchronously email when the change becomes
 * non-submittable due to a change update.
 *
 * @see ChangeNoLongerSubmittableEmail
 * @see ChangeNoLongerSubmittableSender
 */
public class ChangeNoLongerSubmittableOp implements BatchUpdateOp {
  public interface Factory {
    ChangeNoLongerSubmittableOp create(ChangeNoLongerSubmittableSender.UpdateKind updateKind);
  }

  private final ChangeNoLongerSubmittableEmail.Factory changeNoLongerSubmittableEmailFactory;
  private final ChangeNoLongerSubmittableSender.UpdateKind updateKind;

  private Change.Id changeId;
  private ObjectId preUpdateMetaId;

  @Inject
  ChangeNoLongerSubmittableOp(
      ChangeNoLongerSubmittableEmail.Factory changeNoLongerSubmittableEmailFactory,
      @Assisted ChangeNoLongerSubmittableSender.UpdateKind updateKind) {
    this.changeNoLongerSubmittableEmailFactory = changeNoLongerSubmittableEmailFactory;
    this.updateKind = updateKind;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    changeId = ctx.getChange().getId();
    preUpdateMetaId = ctx.getNotes().getMetaId();

    // Return false since we didn't touch the change here.
    return false;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    changeNoLongerSubmittableEmailFactory
        .create(ctx, changeId, updateKind, preUpdateMetaId)
        .sendIfNeededAsync();
  }
}
