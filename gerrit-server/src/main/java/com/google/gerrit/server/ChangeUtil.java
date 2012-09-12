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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.TrackingFooter;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmConcurrencyException;
import com.google.gwtorm.server.OrmException;

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
import org.eclipse.jgit.util.ChangeIdUtil;
import org.eclipse.jgit.util.NB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

public class ChangeUtil {

  private static final Logger log = LoggerFactory.getLogger(ChangeUtil.class);

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

  public static void insertAncestors(ReviewDb db, PatchSet.Id id, RevCommit src)
      throws OrmException {
    final int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<PatchSetAncestor>(cnt);
    for (int p = 0; p < cnt; p++) {
      PatchSetAncestor a =
          new PatchSetAncestor(new PatchSetAncestor.Id(id, p + 1));
      a.setAncestorRevision(new RevId(src.getParent(p).getId().getName()));
      toInsert.add(a);
    }
    db.patchSetAncestors().insert(toInsert);
  }

  public static Change.Id revert(final PatchSet.Id patchSetId,
      final IdentifiedUser user, final String message, final ReviewDb db,
      final RevertedSender.Factory revertedSenderFactory,
      final ChangeHooks hooks, GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated replication, PersonIdent myIdent)
      throws NoSuchChangeException, EmailException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException {
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
    }

    final RevWalk revWalk = new RevWalk(git);
    try {
      RevCommit commitToRevert =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      PersonIdent authorIdent =
          user.newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());

      RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
      revWalk.parseHeaders(parentToCommitToRevert);

      CommitBuilder revertCommitBuilder = new CommitBuilder();
      revertCommitBuilder.addParentId(commitToRevert);
      revertCommitBuilder.setTreeId(parentToCommitToRevert.getTree());
      revertCommitBuilder.setAuthor(authorIdent);
      revertCommitBuilder.setCommitter(myIdent);

      final ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(parentToCommitToRevert.getTree(),
              commitToRevert, authorIdent, myIdent, message);
      revertCommitBuilder.setMessage(ChangeIdUtil.insertId(message, computedChangeId, true));

      RevCommit revertCommit;
      final ObjectInserter oi = git.newObjectInserter();
      try {
        ObjectId id = oi.insert(revertCommitBuilder);
        oi.flush();
        revertCommit = revWalk.parseCommit(id);
      } finally {
        oi.release();
      }

      final Change change = new Change(
          new Change.Key("I" + computedChangeId.name()),
          new Change.Id(db.nextChangeId()),
          user.getAccountId(),
          db.changes().get(changeId).getDest());
      change.nextPatchSetId();

      final PatchSet ps = new PatchSet(change.currPatchSetId());
      ps.setCreatedOn(change.getCreatedOn());
      ps.setUploader(change.getOwner());
      ps.setRevision(new RevId(revertCommit.name()));

      change.setCurrentPatchSet(patchSetInfoFactory.get(revertCommit, ps.getId()));
      ChangeUtil.updated(change);

      final RefUpdate ru = git.updateRef(ps.getRefName());
      ru.setExpectedOldObjectId(ObjectId.zeroId());
      ru.setNewObjectId(revertCommit);
      ru.disableRefLog();
      if (ru.update(revWalk) != RefUpdate.Result.NEW) {
        throw new IOException(String.format(
            "Failed to create ref %s in %s: %s", ps.getRefName(),
            change.getDest().getParentKey().get(), ru.getResult()));
      }
      replication.fire(change.getProject(), ru.getName());

      db.changes().beginTransaction(change.getId());
      try {
        insertAncestors(db, ps.getId(), revertCommit);
        db.patchSets().insert(Collections.singleton(ps));
        db.changes().insert(Collections.singleton(change));
        db.commit();
      } finally {
        db.rollback();
      }

      final ChangeMessage cmsg =
          new ChangeMessage(new ChangeMessage.Key(changeId,
              ChangeUtil.messageUUID(db)), user.getAccountId(), patchSetId);
      final StringBuilder msgBuf =
          new StringBuilder("Patch Set " + patchSetId.get() + ": Reverted");
      msgBuf.append("\n\n");
      msgBuf.append("This patchset was reverted in change: " + change.getKey().get());

      cmsg.setMessage(msgBuf.toString());
      db.changeMessages().insert(Collections.singleton(cmsg));

      final RevertedSender cm = revertedSenderFactory.create(change);
      cm.setFrom(user.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();

      hooks.doPatchsetCreatedHook(change, ps, db);

      return change.getId();
    } finally {
      revWalk.release();
      git.close();
    }
  }

  public static void deleteDraftChange(final PatchSet.Id patchSetId,
      GitRepositoryManager gitManager,
      final GitReferenceUpdated replication, final ReviewDb db)
      throws NoSuchChangeException, OrmException, IOException {
    final Change.Id changeId = patchSetId.getParentKey();
    final Change change = db.changes().get(changeId);
    if (change == null || change.getStatus() != Change.Status.DRAFT) {
      throw new NoSuchChangeException(changeId);
    }

    for (PatchSet ps : db.patchSets().byChange(changeId)) {
      // These should all be draft patch sets.
      deleteOnlyDraftPatchSet(ps, change, gitManager, replication, db);
    }

    db.changeMessages().delete(db.changeMessages().byChange(changeId));
    db.starredChanges().delete(db.starredChanges().byChange(changeId));
    db.trackingIds().delete(db.trackingIds().byChange(changeId));
    db.changes().delete(Collections.singleton(change));
  }

  public static void deleteOnlyDraftPatchSet(final PatchSet patch,
      final Change change, GitRepositoryManager gitManager,
      final GitReferenceUpdated replication, final ReviewDb db)
      throws NoSuchChangeException, OrmException, IOException {
    final PatchSet.Id patchSetId = patch.getId();
    if (patch == null || !patch.isDraft()) {
      throw new NoSuchChangeException(patchSetId.getParentKey());
    }

    Repository repo = gitManager.openRepository(change.getProject());
    try {
      RefUpdate update = repo.updateRef(patch.getRefName());
      update.setForceUpdate(true);
      update.disableRefLog();
      switch (update.delete()) {
        case NEW:
        case FAST_FORWARD:
        case FORCED:
        case NO_CHANGE:
          // Successful deletion.
          break;
        default:
          throw new IOException("Failed to delete ref " + patch.getRefName() +
              " in " + repo.getDirectory() + ": " + update.getResult());
      }
      replication.fire(change.getProject(), update.getName());
    } finally {
      repo.close();
    }

    db.accountPatchReviews().delete(db.accountPatchReviews().byPatchSet(patchSetId));
    db.changeMessages().delete(db.changeMessages().byPatchSet(patchSetId));
    db.patchComments().delete(db.patchComments().byPatchSet(patchSetId));
    db.patchSetApprovals().delete(db.patchSetApprovals().byPatchSet(patchSetId));
    db.patchSetAncestors().delete(db.patchSetAncestors().byPatchSet(patchSetId));

    db.patchSets().delete(Collections.singleton(patch));
  }

  public static <T extends ReplyToChangeSender> void updatedChange(
      final ReviewDb db, final IdentifiedUser user, final Change change,
      final ChangeMessage cmsg, ReplyToChangeSender.Factory<T> senderFactory)
      throws OrmException {
    db.changeMessages().insert(Collections.singleton(cmsg));

    new ApprovalsUtil(db, null).syncChangeStatus(change);

    // Email the reviewers
    try {
      final ReplyToChangeSender cm = senderFactory.create(change);
      cm.setFrom(user.getAccountId());
      cm.setChangeMessage(cmsg);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot email update for change " + change.getChangeId(), e);
    }
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
