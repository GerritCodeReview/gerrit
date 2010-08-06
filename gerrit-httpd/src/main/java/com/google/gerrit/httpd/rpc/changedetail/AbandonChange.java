// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

class AbandonChange extends Handler<ChangeDetail> {
  interface Factory {
    AbandonChange create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;

  private final ChangeHookRunner hooks;

  @Inject
  AbandonChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final AbandonedSender.Factory abandonedSenderFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message, final ChangeHookRunner hooks) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
    this.hooks = hooks;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canAbandon()) {
      throw new NoSuchChangeException(changeId);
    }
    Change change = control.getChange();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
            .messageUUID(db)), currentUser.getAccountId());
    final StringBuilder msgBuf =
        new StringBuilder("Patch Set " + patchSetId.get() + ": Abandoned");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    cmsg.setMessage(msgBuf.toString());

    change = db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus().isOpen()
            && change.currentPatchSetId().equals(patchSetId)) {
          change.setStatus(Change.Status.ABANDONED);
          ChangeUtil.updated(change);
          return change;
        } else {
          return null;
        }
      }
    });

    if (change != null) {
      db.changeMessages().insert(Collections.singleton(cmsg));

      final List<PatchSetApproval> approvals =
          db.patchSetApprovals().byChange(changeId).toList();
      for (PatchSetApproval a : approvals) {
        a.cache(change);
      }
      db.patchSetApprovals().update(approvals);

      // Email the reviewers
      final AbandonedSender cm = abandonedSenderFactory.create(change);
      cm.setFrom(currentUser.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();
    }

    hooks.doChangeAbandonedHook(change, currentUser.getAccount(), message);

    return changeDetailFactory.create(changeId).call();
  }
}
