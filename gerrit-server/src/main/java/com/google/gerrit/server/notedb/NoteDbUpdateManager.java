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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_DRAFT_COMMENTS;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ChainedReceiveCommands;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.InsertedObject;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gwtorm.server.OrmConcurrencyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
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
  public static final String CHANGES_READ_ONLY = "NoteDb changes are read-only";

  public interface Factory {
    NoteDbUpdateManager create(Project.NameKey projectName);
  }

  @AutoValue
  public abstract static class StagedResult {
    private static StagedResult create(
        Change.Id id, NoteDbChangeState.Delta delta, OpenRepo changeRepo, OpenRepo allUsersRepo) {
      ImmutableList<ReceiveCommand> changeCommands = ImmutableList.of();
      ImmutableList<InsertedObject> changeObjects = ImmutableList.of();
      if (changeRepo != null) {
        changeCommands = changeRepo.getCommandsSnapshot();
        changeObjects = changeRepo.tempIns.getInsertedObjects();
      }
      ImmutableList<ReceiveCommand> allUsersCommands = ImmutableList.of();
      ImmutableList<InsertedObject> allUsersObjects = ImmutableList.of();
      if (allUsersRepo != null) {
        allUsersCommands = allUsersRepo.getCommandsSnapshot();
        allUsersObjects = allUsersRepo.tempIns.getInsertedObjects();
      }
      return new AutoValue_NoteDbUpdateManager_StagedResult(
          id, delta,
          changeCommands, changeObjects,
          allUsersCommands, allUsersObjects);
    }

    public abstract Change.Id id();

    @Nullable
    public abstract NoteDbChangeState.Delta delta();

    public abstract ImmutableList<ReceiveCommand> changeCommands();

    public abstract ImmutableList<InsertedObject> changeObjects();

    public abstract ImmutableList<ReceiveCommand> allUsersCommands();

    public abstract ImmutableList<InsertedObject> allUsersObjects();
  }

  @AutoValue
  public abstract static class Result {
    static Result create(NoteDbUpdateManager.StagedResult staged, NoteDbChangeState newState) {
      return new AutoValue_NoteDbUpdateManager_Result(newState, staged);
    }

    @Nullable
    public abstract NoteDbChangeState newState();

    @Nullable
    abstract NoteDbUpdateManager.StagedResult staged();
  }

  public static class OpenRepo implements AutoCloseable {
    public final Repository repo;
    public final RevWalk rw;
    public final ChainedReceiveCommands cmds;

    private final InMemoryInserter tempIns;
    @Nullable private final ObjectInserter finalIns;

    private final boolean close;

    private OpenRepo(
        Repository repo,
        RevWalk rw,
        @Nullable ObjectInserter ins,
        ChainedReceiveCommands cmds,
        boolean close) {
      ObjectReader reader = rw.getObjectReader();
      checkArgument(
          ins == null || reader.getCreatedFromInserter() == ins,
          "expected reader to be created from %s, but was %s",
          ins,
          reader.getCreatedFromInserter());
      this.repo = checkNotNull(repo);
      this.tempIns = new InMemoryInserter(rw.getObjectReader());
      this.rw = new RevWalk(tempIns.newReader());
      this.finalIns = ins;
      this.cmds = checkNotNull(cmds);
      this.close = close;
    }

    public Optional<ObjectId> getObjectId(String refName) throws IOException {
      return cmds.get(refName);
    }

    ImmutableList<ReceiveCommand> getCommandsSnapshot() {
      return ImmutableList.copyOf(cmds.getCommands().values());
    }

    void flush() throws IOException {
      checkState(finalIns != null);
      for (InsertedObject obj : tempIns.getInsertedObjects()) {
        finalIns.insert(obj.type(), obj.data().toByteArray());
      }
      finalIns.flush();
      tempIns.clear();
    }

    @Override
    public void close() {
      rw.close();
      if (close) {
        if (finalIns != null) {
          finalIns.close();
        }
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
  private final ListMultimap<String, RobotCommentUpdate> robotCommentUpdates;
  private final Set<Change.Id> toDelete;

  private OpenRepo changeRepo;
  private OpenRepo allUsersRepo;
  private Map<Change.Id, StagedResult> staged;
  private boolean checkExpectedState = true;

  @AssistedInject
  NoteDbUpdateManager(
      GitRepositoryManager repoManager,
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
    robotCommentUpdates = ArrayListMultimap.create();
    toDelete = new HashSet<>();
  }

  @Override
  public void close() {
    try {
      if (allUsersRepo != null) {
        OpenRepo r = allUsersRepo;
        allUsersRepo = null;
        r.close();
      }
    } finally {
      if (changeRepo != null) {
        OpenRepo r = changeRepo;
        changeRepo = null;
        r.close();
      }
    }
  }

  public NoteDbUpdateManager setChangeRepo(
      Repository repo, RevWalk rw, @Nullable ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(changeRepo == null, "change repo already initialized");
    changeRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  public NoteDbUpdateManager setAllUsersRepo(
      Repository repo, RevWalk rw, @Nullable ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(allUsersRepo == null, "All-Users repo already initialized");
    allUsersRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  public NoteDbUpdateManager setCheckExpectedState(boolean checkExpectedState) {
    this.checkExpectedState = checkExpectedState;
    return this;
  }

  public OpenRepo getChangeRepo() throws IOException {
    initChangeRepo();
    return changeRepo;
  }

  public OpenRepo getAllUsersRepo() throws IOException {
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
    return new OpenRepo(
        repo, new RevWalk(ins.newReader()), ins, new ChainedReceiveCommands(repo), true);
  }

  private boolean isEmpty() {
    if (!migration.commitChangeWrites()) {
      return true;
    }
    return changeUpdates.isEmpty()
        && draftUpdates.isEmpty()
        && robotCommentUpdates.isEmpty()
        && toDelete.isEmpty();
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
    checkArgument(
        update.getProjectName().equals(projectName),
        "update for project %s cannot be added to manager for project %s",
        update.getProjectName(),
        projectName);
    checkState(staged == null, "cannot add new update after staging");
    changeUpdates.put(update.getRefName(), update);
    ChangeDraftUpdate du = update.getDraftUpdate();
    if (du != null) {
      draftUpdates.put(du.getRefName(), du);
    }
    RobotCommentUpdate rcu = update.getRobotCommentUpdate();
    if (rcu != null) {
      robotCommentUpdates.put(rcu.getRefName(), rcu);
    }
  }

  public void add(ChangeDraftUpdate draftUpdate) {
    checkState(staged == null, "cannot add new update after staging");
    draftUpdates.put(draftUpdate.getRefName(), draftUpdate);
  }

  public void deleteChange(Change.Id id) {
    checkState(staged == null, "cannot add new change to delete after staging");
    toDelete.add(id);
  }

  /**
   * Stage updates in the manager's internal list of commands.
   *
   * @return map of the state that would get written to the applicable repo(s) for each affected
   *     change.
   * @throws OrmException if a database layer error occurs.
   * @throws IOException if a storage layer error occurs.
   */
  public Map<Change.Id, StagedResult> stage() throws OrmException, IOException {
    if (staged != null) {
      return staged;
    }
    try (Timer1.Context timer = metrics.stageUpdateLatency.start(CHANGES)) {
      staged = new HashMap<>();
      if (isEmpty()) {
        return staged;
      }

      initChangeRepo();
      if (!draftUpdates.isEmpty() || !toDelete.isEmpty()) {
        initAllUsersRepo();
      }
      checkExpectedState();
      addCommands();

      Table<Change.Id, Account.Id, ObjectId> allDraftIds = getDraftIds();
      Set<Change.Id> changeIds = new HashSet<>();
      for (ReceiveCommand cmd : changeRepo.getCommandsSnapshot()) {
        Change.Id changeId = Change.Id.fromRef(cmd.getRefName());
        changeIds.add(changeId);
        Optional<ObjectId> metaId = Optional.of(cmd.getNewId());
        staged.put(
            changeId,
            StagedResult.create(
                changeId,
                NoteDbChangeState.Delta.create(
                    changeId, metaId, allDraftIds.rowMap().remove(changeId)),
                changeRepo,
                allUsersRepo));
      }

      for (Map.Entry<Change.Id, Map<Account.Id, ObjectId>> e : allDraftIds.rowMap().entrySet()) {
        // If a change remains in the table at this point, it means we are
        // updating its drafts but not the change itself.
        StagedResult r =
            StagedResult.create(
                e.getKey(),
                NoteDbChangeState.Delta.create(e.getKey(), Optional.empty(), e.getValue()),
                changeRepo,
                allUsersRepo);
        checkState(
            r.changeCommands().isEmpty(),
            "should not have change commands when updating only drafts: %s",
            r);
        staged.put(r.id(), r);
      }

      return staged;
    }
  }

  public Result stageAndApplyDelta(Change change) throws OrmException, IOException {
    StagedResult sr = stage().get(change.getId());
    NoteDbChangeState newState =
        NoteDbChangeState.applyDelta(change, sr != null ? sr.delta() : null);
    return Result.create(sr, newState);
  }

  private Table<Change.Id, Account.Id, ObjectId> getDraftIds() {
    Table<Change.Id, Account.Id, ObjectId> draftIds = HashBasedTable.create();
    if (allUsersRepo == null) {
      return draftIds;
    }
    for (ReceiveCommand cmd : allUsersRepo.getCommandsSnapshot()) {
      String r = cmd.getRefName();
      if (r.startsWith(REFS_DRAFT_COMMENTS)) {
        Change.Id changeId = Change.Id.fromRefPart(r.substring(REFS_DRAFT_COMMENTS.length()));
        Account.Id accountId = Account.Id.fromRefSuffix(r);
        checkDraftRef(accountId != null && changeId != null, r);
        draftIds.put(changeId, accountId, cmd.getNewId());
      }
    }
    return draftIds;
  }

  public void flush() throws IOException {
    if (changeRepo != null) {
      changeRepo.flush();
    }
    if (allUsersRepo != null) {
      allUsersRepo.flush();
    }
  }

  public void execute() throws OrmException, IOException {
    // Check before even inspecting the list, as this is a programmer error.
    if (migration.failChangeWrites()) {
      throw new OrmException(CHANGES_READ_ONLY);
    }
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
      close();
    }
  }

  private static void execute(OpenRepo or) throws IOException {
    if (or == null || or.cmds.isEmpty()) {
      return;
    }
    or.flush();
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
    if (!robotCommentUpdates.isEmpty()) {
      addUpdates(robotCommentUpdates, changeRepo);
    }
    for (Change.Id id : toDelete) {
      doDelete(id);
    }
    checkExpectedState();
  }

  private void doDelete(Change.Id id) throws IOException {
    String metaRef = RefNames.changeMetaRef(id);
    Optional<ObjectId> old = changeRepo.cmds.get(metaRef);
    if (old.isPresent()) {
      changeRepo.cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), metaRef));
    }

    // Just scan repo for ref names, but get "old" values from cmds.
    for (Ref r :
        allUsersRepo.repo.getRefDatabase().getRefs(RefNames.refsDraftCommentsPrefix(id)).values()) {
      old = allUsersRepo.cmds.get(r.getName());
      if (old.isPresent()) {
        allUsersRepo.cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), r.getName()));
      }
    }
  }

  public static class MismatchedStateException extends OrmException {
    private static final long serialVersionUID = 1L;

    private MismatchedStateException(Change.Id id, NoteDbChangeState expectedState) {
      super(
          String.format(
              "cannot apply NoteDb updates for change %s;" + " change meta ref does not match %s",
              id, expectedState.getChangeMetaId().name()));
    }
  }

  private void checkExpectedState() throws OrmException, IOException {
    if (!checkExpectedState) {
      return;
    }

    // Refuse to apply an update unless the state in NoteDb matches the state
    // claimed in the ref. This means we may have failed a NoteDb ref update,
    // and it would be incorrect to claim that the ref is up to date after this
    // pipeline.
    //
    // Generally speaking, this case should be rare; in most cases, we should
    // have detected and auto-fixed the stale state when creating ChangeNotes
    // that got passed into the ChangeUpdate.
    for (Collection<ChangeUpdate> us : changeUpdates.asMap().values()) {
      ChangeUpdate u = us.iterator().next();
      NoteDbChangeState expectedState = NoteDbChangeState.parse(u.getChange());

      if (expectedState == null) {
        // No previous state means we haven't previously written NoteDb graphs
        // for this change yet. This means either:
        //  - The change is new, and we'll be creating its ref.
        //  - We short-circuited before adding any commands that update this
        //    ref, and we won't stage a delta for this change either.
        // Either way, it is safe to proceed here rather than throwing
        // MismatchedStateException.
        continue;
      }

      if (expectedState.getPrimaryStorage() == PrimaryStorage.NOTE_DB) {
        // NoteDb is primary, no need to compare state to ReviewDb.
        continue;
      }

      if (!expectedState.isChangeUpToDate(changeRepo.cmds.getRepoRefCache())) {
        throw new MismatchedStateException(u.getId(), expectedState);
      }
    }

    for (Collection<ChangeDraftUpdate> us : draftUpdates.asMap().values()) {
      ChangeDraftUpdate u = us.iterator().next();
      NoteDbChangeState expectedState = NoteDbChangeState.parse(u.getChange());

      if (expectedState == null || expectedState.getPrimaryStorage() == PrimaryStorage.NOTE_DB) {
        continue; // See above.
      }

      Account.Id accountId = u.getAccountId();
      if (!expectedState.areDraftsUpToDate(allUsersRepo.cmds.getRepoRefCache(), accountId)) {
        throw new OrmConcurrencyException(
            String.format(
                "cannot apply NoteDb updates for change %s;"
                    + " draft ref for account %s does not match %s",
                u.getId(), accountId, expectedState.getChangeMetaId().name()));
      }
    }
  }

  private static <U extends AbstractChangeUpdate> void addUpdates(
      ListMultimap<String, U> all, OpenRepo or) throws OrmException, IOException {
    for (Map.Entry<String, Collection<U>> e : all.asMap().entrySet()) {
      String refName = e.getKey();
      Collection<U> updates = e.getValue();
      ObjectId old = or.cmds.get(refName).orElse(ObjectId.zeroId());
      // Only actually write to the ref if one of the updates explicitly allows
      // us to do so, i.e. it is known to represent a new change. This avoids
      // writing partial change meta if the change hasn't been backfilled yet.
      if (!allowWrite(updates, old)) {
        continue;
      }

      ObjectId curr = old;
      for (U u : updates) {
        ObjectId next = u.apply(or.rw, or.tempIns, curr);
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
