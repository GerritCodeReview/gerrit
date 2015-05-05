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

import static com.google.gerrit.server.change.PatchSetInserter.ValidatePolicy.RECEIVE_COMMITS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmConcurrencyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ChangeUtil {
  /**
   * Epoch for sort key calculations, Tue Sep 30 2008 17:00:00.
   * <p>
   * We overrun approximately 4,083 years later, so ~6092.
   */
  @VisibleForTesting
  private static final long SORT_KEY_EPOCH_MINS =
      MINUTES.convert(1222819200L, SECONDS);

  private static final Object uuidLock = new Object();
  private static final int SEED = 0x2418e6f9;
  private static int uuidPrefix;
  private static int uuidSeq;

  private static final Logger log =
      LoggerFactory.getLogger(ChangeUtil.class);

  /**
   * Generate a new unique identifier for change message entities.
   *
   * @param db the database connection, used to increment the change message
   *        allocation sequence.
   * @return the new unique identifier.
   * @throws OrmException the database couldn't be incremented.
   */
  public static String messageUUID(ReviewDb db) throws OrmException {
    int p, s;
    synchronized (uuidLock) {
      if (uuidSeq == 0) {
        uuidPrefix = db.nextChangeMessageId();
        uuidSeq = Integer.MAX_VALUE;
      }
      p = uuidPrefix;
      s = uuidSeq--;
    }
    String u = IdGenerator.format(IdGenerator.mix(SEED, p));
    String l = IdGenerator.format(IdGenerator.mix(p, s));
    return u + '_' + l;
  }

  public static void touch(Change change, ReviewDb db)
      throws OrmException {
    try {
      updated(change);
      db.changes().update(Collections.singleton(change));
    } catch (OrmConcurrencyException e) {
      // Ignore a concurrent update, we just wanted to tag it as newer.
    }
  }

  public static void bumpRowVersionNotLastUpdatedOn(Change.Id id, ReviewDb db)
      throws OrmException {
    // Empty update of Change to bump rowVersion, changing its ETag.
    Change c = db.changes().get(id);
    if (c != null) {
      db.changes().update(Collections.singleton(c));
    }
  }

  public static void updated(Change c) {
    c.setLastUpdatedOn(TimeUtil.nowTs());
    computeSortKey(c);
  }

  public static void insertAncestors(ReviewDb db, PatchSet.Id id, RevCommit src)
      throws OrmException {
    int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<PatchSetAncestor>(cnt);
    for (int p = 0; p < cnt; p++) {
      PatchSetAncestor a =
          new PatchSetAncestor(new PatchSetAncestor.Id(id, p + 1));
      a.setAncestorRevision(new RevId(src.getParent(p).getId().getName()));
      toInsert.add(a);
    }
    db.patchSetAncestors().insert(toInsert);
  }

  public static String sortKey(long lastUpdatedMs, int id) {
    long lastUpdatedMins = MINUTES.convert(lastUpdatedMs, MILLISECONDS);
    long minsSinceEpoch = lastUpdatedMins - SORT_KEY_EPOCH_MINS;
    StringBuilder r = new StringBuilder(16);
    r.setLength(16);
    formatHexInt(r, 0, Ints.checkedCast(minsSinceEpoch));
    formatHexInt(r, 8, id);
    return r.toString();
  }

  public static long parseSortKey(String sortKey) {
    if ("z".equals(sortKey)) {
      return Long.MAX_VALUE;
    }
    return Long.parseLong(sortKey, 16);
  }

  public static void computeSortKey(Change c) {
    long lastUpdatedMs = c.getLastUpdatedOn().getTime();
    int id = c.getId().get();
    c.setSortKey(sortKey(lastUpdatedMs, id));
  }

  public static PatchSet.Id nextPatchSetId(Map<String, Ref> allRefs,
      PatchSet.Id id) {
    PatchSet.Id next = nextPatchSetId(id);
    while (allRefs.containsKey(next.toRefName())) {
      next = nextPatchSetId(next);
    }
    return next;
  }

  public static PatchSet.Id nextPatchSetId(Repository git, PatchSet.Id id)
      throws IOException {
    return nextPatchSetId(git.getRefDatabase().getRefs(RefDatabase.ALL), id);
  }

  private final Provider<CurrentUser> userProvider;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final Provider<ReviewDb> db;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final GitRepositoryManager gitManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final ChangeIndexer indexer;

  @Inject
  ChangeUtil(Provider<CurrentUser> userProvider,
      CommitValidators.Factory commitValidatorsFactory,
      Provider<ReviewDb> db,
      RevertedSender.Factory revertedSenderFactory,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      GitRepositoryManager gitManager,
      GitReferenceUpdated gitRefUpdated,
      ChangeIndexer indexer) {
    this.userProvider = userProvider;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.db = db;
    this.revertedSenderFactory = revertedSenderFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.gitManager = gitManager;
    this.gitRefUpdated = gitRefUpdated;
    this.indexer = indexer;
  }

  public Change.Id revert(ChangeControl ctl, PatchSet.Id patchSetId,
      String message, PersonIdent myIdent, SshInfo sshInfo)
      throws NoSuchChangeException, EmailException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException {
    Change.Id changeId = patchSetId.getParentKey();
    PatchSet patch = db.get().patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }
    Change changeToRevert = db.get().changes().get(changeId);

    Repository git;
    try {
      git = gitManager.openRepository(ctl.getChange().getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      RevWalk revWalk = new RevWalk(git);
      try {
        RevCommit commitToRevert =
            revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

        PersonIdent authorIdent =
            user().newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());

        RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
        revWalk.parseHeaders(parentToCommitToRevert);

        CommitBuilder revertCommitBuilder = new CommitBuilder();
        revertCommitBuilder.addParentId(commitToRevert);
        revertCommitBuilder.setTreeId(parentToCommitToRevert.getTree());
        revertCommitBuilder.setAuthor(authorIdent);
        revertCommitBuilder.setCommitter(authorIdent);

        if (message == null) {
          message = MessageFormat.format(
              ChangeMessages.get().revertChangeDefaultMessage,
              changeToRevert.getSubject(), patch.getRevision().get());
        }

        ObjectId computedChangeId =
            ChangeIdUtil.computeChangeId(parentToCommitToRevert.getTree(),
                commitToRevert, authorIdent, myIdent, message);
        revertCommitBuilder.setMessage(
            ChangeIdUtil.insertId(message, computedChangeId, true));

        RevCommit revertCommit;
        ObjectInserter oi = git.newObjectInserter();
        try {
          ObjectId id = oi.insert(revertCommitBuilder);
          oi.flush();
          revertCommit = revWalk.parseCommit(id);
        } finally {
          oi.close();
        }

        RefControl refControl = ctl.getRefControl();
        Change change = new Change(
            new Change.Key("I" + computedChangeId.name()),
            new Change.Id(db.get().nextChangeId()),
            user().getAccountId(),
            changeToRevert.getDest(),
            TimeUtil.nowTs());
        change.setTopic(changeToRevert.getTopic());
        ChangeInserter ins =
            changeInserterFactory.create(refControl, change, revertCommit);
        PatchSet ps = ins.getPatchSet();

        String ref = refControl.getRefName();
        String cmdRef = MagicBranch.NEW_PUBLISH_CHANGE
            + ref.substring(ref.lastIndexOf('/') + 1);
        CommitReceivedEvent commitReceivedEvent = new CommitReceivedEvent(
            new ReceiveCommand(ObjectId.zeroId(), revertCommit.getId(), cmdRef),
            refControl.getProjectControl().getProject(),
            refControl.getRefName(), revertCommit, user());

        try {
          commitValidatorsFactory.create(refControl, sshInfo, git)
              .validateForGerritCommits(commitReceivedEvent);
        } catch (CommitValidationException e) {
          throw new InvalidChangeOperationException(e.getMessage());
        }

        RefUpdate ru = git.updateRef(ps.getRefName());
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setNewObjectId(revertCommit);
        ru.disableRefLog();
        if (ru.update(revWalk) != RefUpdate.Result.NEW) {
          throw new IOException(String.format(
              "Failed to create ref %s in %s: %s", ps.getRefName(),
              change.getDest().getParentKey().get(), ru.getResult()));
        }

        ChangeMessage cmsg = new ChangeMessage(
            new ChangeMessage.Key(changeId, messageUUID(db.get())),
            user().getAccountId(), TimeUtil.nowTs(), patchSetId);
        StringBuilder msgBuf = new StringBuilder();
        msgBuf.append("Patch Set ").append(patchSetId.get()).append(": Reverted");
        msgBuf.append("\n\n");
        msgBuf.append("This patchset was reverted in change: ")
              .append(change.getKey().get());
        cmsg.setMessage(msgBuf.toString());

        ins.setMessage(cmsg).insert();

        try {
          RevertedSender cm = revertedSenderFactory.create(change);
          cm.setFrom(user().getAccountId());
          cm.setChangeMessage(cmsg);
          cm.send();
        } catch (Exception err) {
          log.error("Cannot send email for revert change " + change.getId(),
              err);
        }

        return change.getId();
      } finally {
        revWalk.close();
      }
    } finally {
      git.close();
    }
  }

  public Change.Id editCommitMessage(ChangeControl ctl, PatchSet.Id patchSetId,
      String message, PersonIdent myIdent)
      throws NoSuchChangeException, EmailException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, PatchSetInfoNotAvailableException {
    Change.Id changeId = patchSetId.getParentKey();
    PatchSet originalPS = db.get().patchSets().get(patchSetId);
    if (originalPS == null) {
      throw new NoSuchChangeException(changeId);
    }

    if (message == null || message.length() == 0) {
      throw new InvalidChangeOperationException(
          "The commit message cannot be empty");
    }

    Repository git;
    try {
      git = gitManager.openRepository(ctl.getChange().getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      RevWalk revWalk = new RevWalk(git);
      try {
        RevCommit commit =
            revWalk.parseCommit(ObjectId.fromString(originalPS.getRevision()
                .get()));
        if (commit.getFullMessage().equals(message)) {
          throw new InvalidChangeOperationException(
              "New commit message cannot be same as existing commit message");
        }

        Date now = myIdent.getWhen();
        Change change = db.get().changes().get(changeId);
        PersonIdent authorIdent =
            user().newCommitterIdent(now, myIdent.getTimeZone());

        CommitBuilder commitBuilder = new CommitBuilder();
        commitBuilder.setTreeId(commit.getTree());
        commitBuilder.setParentIds(commit.getParents());
        commitBuilder.setAuthor(commit.getAuthorIdent());
        commitBuilder.setCommitter(authorIdent);
        commitBuilder.setMessage(message);

        RevCommit newCommit;
        ObjectInserter oi = git.newObjectInserter();
        try {
          ObjectId id = oi.insert(commitBuilder);
          oi.flush();
          newCommit = revWalk.parseCommit(id);
        } finally {
          oi.close();
        }

        PatchSet.Id id = nextPatchSetId(git, change.currentPatchSetId());
        PatchSet newPatchSet = new PatchSet(id);
        newPatchSet.setCreatedOn(new Timestamp(now.getTime()));
        newPatchSet.setUploader(user().getAccountId());
        newPatchSet.setRevision(new RevId(newCommit.name()));

        String msg = "Patch Set " + newPatchSet.getPatchSetId()
            + ": Commit message was updated";

        change = patchSetInserterFactory
            .create(git, revWalk, ctl, newCommit)
            .setPatchSet(newPatchSet)
            .setMessage(msg)
            .setCopyLabels(true)
            .setValidatePolicy(RECEIVE_COMMITS)
            .setDraft(originalPS.isDraft())
            .insert();

        return change.getId();
      } finally {
        revWalk.close();
      }
    } finally {
      git.close();
    }
  }

  public void deleteDraftChange(PatchSet.Id patchSetId)
      throws NoSuchChangeException, OrmException, IOException {
    deleteDraftChange(patchSetId.getParentKey());
  }

  public void deleteDraftChange(Change.Id changeId)
      throws NoSuchChangeException, OrmException, IOException {
    ReviewDb db = this.db.get();
    Change change = db.changes().get(changeId);
    if (change == null || change.getStatus() != Change.Status.DRAFT) {
      throw new NoSuchChangeException(changeId);
    }

    for (PatchSet ps : db.patchSets().byChange(changeId)) {
      // These should all be draft patch sets.
      deleteOnlyDraftPatchSet(ps, change);
    }

    db.changeMessages().delete(db.changeMessages().byChange(changeId));
    db.starredChanges().delete(db.starredChanges().byChange(changeId));
    db.changes().delete(Collections.singleton(change));
    indexer.delete(db, change);
  }

  public void deleteOnlyDraftPatchSet(PatchSet patch, Change change)
      throws NoSuchChangeException, OrmException, IOException {
    PatchSet.Id patchSetId = patch.getId();
    if (!patch.isDraft()) {
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
      gitRefUpdated.fire(change.getProject(), update);
    } finally {
      repo.close();
    }

    ReviewDb db = this.db.get();
    db.accountPatchReviews().delete(db.accountPatchReviews().byPatchSet(patchSetId));
    db.changeMessages().delete(db.changeMessages().byPatchSet(patchSetId));
    db.patchComments().delete(db.patchComments().byPatchSet(patchSetId));
    // No need to delete from notedb; draft patch sets will be filtered out.
    db.patchSetApprovals().delete(db.patchSetApprovals().byPatchSet(patchSetId));
    db.patchSetAncestors().delete(db.patchSetAncestors().byPatchSet(patchSetId));

    db.patchSets().delete(Collections.singleton(patch));
  }

  private IdentifiedUser user() {
    return (IdentifiedUser) userProvider.get();
  }

  private static PatchSet.Id nextPatchSetId(PatchSet.Id id) {
    return new PatchSet.Id(id.getParentKey(), id.get() + 1);
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
