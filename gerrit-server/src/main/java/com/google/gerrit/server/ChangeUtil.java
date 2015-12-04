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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gwtorm.server.OrmConcurrencyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
public class ChangeUtil {
  private static final Object uuidLock = new Object();
  private static final int SEED = 0x2418e6f9;
  private static int uuidPrefix;
  private static int uuidSeq;

  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";
  private static final int SUBJECT_CROP_RANGE = 10;

  private static final Logger log =
      LoggerFactory.getLogger(ChangeUtil.class);

  public static final Function<PatchSet, Integer> TO_PS_ID =
      new Function<PatchSet, Integer>() {
        @Override
        public Integer apply(PatchSet in) {
          return in.getId().get();
        }
      };

  public static final Ordering<PatchSet> PS_ID_ORDER = Ordering.natural()
    .onResultOf(TO_PS_ID);

  /**
   * Generate a new unique identifier for change message entities.
   *
   * @param db the database connection, used to increment the change message
   *        allocation sequence.
   * @return the new unique identifier.
   * @throws OrmException the database couldn't be incremented.
   */
  public static String messageUUID(ReviewDb db) throws OrmException {
    int p;
    int s;
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

  public static String cropSubject(String subject) {
    if (subject.length() > SUBJECT_MAX_LENGTH) {
      int maxLength = SUBJECT_MAX_LENGTH - SUBJECT_CROP_APPENDIX.length();
      for (int cropPosition = maxLength;
          cropPosition > maxLength - SUBJECT_CROP_RANGE; cropPosition--) {
        if (Character.isWhitespace(subject.charAt(cropPosition - 1))) {
          return subject.substring(0, cropPosition) + SUBJECT_CROP_APPENDIX;
        }
      }
      return subject.substring(0, maxLength) + SUBJECT_CROP_APPENDIX;
    }
    return subject;
  }

  private final Provider<IdentifiedUser> user;
  private final Provider<ReviewDb> db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final GitRepositoryManager gitManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final StarredChangesUtil starredChangesUtil;

  @Inject
  ChangeUtil(Provider<IdentifiedUser> user,
      Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      ChangeControl.GenericFactory changeControlFactory,
      RevertedSender.Factory revertedSenderFactory,
      ChangeInserter.Factory changeInserterFactory,
      GitRepositoryManager gitManager,
      GitReferenceUpdated gitRefUpdated,
      BatchUpdate.Factory updateFactory,
      ChangeMessagesUtil changeMessagesUtil,
      ChangeUpdate.Factory changeUpdateFactory,
      StarredChangesUtil starredChangesUtil) {
    this.user = user;
    this.db = db;
    this.queryProvider = queryProvider;
    this.changeControlFactory = changeControlFactory;
    this.revertedSenderFactory = revertedSenderFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.gitManager = gitManager;
    this.gitRefUpdated = gitRefUpdated;
    this.updateFactory = updateFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.changeUpdateFactory = changeUpdateFactory;
    this.starredChangesUtil = starredChangesUtil;
  }

  public Change.Id revert(ChangeControl ctl, PatchSet.Id patchSetId,
      String message, PersonIdent myIdent)
      throws NoSuchChangeException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      RestApiException, UpdateException {
    Change.Id changeId = patchSetId.getParentKey();
    PatchSet patch = db.get().patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }
    Change changeToRevert = db.get().changes().get(changeId);

    Project.NameKey project = ctl.getChange().getProject();
    try (Repository git = gitManager.openRepository(project);
        RevWalk revWalk = new RevWalk(git)) {
      RevCommit commitToRevert =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      PersonIdent authorIdent = user.get()
          .newCommitterIdent(myIdent.getWhen(), myIdent.getTimeZone());

      if (commitToRevert.getParentCount() == 0) {
        throw new ResourceConflictException("Cannot revert initial commit");
      }

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
      ChangeInserter ins;
      try (ObjectInserter oi = git.newObjectInserter()) {
        ObjectId id = oi.insert(revertCommitBuilder);
        oi.flush();
        revertCommit = revWalk.parseCommit(id);

        RefControl refControl = ctl.getRefControl();
        Change change = new Change(
            new Change.Key("I" + computedChangeId.name()),
            new Change.Id(db.get().nextChangeId()),
            user.get().getAccountId(),
            changeToRevert.getDest(),
            TimeUtil.nowTs());
        change.setTopic(changeToRevert.getTopic());
        ins = changeInserterFactory.create(
              refControl, change, revertCommit)
            .setValidatePolicy(CommitValidators.Policy.GERRIT);

        ChangeMessage changeMessage = new ChangeMessage(
            new ChangeMessage.Key(
                patchSetId.getParentKey(), ChangeUtil.messageUUID(db.get())),
                user.get().getAccountId(), TimeUtil.nowTs(), patchSetId);
        StringBuilder msgBuf = new StringBuilder();
        msgBuf.append("Patch Set ").append(patchSetId.get()).append(": Reverted");
        msgBuf.append("\n\n");
        msgBuf.append("This patchset was reverted in change: ")
              .append(change.getKey().get());
        changeMessage.setMessage(msgBuf.toString());
        ChangeUpdate update = changeUpdateFactory.create(ctl, TimeUtil.nowTs());
        changeMessagesUtil.addChangeMessage(db.get(), update, changeMessage);
        update.commit();

        ins.setMessage("Uploaded patch set 1.");
        try (BatchUpdate bu = updateFactory.create(
            db.get(), change.getProject(), refControl.getUser(),
            change.getCreatedOn())) {
          bu.setRepository(git, revWalk, oi);
          bu.insertChange(ins);
          bu.execute();
        }
      }

      Change.Id id = ins.getChange().getId();
      try {
        RevertedSender cm = revertedSenderFactory.create(id);
        cm.setFrom(user.get().getAccountId());
        cm.setChangeMessage(ins.getChangeMessage());
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for revert change " + id, err);
      }

      return id;
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
  }

  public String getMessage(Change change)
      throws NoSuchChangeException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException {
    Change.Id changeId = change.getId();
    PatchSet ps = db.get().patchSets().get(change.currentPatchSetId());
    if (ps == null) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = gitManager.openRepository(change.getProject());
        RevWalk revWalk = new RevWalk(git)) {
      RevCommit commit = revWalk.parseCommit(
          ObjectId.fromString(ps.getRevision().get()));
      return commit.getFullMessage();
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
  }

  public void deleteDraftChange(Change change)
      throws NoSuchChangeException, OrmException, IOException {
    ReviewDb db = this.db.get();
    Change.Id changeId = change.getId();
    if (change.getStatus() != Change.Status.DRAFT) {
      // TODO(dborowitz): ResourceConflictException.
      throw new NoSuchChangeException(changeId);
    }
    List<PatchSet> patchSets = db.patchSets().byChange(changeId).toList();
    for (PatchSet ps : patchSets) {
      if (!ps.isDraft()) {
        // TODO(dborowitz): ResourceConflictException.
        throw new NoSuchChangeException(changeId);
      }
      db.accountPatchReviews().delete(
          db.accountPatchReviews().byPatchSet(ps.getId()));
    }

    // No need to delete from notedb; draft patch sets will be filtered out.
    db.patchComments().delete(db.patchComments().byChange(changeId));

    db.patchSetApprovals().delete(db.patchSetApprovals().byChange(changeId));
    db.patchSets().delete(patchSets);
    db.changeMessages().delete(db.changeMessages().byChange(changeId));
    starredChangesUtil.unstarAll(changeId);
    db.changes().delete(Collections.singleton(change));

    // Delete all refs at once.
    try (Repository repo = gitManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repo)) {
      String prefix = new PatchSet.Id(changeId, 1).toRefName();
      prefix = prefix.substring(0, prefix.length() - 1);
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      for (Ref ref : repo.getRefDatabase().getRefs(prefix).values()) {
        ru.addCommand(
            new ReceiveCommand(
              ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
      }
      ru.execute(rw, NullProgressMonitor.INSTANCE);
      for (ReceiveCommand cmd : ru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException("failed: " + cmd + ": " + cmd.getResult());
        }
      }
    }
  }

  public void deleteOnlyDraftPatchSet(PatchSet patch, Change change)
      throws NoSuchChangeException, OrmException, IOException {
    PatchSet.Id patchSetId = patch.getId();
    if (!patch.isDraft()) {
      throw new NoSuchChangeException(patchSetId.getParentKey());
    }

    try (Repository repo = gitManager.openRepository(change.getProject())) {
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
      gitRefUpdated.fire(change.getProject(), update, ReceiveCommand.Type.DELETE);
    }

    deleteOnlyDraftPatchSetPreserveRef(this.db.get(), patch);
  }

  /**
   * Find changes matching the given identifier.
   *
   * @param id change identifier, either a numeric ID, a Change-Id, or
   *     project~branch~id triplet.
   * @param user user to wrap in controls.
   * @return possibly-empty list of controls for all matching changes,
   *     corresponding to the given user; may or may not be visible.
   * @throws OrmException if an error occurred querying the database.
   */
  public List<ChangeControl> findChanges(String id, CurrentUser user)
      throws OrmException {
    // Try legacy id
    if (id.matches("^[1-9][0-9]*$")) {
      try {
        return ImmutableList.of(
            changeControlFactory.controlFor(Change.Id.parse(id), user));
      } catch (NoSuchChangeException e) {
        return Collections.emptyList();
      }
    }

    // Use the index to search for changes, but don't return any stored fields,
    // to force rereading in case the index is stale.
    InternalChangeQuery query = queryProvider.get()
        .setRequestedFields(ImmutableSet.<String> of());

    // Try isolated changeId
    if (!id.contains("~")) {
      return asChangeControls(query.byKeyPrefix(id));
    }

    // Try change triplet
    Optional<ChangeTriplet> triplet = ChangeTriplet.parse(id);
    if (triplet.isPresent()) {
      return asChangeControls(query.byBranchKey(
          triplet.get().branch(),
          triplet.get().id()));
    }

    return Collections.emptyList();
  }

  private List<ChangeControl> asChangeControls(List<ChangeData> cds)
      throws OrmException {
    List<ChangeControl> ctls = new ArrayList<>(cds.size());
    for (ChangeData cd : cds) {
      ctls.add(cd.changeControl(user.get()));
    }
    return ctls;
  }

  private static void deleteOnlyDraftPatchSetPreserveRef(ReviewDb db,
      PatchSet patch) throws NoSuchChangeException, OrmException {
    PatchSet.Id patchSetId = patch.getId();
    if (!patch.isDraft()) {
      throw new NoSuchChangeException(patchSetId.getParentKey());
    }

    db.accountPatchReviews().delete(db.accountPatchReviews().byPatchSet(patchSetId));
    db.changeMessages().delete(db.changeMessages().byPatchSet(patchSetId));
    // No need to delete from notedb; draft patch sets will be filtered out.
    db.patchComments().delete(db.patchComments().byPatchSet(patchSetId));
    db.patchSetApprovals().delete(db.patchSetApprovals().byPatchSet(patchSetId));

    db.patchSets().delete(Collections.singleton(patch));
  }

  public static PatchSet.Id nextPatchSetId(PatchSet.Id id) {
    return new PatchSet.Id(id.getParentKey(), id.get() + 1);
  }
}
