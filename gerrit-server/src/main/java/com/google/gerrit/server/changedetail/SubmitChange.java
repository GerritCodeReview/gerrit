// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.changedetail;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;

import java.sql.Timestamp;
import java.util.Collections;

public class SubmitChange {

  public static Change submit(ReviewDb db, Change change, PatchSet ps,
      IdentifiedUser caller) throws OrmException {
    final Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    db.changes().beginTransaction(change.getId());
    try {
      approve(db, ps, caller, timestamp);
      change = db.changes().atomicUpdate(
        change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.setStatus(Change.Status.SUBMITTED);
              change.setLastUpdatedOn(timestamp);
              ChangeUtil.computeSortKey(change);
              return change;
            }
            return null;
          }
        });
      if (change == null) {
        return null;
      }
      db.commit();
    } finally {
      db.rollback();
    }
    return change;
  }

  private static void approve(ReviewDb db, PatchSet rev, IdentifiedUser caller,
      Timestamp timestamp) throws OrmException {
    PatchSetApproval submit = Iterables.getFirst(Iterables.filter(
      db.patchSetApprovals()
        .byPatchSetUser(rev.getId(), caller.getAccountId()),
      new Predicate<PatchSetApproval>() {
        @Override
        public boolean apply(PatchSetApproval input) {
          return input.isSubmit();
        }
      }), null);
    if (submit == null) {
      submit = new PatchSetApproval(
          new PatchSetApproval.Key(
              rev.getId(),
              caller.getAccountId(),
              LabelId.SUBMIT),
          (short) 1);
    }
    submit.setValue((short) 1);
    submit.setGranted(timestamp);
    db.patchSetApprovals().upsert(Collections.singleton(submit));
  }
}
