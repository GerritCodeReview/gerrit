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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.TrackingId;
import com.google.gerrit.server.config.TrackingFooter;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.NB;

import java.io.IOException;
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

  public static void testMerge(MergeOp.Factory opFactory, Change change) {
    opFactory.create(change.getDest()).verifyMergeability(change);
  }

  public static void submit(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final ReviewDb db,
      final MergeOp.Factory opFactory, final MergeQueue merger)
      throws OrmException {
    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSetApproval approval = createSubmitApproval(patchSetId, user, db);

    db.patchSetApprovals().upsert(Collections.singleton(approval));

    final Change updatedChange = db.changes().atomicUpdate(changeId,
        new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.NEW) {
          change.setStatus(Change.Status.SUBMITTED);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });

    if (updatedChange.getStatus() == Change.Status.SUBMITTED) {
      merger.merge(opFactory, updatedChange.getDest());
    }
  }

  public static PatchSetApproval createSubmitApproval(
      final PatchSet.Id patchSetId, final IdentifiedUser user, final ReviewDb db
      ) throws OrmException {
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
      final AbandonedSender.Factory senderFactory,
      final ChangeHookRunner hooks) throws NoSuchChangeException,
      InvalidChangeOperationException, EmailException, OrmException {
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

    final Change updatedChange = db.changes().atomicUpdate(changeId,
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

    updatedChange(db, user, updatedChange, cmsg, senderFactory,
        "Change is no longer open or patchset is not latest");

    hooks.doChangeAbandonedHook(updatedChange, user.getAccount(), message);
  }

  public static Change.Id revert(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final RevertedSender.Factory revertedSenderFactory,
      final ChangeHookRunner hooks, GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ReplicationQueue replication, PersonIdent myIdent)
      throws NoSuchChangeException, EmailException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      PatchSetInfoNotAvailableException {

    final Change.Id changeId = patchSetId.getParentKey();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final Repository git;
    try {
      git = gitManager.openRepository(db.changes().get(changeId).getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    };

    final RevWalk revWalk = new RevWalk(git);
    try {
      RevCommit commitToRevert =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      PersonIdent authorIdent =
          user.newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());

      RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
      revWalk.parseHeaders(parentToCommitToRevert);

      CommitBuilder revertCommit = new CommitBuilder();
      revertCommit.addParentId(commitToRevert);
      revertCommit.setTreeId(parentToCommitToRevert.getTree());
      revertCommit.setAuthor(authorIdent);
      revertCommit.setCommitter(myIdent);
      revertCommit.setMessage(message);

      final ObjectInserter oi = git.newObjectInserter();;
      ObjectId id;
      try {
        id = oi.insert(revertCommit);
        oi.flush();
      } finally {
        oi.release();
      }

      Change.Key changeKey = new Change.Key("I" + id.name());
      final Change change =
          new Change(changeKey, new Change.Id(db.nextChangeId()),
              user.getAccountId(), db.changes().get(changeId).getDest());
      change.nextPatchSetId();

      final PatchSet ps = new PatchSet(change.currPatchSetId());
      ps.setCreatedOn(change.getCreatedOn());
      ps.setUploader(user.getAccountId());
      ps.setRevision(new RevId(id.getName()));

      db.patchSets().insert(Collections.singleton(ps));

      final PatchSetInfo info =
          patchSetInfoFactory.get(revWalk.parseCommit(id), ps.getId());
      change.setCurrentPatchSet(info);
      ChangeUtil.updated(change);
      db.changes().insert(Collections.singleton(change));

      final RefUpdate ru = git.updateRef(ps.getRefName());
      ru.setNewObjectId(id);
      ru.disableRefLog();
      if (ru.update(revWalk) != RefUpdate.Result.NEW) {
        throw new IOException("Failed to create ref " + ps.getRefName()
            + " in " + git.getDirectory() + ": " + ru.getResult());
      }
      replication.scheduleUpdate(db.changes().get(changeId).getProject(),
          ru.getName());

      final ChangeMessage cmsg =
          new ChangeMessage(new ChangeMessage.Key(changeId,
              ChangeUtil.messageUUID(db)), user.getAccountId());
      final StringBuilder msgBuf =
          new StringBuilder("Patch Set " + patchSetId.get() + ": Reverted");
      msgBuf.append("\n\n");
      msgBuf.append("This patchset was reverted in change: " + changeKey.get());

      cmsg.setMessage(msgBuf.toString());
      db.changeMessages().insert(Collections.singleton(cmsg));

      final RevertedSender cm = revertedSenderFactory.create(change);
      cm.setFrom(user.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();

      hooks.doPatchsetCreatedHook(change, ps);

      return change.getId();
    } finally {
      revWalk.release();
      git.close();
    }
  }

  public static void restore(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final RestoredSender.Factory senderFactory,
      final ChangeHookRunner hooks) throws NoSuchChangeException,
      InvalidChangeOperationException, EmailException, OrmException {
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

    final Change updatedChange = db.changes().atomicUpdate(changeId,
        new AtomicUpdate<Change>() {
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

    updatedChange(db, user, updatedChange, cmsg, senderFactory,
       "Change is not abandoned or patchset is not latest");

    hooks.doChangeRestoreHook(updatedChange, user.getAccount(), message);
  }

  private static void updatedChange(final ReviewDb db, final IdentifiedUser user,
      final Change change, final ChangeMessage cmsg,
      ReplyToChangeSender.Factory senderFactory, final String err)
      throws NoSuchChangeException, InvalidChangeOperationException,
      EmailException, OrmException {
    if (change == null) {
      throw new InvalidChangeOperationException(err);
    }
    db.changeMessages().insert(Collections.singleton(cmsg));

    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);

    // Email the reviewers
    final ReplyToChangeSender cm = senderFactory.create(change);
    cm.setFrom(user.getAccountId());
    cm.setChangeMessage(cmsg);
    cm.send();
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
