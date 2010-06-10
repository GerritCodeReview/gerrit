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

package com.google.gerrit.server;

import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.NB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeUtil {
  private static int uuidPrefix;
  private static int uuidSeq;

  /**
   * Generate a new unique identifier for change message entities.
   *
   * @param db the database connection, used to increment the change message
   *        allocation sequence.
   * @return the new unique identifier.
   * @throws OrmException the database couldn't be incremented.
   */
  public static String messageUUID(final ReviewDb db) throws OrmException {
    final byte[] raw = new byte[8];
    fill(raw, db);
    return Base64.encodeBytes(raw);
  }

  private static synchronized void fill(byte[] raw, ReviewDb db)
      throws OrmException {
    if (uuidSeq == 0) {
      uuidPrefix = db.nextChangeMessageId();
      uuidSeq = Integer.MAX_VALUE;
    }
    NB.encodeInt32(raw, 0, uuidPrefix);
    NB.encodeInt32(raw, 4, uuidSeq--);
  }

  public static void touch(final Change change, ReviewDb db)
      throws OrmException {
    try {
      updated(change);
      db.changes().update(Collections.singleton(change));
    } catch (OrmConcurrencyException e) {
      // Ignore a concurrent update, we just wanted to tag it as newer.
    }
  }

  public static void updated(final Change c) {
    c.resetLastUpdatedOn();
    computeSortKey(c);
  }

  public static void submit(PatchSet.Id patchSetId, IdentifiedUser user, ReviewDb db, MergeQueue merger)
      throws OrmException {
    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSetApproval approval = createSubmitApproval(patchSetId, user, db);

    db.patchSetApprovals().upsert(Collections.singleton(approval));

    final Change change = db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.NEW) {
          change.setStatus(Change.Status.SUBMITTED);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });

    if (change.getStatus() == Change.Status.SUBMITTED) {
      merger.merge(change.getDest());
    }
  }

  public static PatchSetApproval createSubmitApproval(PatchSet.Id patchSetId, IdentifiedUser user, ReviewDb db)
      throws OrmException {
    final List<PatchSetApproval> allApprovals =
        new ArrayList<PatchSetApproval>(db.patchSetApprovals().byPatchSet(
            patchSetId).toList());

    final PatchSetApproval.Key akey =
        new PatchSetApproval.Key(patchSetId, user.getAccountId(), SUBMIT);

    for (final PatchSetApproval approval : allApprovals) {
      if (akey.equals(approval.getKey())) {
        approval.setValue((short) 1);
        approval.setGranted();
        return approval;
      }
    }
    return new PatchSetApproval(akey, (short) 1);
  }


  public static void computeSortKey(final Change c) {
    // The encoding uses minutes since Wed Oct 1 00:00:00 2008 UTC.
    // We overrun approximately 4,085 years later, so ~6093.
    //
    final long lastUpdatedOn =
        (c.getLastUpdatedOn().getTime() / 1000L) - 1222819200L;
    final StringBuilder r = new StringBuilder(16);
    r.setLength(16);
    formatHexInt(r, 0, (int) (lastUpdatedOn / 60));
    formatHexInt(r, 8, c.getId().get());
    c.setSortKey(r.toString());
  }

  private static final char[] hexchar =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', //
          'a', 'b', 'c', 'd', 'e', 'f'};

  private static void formatHexInt(final StringBuilder dst, final int p, int w) {
    int o = p + 7;
    while (o >= p && w != 0) {
      dst.setCharAt(o--, hexchar[w & 0xf]);
      w >>>= 4;
    }
    while (o >= p) {
      dst.setCharAt(o--, '0');
    }
  }
}
