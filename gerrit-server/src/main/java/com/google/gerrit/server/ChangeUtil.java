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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.TrackingId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.TrackingFooter;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;


import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.NB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

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

  public static void updateTrackingIds(ReviewDb db, Change change,
      TrackingFooters trackingFooters, List<FooterLine> footerLines)
      throws OrmException {
    if (trackingFooters.getTrackingFooters().isEmpty() || footerLines.isEmpty()) {
      return;
    }

    final Set<TrackingId> want = new HashSet<TrackingId>();
    final Set<TrackingId> have = new HashSet<TrackingId>( //
        db.trackingIds().byChange(change.getId()).toList());

    for (final TrackingFooter footer : trackingFooters.getTrackingFooters()) {
      for (final FooterLine footerLine : footerLines) {
        if (footerLine.matches(footer.footerKey())) {
          // supporting multiple tracking-ids on a single line
          final Matcher m = footer.match().matcher(footerLine.getValue());
          while (m.find()) {
            if (m.group().isEmpty()) {
              continue;
            }

            String idstr;
            if (m.groupCount() > 0) {
              idstr = m.group(1);
            } else {
              idstr = m.group();
            }

            if (idstr.isEmpty()) {
              continue;
            }
            if (idstr.length() > TrackingId.TRACKING_ID_MAX_CHAR) {
              continue;
            }

            want.add(new TrackingId(change.getId(), idstr, footer.system()));
          }
        }
      }
    }

    // Only insert the rows we don't have, and delete rows we don't match.
    //
    final Set<TrackingId> toInsert = new HashSet<TrackingId>(want);
    final Set<TrackingId> toDelete = new HashSet<TrackingId>(have);

    toInsert.removeAll(have);
    toDelete.removeAll(want);

    db.trackingIds().insert(toInsert);
    db.trackingIds().delete(toDelete);
  }

  public static void submit(MergeOp.Factory opFactory, PatchSet.Id patchSetId,
      IdentifiedUser user, ReviewDb db, MergeQueue merger) throws OrmException {
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
      merger.merge(opFactory, change.getDest());
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

  public static void abandon(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final AbandonedSender.Factory abandonedSenderFactory,
      final ChangeHookRunner hooks) throws NoSuchChangeException,
      EmailException, OrmException {
    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
            .messageUUID(db)), user.getAccountId());
    final StringBuilder msgBuf =
        new StringBuilder("Patch Set " + patchSetId.get() + ": Abandoned");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    cmsg.setMessage(msgBuf.toString());

    final Change change = db.changes().atomicUpdate(changeId,
        new AtomicUpdate<Change>() {
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
      cm.setFrom(user.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();
    }

    hooks.doChangeAbandonedHook(change, user.getAccount(), message);
  }

  public static void restore(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final AbandonedSender.Factory abandonedSenderFactory,
      final ChangeHookRunner hooks) throws NoSuchChangeException,
      EmailException, OrmException {
    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
            .messageUUID(db)), user.getAccountId());
    final StringBuilder msgBuf =
        new StringBuilder("Patch Set " + patchSetId.get() + ": Restored");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    cmsg.setMessage(msgBuf.toString());

    Change change = db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.ABANDONED
            && change.currentPatchSetId().equals(patchSetId)) {
          change.setStatus(Change.Status.NEW);
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
      cm.setFrom(user.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();
    }

    hooks.doChangeRestoreHook(change, user.getAccount(), message);
  }

  public static String sortKey(long lastUpdated, int id){
    // The encoding uses minutes since Wed Oct 1 00:00:00 2008 UTC.
    // We overrun approximately 4,085 years later, so ~6093.
    //
    final long lastUpdatedOn = (lastUpdated / 1000L) - 1222819200L;
    final StringBuilder r = new StringBuilder(16);
    r.setLength(16);
    formatHexInt(r, 0, (int) (lastUpdatedOn / 60));
    formatHexInt(r, 8, id);
    return r.toString();
  }

  public static void computeSortKey(final Change c) {
    long lastUpdated = c.getLastUpdatedOn().getTime();
    int id = c.getId().get();
    c.setSortKey(sortKey(lastUpdated, id));
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
