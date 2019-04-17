// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Object to manage a single sequence of updates to NoteDb.
 *
 * <p>Instances are one-time-use. Handles updating both the change repo and the All-Users repo for
 * any affected changes, with proper ordering.
 *
 * <p>To see the state that would be applied prior to executing the full sequence of updates, use
 * {@link #stage()}.
 */
public class NoteDbUpdateManager implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    NoteDbUpdateManager create(Project.NameKey projectName);
  }

  private final Provider<PersonIdent> serverIdent;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final NoteDbMetrics metrics;
  private final Project.NameKey projectName;
  private final ListMultimap<String, ChangeUpdate> changeUpdates;
  private final ListMultimap<String, ChangeDraftUpdate> draftUpdates;
  private final ListMultimap<String, RobotCommentUpdate> robotCommentUpdates;
  private final ListMultimap<String, NoteDbRewriter> rewriters;
  private final Set<Change.Id> toDelete;

  private OpenRepo changeRepo;
  private OpenRepo allUsersRepo;
  private AllUsersAsyncUpdate updateAllUsersAsync;
  private boolean executed;
  private String refLogMessage;
  private PersonIdent refLogIdent;
  private PushCertificate pushCert;

  @Inject
  NoteDbUpdateManager(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      NoteDbMetrics metrics,
      AllUsersAsyncUpdate updateAllUsersAsync,
      @Assisted Project.NameKey projectName) {
    this.serverIdent = serverIdent;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.metrics = metrics;
    this.updateAllUsersAsync = updateAllUsersAsync;
    this.projectName = projectName;
    changeUpdates = MultimapBuilder.hashKeys().arrayListValues().build();
    draftUpdates = MultimapBuilder.hashKeys().arrayListValues().build();
    robotCommentUpdates = MultimapBuilder.hashKeys().arrayListValues().build();
    rewriters = MultimapBuilder.hashKeys().arrayListValues().build();
    toDelete = new HashSet<>();
  }

  @Override
  public void close() {
    if (allUsersRepo != null) {
      OpenRepo r = allUsersRepo;
      allUsersRepo = null;
      closeChecked(r);
    }
    if (changeRepo != null) {
      OpenRepo r = changeRepo;
      changeRepo = null;
      closeChecked(r);
    }
    if (updateAllUsersAsync != null) {
      AllUsersAsyncUpdate r = updateAllUsersAsync;
      updateAllUsersAsync = null;
      r.maybeClose();
    }
  }

  private void closeChecked(OpenRepo repo) {
    try {
      repo.close();
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Unable to close repo");
    }
  }

  public NoteDbUpdateManager setChangeRepo(
      Repository repo, RevWalk rw, @Nullable ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(changeRepo == null, "change repo already initialized");
    changeRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  public NoteDbUpdateManager setRefLogMessage(String message) {
    this.refLogMessage = message;
    return this;
  }

  public NoteDbUpdateManager setRefLogIdent(PersonIdent ident) {
    this.refLogIdent = ident;
    return this;
  }

  /**
   * Set a push certificate for the push that originally triggered this NoteDb update.
   *
   * <p>The pusher will not necessarily have specified any of the NoteDb refs explicitly, such as
   * when processing a push to {@code refs/for/master}. That's fine; this is just passed to the
   * underlying {@link BatchRefUpdate}, and the implementation decides what to do with it.
   *
   * <p>The cert should be associated with the main repo. There is currently no way of associating a
   * push cert with the {@code All-Users} repo, since it is not currently possible to update draft
   * changes via push.
   *
   * @param pushCert push certificate; may be null.
   * @return this
   */
  public NoteDbUpdateManager setPushCertificate(PushCertificate pushCert) {
    this.pushCert = pushCert;
    return this;
  }

  private void initChangeRepo() throws IOException {
    if (changeRepo == null) {
      changeRepo = OpenRepo.open(repoManager, projectName);
    }
  }

  private void initAllUsersRepo() throws IOException {
    if (allUsersRepo == null) {
      allUsersRepo = OpenRepo.open(repoManager, allUsersName);
    }
  }

  private boolean isEmpty() {
    return changeUpdates.isEmpty()
        && draftUpdates.isEmpty()
        && robotCommentUpdates.isEmpty()
        && rewriters.isEmpty()
        && toDelete.isEmpty()
        && !hasCommands(changeRepo)
        && !hasCommands(allUsersRepo)
        && updateAllUsersAsync.isEmpty();
  }

  private static boolean hasCommands(@Nullable OpenRepo or) {
    return or != null && !or.cmds.isEmpty();
  }

  /**
   * Add an update to the list of updates to execute.
   *
   * <p>Updates should only be added to the manager after all mutations have been made, as this
   * method may eagerly access the update.
   *
   * @param update the update to add.
   */
  public void add(ChangeUpdate update) {
    checkNotExecuted();
    checkArgument(
        update.getProjectName().equals(projectName),
        "update for project %s cannot be added to manager for project %s",
        update.getProjectName(),
        projectName);
    checkArgument(
        !rewriters.containsKey(update.getRefName()),
        "cannot update & rewrite ref %s in one BatchUpdate",
        update.getRefName());

    ChangeDraftUpdate du = update.getDraftUpdate();
    if (du != null) {
      draftUpdates.put(du.getRefName(), du);
    }
    RobotCommentUpdate rcu = update.getRobotCommentUpdate();
    if (rcu != null) {
      robotCommentUpdates.put(rcu.getRefName(), rcu);
    }
    DeleteCommentRewriter deleteCommentRewriter = update.getDeleteCommentRewriter();
    if (deleteCommentRewriter != null) {
      // Checks whether there is any ChangeUpdate or rewriter added earlier for the same ref.
      checkArgument(
          !changeUpdates.containsKey(deleteCommentRewriter.getRefName()),
          "cannot update & rewrite ref %s in one BatchUpdate",
          deleteCommentRewriter.getRefName());
      checkArgument(
          !rewriters.containsKey(deleteCommentRewriter.getRefName()),
          "cannot rewrite the same ref %s in one BatchUpdate",
          deleteCommentRewriter.getRefName());
      rewriters.put(deleteCommentRewriter.getRefName(), deleteCommentRewriter);
    }

    DeleteChangeMessageRewriter deleteChangeMessageRewriter =
        update.getDeleteChangeMessageRewriter();
    if (deleteChangeMessageRewriter != null) {
      // Checks whether there is any ChangeUpdate or rewriter added earlier for the same ref.
      checkArgument(
          !changeUpdates.containsKey(deleteChangeMessageRewriter.getRefName()),
          "cannot update & rewrite ref %s in one BatchUpdate",
          deleteChangeMessageRewriter.getRefName());
      checkArgument(
          !rewriters.containsKey(deleteChangeMessageRewriter.getRefName()),
          "cannot rewrite the same ref %s in one BatchUpdate",
          deleteChangeMessageRewriter.getRefName());
      rewriters.put(deleteChangeMessageRewriter.getRefName(), deleteChangeMessageRewriter);
    }

    changeUpdates.put(update.getRefName(), update);
  }

  public void add(ChangeDraftUpdate draftUpdate) {
    checkNotExecuted();
    draftUpdates.put(draftUpdate.getRefName(), draftUpdate);
  }

  public void deleteChange(Change.Id id) {
    checkNotExecuted();
    toDelete.add(id);
  }

  /**
   * Stage updates in the manager's internal list of commands.
   *
   * @throws OrmException if a database layer error occurs.
   * @throws IOException if a storage layer error occurs.
   */
  private void stage() throws OrmException, IOException {
    try (Timer1.Context timer = metrics.stageUpdateLatency.start(CHANGES)) {
      if (isEmpty()) {
        return;
      }

      initChangeRepo();
      if (!draftUpdates.isEmpty() || !toDelete.isEmpty()) {
        initAllUsersRepo();
      }
      addCommands();
    }
  }

  public void flush() throws IOException {
    checkNotExecuted();
    if (changeRepo != null) {
      changeRepo.flush();
    }
    if (allUsersRepo != null) {
      allUsersRepo.flush();
    }
  }

  @Nullable
  public BatchRefUpdate execute() throws OrmException, IOException {
    return execute(false);
  }

  @Nullable
  public BatchRefUpdate execute(boolean dryrun) throws OrmException, IOException {
    checkNotExecuted();
    if (isEmpty()) {
      executed = true;
      return null;
    }
    try (Timer1.Context timer = metrics.updateLatency.start(CHANGES)) {
      stage();
      // ChangeUpdates must execute before ChangeDraftUpdates.
      //
      // ChangeUpdate will automatically delete draft comments for any published
      // comments, but the updates to the two repos don't happen atomically.
      // Thus if the change meta update succeeds and the All-Users update fails,
      // we may have stale draft comments. Doing it in this order allows stale
      // comments to be filtered out by ChangeNotes, reflecting the fact that
      // comments can only go from DRAFT to PUBLISHED, not vice versa.
      BatchRefUpdate result = execute(changeRepo, dryrun, pushCert);
      execute(allUsersRepo, dryrun, null);
      if (!dryrun) {
        // dryrun is only used for the main repo
        updateAllUsersAsync.execute(refLogIdent, refLogMessage, pushCert);
      }
      executed = true;
      return result;
    } finally {
      close();
    }
  }

  private BatchRefUpdate execute(OpenRepo or, boolean dryrun, @Nullable PushCertificate pushCert)
      throws IOException {
    if (or == null || or.cmds.isEmpty()) {
      return null;
    }
    if (!dryrun) {
      or.flush();
    } else {
      // OpenRepo buffers objects separately; caller may assume that objects are available in the
      // inserter it previously passed via setChangeRepo.
      or.flushToFinalInserter();
    }

    BatchRefUpdate bru = or.repo.getRefDatabase().newBatchUpdate();
    bru.setPushCertificate(pushCert);
    if (refLogMessage != null) {
      bru.setRefLogMessage(refLogMessage, false);
    } else {
      bru.setRefLogMessage(
          firstNonNull(NoteDbUtil.guessRestApiHandler(), "Update NoteDb refs"), false);
    }
    bru.setRefLogIdent(refLogIdent != null ? refLogIdent : serverIdent.get());
    bru.setAtomic(true);
    or.cmds.addTo(bru);
    bru.setAllowNonFastForwards(true);

    if (!dryrun) {
      RefUpdateUtil.executeChecked(bru, or.rw);
    }
    return bru;
  }

  private void addCommands() throws OrmException, IOException {
    if (isEmpty()) {
      return;
    }
    checkState(changeRepo != null, "must set change repo");
    if (!draftUpdates.isEmpty()) {
      checkState(allUsersRepo != null, "must set all users repo");
    }
    changeRepo.addUpdates(changeUpdates);
    if (!draftUpdates.isEmpty()) {
      boolean publishOnly =
          draftUpdates.values().stream().anyMatch(ChangeDraftUpdate::isPublishOnly);
      if (publishOnly) {
        updateAllUsersAsync.repo().addUpdates(draftUpdates);
      } else {
        allUsersRepo.addUpdates(draftUpdates);
      }
    }
    if (!robotCommentUpdates.isEmpty()) {
      changeRepo.addUpdates(robotCommentUpdates);
    }
    if (!rewriters.isEmpty()) {
      addRewrites(rewriters, changeRepo);
    }

    for (Change.Id id : toDelete) {
      doDelete(id);
    }
  }

  private void doDelete(Change.Id id) throws IOException {
    String metaRef = RefNames.changeMetaRef(id);
    Optional<ObjectId> old = changeRepo.cmds.get(metaRef);
    if (old.isPresent()) {
      changeRepo.cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), metaRef));
    }

    // Just scan repo for ref names, but get "old" values from cmds.
    for (Ref r :
        allUsersRepo.repo.getRefDatabase().getRefsByPrefix(RefNames.refsDraftCommentsPrefix(id))) {
      old = allUsersRepo.cmds.get(r.getName());
      if (old.isPresent()) {
        allUsersRepo.cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), r.getName()));
      }
    }
  }

  private void checkNotExecuted() {
    checkState(!executed, "update has already been executed");
  }

  private static void addRewrites(ListMultimap<String, NoteDbRewriter> rewriters, OpenRepo openRepo)
      throws OrmException, IOException {
    for (Map.Entry<String, Collection<NoteDbRewriter>> entry : rewriters.asMap().entrySet()) {
      String refName = entry.getKey();
      ObjectId oldTip = openRepo.cmds.get(refName).orElse(ObjectId.zeroId());

      if (oldTip.equals(ObjectId.zeroId())) {
        throw new OrmException(String.format("Ref %s is empty", refName));
      }

      ObjectId currTip = oldTip;
      try {
        for (NoteDbRewriter noteDbRewriter : entry.getValue()) {
          ObjectId nextTip =
              noteDbRewriter.rewriteCommitHistory(openRepo.rw, openRepo.tempIns, currTip);
          if (nextTip != null) {
            currTip = nextTip;
          }
        }
      } catch (ConfigInvalidException e) {
        throw new OrmException("Cannot rewrite commit history", e);
      }

      if (!oldTip.equals(currTip)) {
        openRepo.cmds.add(new ReceiveCommand(oldTip, currTip, refName));
      }
    }
  }
}
