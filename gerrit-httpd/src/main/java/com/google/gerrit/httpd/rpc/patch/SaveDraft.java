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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
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

    db.changes().beginTransaction(changeId);
    try {
      changeControlFactory.validateFor(changeId);
      if (db.patchSets().get(patchSetId) == null) {
        throw new NoSuchChangeException(changeId);
      }

      final Account.Id me = currentUser.getAccountId();
      if (comment.getKey().get() == null) {
        if (comment.getLine() < 0) {
          throw new IllegalStateException("Comment line must be >= 0, not "
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
        if (comment.getRange() != null
            && comment.getLine() != comment.getRange().getEndLine()) {
            throw new IllegalStateException(
              "Range endLine must be on the same line as the comment");
        }

        final PatchLineComment nc =
            new PatchLineComment(new PatchLineComment.Key(patchKey,
                ChangeUtil.messageUUID(db)), comment.getLine(), me,
                comment.getParentUuid(), TimeUtil.nowTs());
        nc.setSide(comment.getSide());
        nc.setMessage(comment.getMessage());
        nc.setRange(comment.getRange());
        db.patchComments().insert(Collections.singleton(nc));
        db.commit();
        return nc;

      } else {
        if (!me.equals(comment.getAuthor())) {
          throw new NoSuchChangeException(changeId);
        }
        comment.updated(TimeUtil.nowTs());
        db.patchComments().update(Collections.singleton(comment));
        db.commit();
        return comment;
      }
    } finally {
      db.rollback();
    }
  }
}
