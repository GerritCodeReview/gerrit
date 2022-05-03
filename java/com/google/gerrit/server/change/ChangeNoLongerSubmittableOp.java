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

import com.google.gerrit.server.mail.send.ChangeNoLongerSubmittableSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.ChangeNoLongerSubmittableEmail;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * A {@link BatchUpdateOp} that triggers an asynchronously email when the change becomes
 * non-submittable due to a change update.
 *
 * <p>To know whether the change got non-submittable by the update and to include the submit
 * requirements from before/after the update into the email, we need to have {@link ChangeData}
 * instances from before and after the update available.
 *
 * <p>The {@link #preUpdateChangeData} is captured during the {@link #updateChange(ChangeContext)}
 * step. The {@link ChangeData} instance that we see here contains the state from before the update.
 * This {@link ChangeData} instance doesn't have submit requirement results populated yet.
 *
 * <p>The updated change data that reflects the change state after the update is retrieved by {@link
 * ChangeNoLongerSubmittableEmail} from {@link
 * PostUpdateContext#getChangeData(com.google.gerrit.entities.Change)}. The {@link ChangeData}
 * instance that is retrieved here was created for the (re)indexing and hence it has the submit
 * requirement results already populated.
 *
 * <p>Since the {@link #preUpdateChangeData} doesn't have submit requirement results populated yet
 * and evaluating submit requirements is expensive the email sending is done asynchronously, so that
 * this submit requirement computation doesn't add up to the latency of the user request.
 *
 * @see ChangeNoLongerSubmittableEmail
 */
public class ChangeNoLongerSubmittableOp implements BatchUpdateOp {
  public interface Factory {
    ChangeNoLongerSubmittableOp create(ChangeNoLongerSubmittableSender.UpdateKind updateKind);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeNoLongerSubmittableEmail.Factory changeNoLongerSubmittableEmailFactory;
  private final ChangeNoLongerSubmittableSender.UpdateKind updateKind;

  private ChangeData preUpdateChangeData;

  @Inject
  ChangeNoLongerSubmittableOp(
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory changeNotesFactory,
      ChangeNoLongerSubmittableEmail.Factory changeNoLongerSubmittableEmailFactory,
      @Assisted ChangeNoLongerSubmittableSender.UpdateKind updateKind) {
    this.changeDataFactory = changeDataFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.changeNoLongerSubmittableEmailFactory = changeNoLongerSubmittableEmailFactory;
    this.updateKind = updateKind;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    // Create change notes newly (defensive copy) as change notes contains a mutable Change
    // instance.
    preUpdateChangeData =
        changeDataFactory.create(
            changeNotesFactory.createChecked(
                ctx.getProject(), ctx.getChange().getId(), ctx.getNotes().getMetaId()));

    // Return false since we didn't touch the change here.
    return false;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    changeNoLongerSubmittableEmailFactory
        .create(ctx, updateKind, preUpdateChangeData)
        .sendIfNeededAsync();
  }
}
