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
import com.google.common.primitives.Ints;
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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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

  private final Provider<CurrentUser> user;
  private final Provider<ReviewDb> db;
  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final GitRepositoryManager gitManager;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeUpdate.Factory changeUpdateFactory;

  @Inject
  ChangeUtil(Provider<CurrentUser> user,
      Provider<ReviewDb> db,
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      ChangeControl.GenericFactory changeControlFactory,
      RevertedSender.Factory revertedSenderFactory,
      ChangeInserter.Factory changeInserterFactory,
      GitRepositoryManager gitManager,
      BatchUpdate.Factory updateFactory,
      ChangeMessagesUtil changeMessagesUtil,
      ChangeUpdate.Factory changeUpdateFactory) {
    this.user = user;
    this.db = db;
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.changeControlFactory = changeControlFactory;
    this.revertedSenderFactory = revertedSenderFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.gitManager = gitManager;
    this.updateFactory = updateFactory;
    this.changeMessagesUtil = changeMessagesUtil;
    this.changeUpdateFactory = changeUpdateFactory;
  }

  public Change.Id revert(ChangeControl ctl, PatchSet.Id patchSetId,
      String message, PersonIdent myIdent)
      throws NoSuchChangeException, OrmException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      RestApiException, UpdateException {
    Change.Id changeIdToRevert = patchSetId.getParentKey();
    PatchSet patch = db.get().patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeIdToRevert);
    }
    Change changeToRevert = db.get().changes().get(changeIdToRevert);

    Project.NameKey project = ctl.getChange().getProject();
    try (Repository git = gitManager.openRepository(project);
        RevWalk revWalk = new RevWalk(git)) {
      RevCommit commitToRevert =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));

      PersonIdent authorIdent = user.get().asIdentifiedUser()
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
      Change.Id changeId = new Change.Id(seq.nextChangeId());
      try (ObjectInserter oi = git.newObjectInserter()) {
        ObjectId id = oi.insert(revertCommitBuilder);
        oi.flush();
        revertCommit = revWalk.parseCommit(id);

        RefControl refControl = ctl.getRefControl();
        ins = changeInserterFactory.create(
              refControl, changeId, revertCommit)
            .setValidatePolicy(CommitValidators.Policy.GERRIT)
            .setTopic(changeToRevert.getTopic());

        ChangeMessage changeMessage = new ChangeMessage(
            new ChangeMessage.Key(
                patchSetId.getParentKey(), ChangeUtil.messageUUID(db.get())),
                user.get().getAccountId(), TimeUtil.nowTs(), patchSetId);
        StringBuilder msgBuf = new StringBuilder();
        msgBuf.append("Patch Set ").append(patchSetId.get()).append(": Reverted");
        msgBuf.append("\n\n");
        msgBuf.append("This patchset was reverted in change: ")
              .append("I").append(computedChangeId.name());
        changeMessage.setMessage(msgBuf.toString());
        ChangeUpdate update = changeUpdateFactory.create(ctl, TimeUtil.nowTs());
        changeMessagesUtil.addChangeMessage(db.get(), update, changeMessage);
        update.commit();

        ins.setMessage("Uploaded patch set 1.");
        try (BatchUpdate bu = updateFactory.create(
            db.get(), project, refControl.getUser(),
            TimeUtil.nowTs())) {
          bu.setRepository(git, revWalk, oi);
          bu.insertChange(ins);
          bu.execute();
        }
      }

      try {
        RevertedSender cm = revertedSenderFactory.create(changeId);
        cm.setFrom(user.get().getAccountId());
        cm.setChangeMessage(ins.getChangeMessage());
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for revert change " + changeId, err);
      }

      return changeId;
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeIdToRevert, e);
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
    if (!id.isEmpty() && id.charAt(0) != '0') {
      Integer n = Ints.tryParse(id);
      try {
        if (n != null) {
          return ImmutableList.of(
              changeControlFactory.controlFor(new Change.Id(n), user));
        }
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

  public static PatchSet.Id nextPatchSetId(PatchSet.Id id) {
    return new PatchSet.Id(id.getParentKey(), id.get() + 1);
  }
}
