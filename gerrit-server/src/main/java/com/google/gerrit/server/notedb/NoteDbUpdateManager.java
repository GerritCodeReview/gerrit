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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_DRAFT_COMMENTS;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ChainedReceiveCommands;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Object to manage a single sequence of updates to NoteDb.
 * <p>
 * Instances are one-time-use. Handles updating both the change repo and the
 * All-Users repo for any affected changes, with proper ordering.
 * <p>
 * To see the state that would be applied prior to executing the full sequence
 * of updates, use {@link #stage()}.
 */
public class NoteDbUpdateManager {
  public interface Factory {
    NoteDbUpdateManager create(Project.NameKey projectName);
  }

  static class OpenRepo implements AutoCloseable {
    final Repository repo;
    final RevWalk rw;
    final ObjectInserter ins;
    final ChainedReceiveCommands cmds;
    private final boolean close;

    OpenRepo(Repository repo, RevWalk rw, ObjectInserter ins,
        ChainedReceiveCommands cmds, boolean close) {
      this.repo = checkNotNull(repo);
      this.rw = checkNotNull(rw);
      this.ins = ins;
      this.cmds = cmds;
      this.close = close;
    }

    ObjectId getObjectId(String refName) throws IOException {
      return cmds.getObjectId(repo, refName);
    }

    @Override
    public void close() {
      if (close) {
        ins.close();
        rw.close();
        repo.close();
      }
    }
  }

  private final GitRepositoryManager repoManager;
  private final NotesMigration migration;
  private final AllUsersName allUsersName;
  private final NoteDbMetrics metrics;
  private final Project.NameKey projectName;
  private final ListMultimap<String, ChangeUpdate> changeUpdates;
  private final ListMultimap<String, ChangeDraftUpdate> draftUpdates;

  private OpenRepo changeRepo;
  private OpenRepo allUsersRepo;
  private Map<Change.Id, NoteDbChangeState.Delta> staged;

  @AssistedInject
  NoteDbUpdateManager(GitRepositoryManager repoManager,
      NotesMigration migration,
      AllUsersName allUsersName,
      NoteDbMetrics metrics,
      @Assisted Project.NameKey projectName) {
    this.repoManager = repoManager;
    this.migration = migration;
    this.allUsersName = allUsersName;
    this.metrics = metrics;
    this.projectName = projectName;
    changeUpdates = ArrayListMultimap.create();
    draftUpdates = ArrayListMultimap.create();
  }

  public NoteDbUpdateManager setChangeRepo(Repository repo, RevWalk rw,
      ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(changeRepo == null, "change repo already initialized");
    changeRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  public NoteDbUpdateManager setAllUsersRepo(Repository repo, RevWalk rw,
      ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(allUsersRepo == null, "All-Users repo already initialized");
    allUsersRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  OpenRepo getChangeRepo() throws IOException {
    initChangeRepo();
    return changeRepo;
  }

  OpenRepo getAllUsersRepo() throws IOException {
    initAllUsersRepo();
    return allUsersRepo;
  }

  private void initChangeRepo() throws IOException {
    if (changeRepo == null) {
      changeRepo = openRepo(projectName);
    }
  }

  private void initAllUsersRepo() throws IOException {
    if (allUsersRepo == null) {
      allUsersRepo = openRepo(allUsersName);
    }
  }

  private OpenRepo openRepo(Project.NameKey p) throws IOException {
    Repository repo = repoManager.openRepository(p);
    ObjectInserter ins = repo.newObjectInserter();
    return new OpenRepo(repo, new RevWalk(ins.newReader()), ins,
        new ChainedReceiveCommands(), true);
  }

  private boolean isEmpty() {
    if (!migration.writeChanges()) {
      return true;
    }
    return changeUpdates.isEmpty()
        && draftUpdates.isEmpty();
  }

  /**
   * Add an update to the list of updates to execute.
   * <p>
   * Updates should only be added to the manager after all mutations have been
   * made, as this method may eagerly access the update.
   *
   * @param update the update to add.
   */
  public void add(ChangeUpdate update) {
    checkArgument(update.getProjectName().equals(projectName),
      "update for project %s cannot be added to manager for project %s",
      update.getProjectName(), projectName);
    checkState(staged == null, "cannot add new update after staging");
    changeUpdates.put(update.getRefName(), update);
    ChangeDraftUpdate du = update.getDraftUpdate();
    if (du != null) {
      draftUpdates.put(du.getRefName(), du);
    }
  }

  public void add(ChangeDraftUpdate draftUpdate) {
    checkState(staged == null, "cannot add new update after staging");
    draftUpdates.put(draftUpdate.getRefName(), draftUpdate);
  }

  /**
   * Stage updates in the manager's internal list of commands.
   *
   * @return map of the state that would get written to the applicable repo(s)
   *     for each affected change.
   * @throws OrmException if a database layer error occurs.
   * @throws IOException if a storage layer error occurs.
   */
  public Map<Change.Id, NoteDbChangeState.Delta> stage()
      throws OrmException, IOException {
    if (staged != null) {
      return staged;
    }
    try (Timer1.Context timer = metrics.stageUpdateLatency.start(CHANGES)) {
      staged = new HashMap<>();
      if (isEmpty()) {
        return staged;
      }

      initChangeRepo();
      if (!draftUpdates.isEmpty()) {
        initAllUsersRepo();
      }
      addCommands();

      Table<Change.Id, Account.Id, ObjectId> allDraftIds = getDraftIds();
      Set<Change.Id> changeIds = new HashSet<>();
      for (ReceiveCommand cmd : changeRepo.cmds.getCommands().values()) {
        Change.Id changeId = Change.Id.fromRef(cmd.getRefName());
        changeIds.add(changeId);
        Optional<ObjectId> metaId = Optional.of(cmd.getNewId());
        staged.put(
            changeId,
            NoteDbChangeState.Delta.create(
                changeId, metaId, allDraftIds.rowMap().remove(changeId)));
      }

      for (Map.Entry<Change.Id, Map<Account.Id, ObjectId>> e
          : allDraftIds.rowMap().entrySet()) {
        // If a change remains in the table at this point, it means we are
        // updating its drafts but not the change itself.
        staged.put(
            e.getKey(),
            NoteDbChangeState.Delta.create(
                e.getKey(), Optional.<ObjectId>absent(), e.getValue()));
      }

      return staged;
    }
  }

  private Table<Change.Id, Account.Id, ObjectId> getDraftIds() {
    Table<Change.Id, Account.Id, ObjectId> draftIds = HashBasedTable.create();
    if (allUsersRepo == null) {
      return draftIds;
    }
    for (ReceiveCommand cmd : allUsersRepo.cmds.getCommands().values()) {
      String r = cmd.getRefName();
      if (r.startsWith(REFS_DRAFT_COMMENTS)) {
        Change.Id changeId =
            Change.Id.fromRefPart(r.substring(REFS_DRAFT_COMMENTS.length()));
        Account.Id accountId = Account.Id.fromRefSuffix(r);
        checkDraftRef(accountId != null && changeId != null, r);
        draftIds.put(changeId, accountId, cmd.getNewId());
      }
    }
    return draftIds;
  }

  public void execute() throws OrmException, IOException {
    if (isEmpty()) {
      return;
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
      execute(changeRepo);
      execute(allUsersRepo);
    } finally {
      if (allUsersRepo != null) {
        allUsersRepo.close();
      }
      if (changeRepo != null) {
        changeRepo.close();
      }
    }
  }

  private static void execute(OpenRepo or) throws IOException {
    if (or == null || or.cmds.isEmpty()) {
      return;
    }
    or.ins.flush();
    BatchRefUpdate bru = or.repo.getRefDatabase().newBatchUpdate();
    or.cmds.addTo(bru);
    bru.setAllowNonFastForwards(true);
    bru.execute(or.rw, NullProgressMonitor.INSTANCE);
    for (ReceiveCommand cmd : bru.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Update failed: " + bru);
      }
    }
  }

  private void addCommands() throws OrmException, IOException {
    if (isEmpty()) {
      return;
    }
    checkState(changeRepo != null, "must set change repo");
    if (!draftUpdates.isEmpty()) {
      checkState(allUsersRepo != null, "must set all users repo");
    }
    addUpdates(changeUpdates, changeRepo);
    if (!draftUpdates.isEmpty()) {
      addUpdates(draftUpdates, allUsersRepo);
    }
  }

  private static <U extends AbstractChangeUpdate> void addUpdates(
      ListMultimap<String, U> all, OpenRepo or)
      throws OrmException, IOException {
    for (Map.Entry<String, Collection<U>> e : all.asMap().entrySet()) {
      String refName = e.getKey();
      Collection<U> updates = e.getValue();
      ObjectId old = firstNonNull(
          or.cmds.getObjectId(or.repo, refName), ObjectId.zeroId());
      // Only actually write to the ref if one of the updates explicitly allows
      // us to do so, i.e. it is known to represent a new change. This avoids
      // writing partial change meta if the change hasn't been backfilled yet.
      if (!allowWrite(updates, old)) {
        continue;
      }

      ObjectId curr = old;
      for (U u : updates) {
        ObjectId next = u.apply(or.rw, or.ins, curr);
        if (next == null) {
          continue;
        }
        curr = next;
      }
      if (!old.equals(curr)) {
        or.cmds.add(new ReceiveCommand(old, curr, refName));
      }
    }
  }

  private static <U extends AbstractChangeUpdate> boolean allowWrite(
      Collection<U> updates, ObjectId old) {
    if (!old.equals(ObjectId.zeroId())) {
      return true;
    }
    return updates.iterator().next().allowWriteToNewRef();
  }

  private static void checkDraftRef(boolean condition, String refName) {
    checkState(condition, "invalid draft ref: %s", refName);
  }
}
