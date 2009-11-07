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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

class SaveDraft extends Handler<PatchLineComment> {
  interface Factory {
    SaveDraft create(PatchLineComment comment);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;

  private final PatchLineComment comment;

  @Inject
  SaveDraft(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      @Assisted final PatchLineComment comment) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;

    this.comment = comment;
  }

  @Override
  public PatchLineComment call() throws NoSuchChangeException, OrmException {
    if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
      throw new IllegalStateException("Comment published");
    }

    final Patch.Key patchKey = comment.getKey().getParentKey();
    final PatchSet.Id patchSetId = patchKey.getParentKey();
    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    changeControlFactory.validateFor(changeId);
    if (db.patchSets().get(patchSetId) == null) {
      throw new NoSuchChangeException(changeId);
    }

    final Account.Id me = currentUser.getAccountId();
    if (comment.getKey().get() == null) {
      if (comment.getLine() < 1) {
        throw new IllegalStateException("Comment line must be >= 1, not "
            + comment.getLine());
      }

      if (comment.getParentUuid() != null) {
        final PatchLineComment parent =
            db.patchComments().get(
                new PatchLineComment.Key(patchKey, comment.getParentUuid()));
        if (parent == null || parent.getSide() != comment.getSide()) {
          throw new IllegalStateException("Parent comment must be on same side");
        }
      }

      final PatchLineComment nc =
          new PatchLineComment(new PatchLineComment.Key(patchKey, ChangeUtil
              .messageUUID(db)), comment.getLine(), me, comment.getParentUuid());
      nc.setSide(comment.getSide());
      nc.setMessage(comment.getMessage());
      db.patchComments().insert(Collections.singleton(nc));
      return nc;

    } else {
      if (!me.equals(comment.getAuthor())) {
        throw new NoSuchChangeException(changeId);
      }
      comment.updated();
      db.patchComments().update(Collections.singleton(comment));
      return comment;
    }
  }
}
