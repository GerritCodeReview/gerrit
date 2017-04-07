// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * {@link BatchUpdate} implementation that only supports NoteDb.
 *
 * <p>Used when {@code noteDb.changes.disableReviewDb=true}, at which point ReviewDb is not
 * consulted during updates.
 */
class NoteDbBatchUpdate extends BatchUpdate {
  interface AssistedFactory {
    NoteDbBatchUpdate create(
        ReviewDb db, Project.NameKey project, CurrentUser user, Timestamp when);
  }

  static void execute(
      ImmutableList<NoteDbBatchUpdate> updates,
      BatchUpdateListener listener,
      @Nullable RequestId requestId,
      boolean dryrun)
      throws UpdateException, RestApiException {
    if (updates.isEmpty()) {
      return;
    }
    setRequestIds(updates, requestId);

    try {
      Order order = getOrder(updates);
      // TODO(dborowitz): Fuse implementations to use a single BatchRefUpdate between phases. Note
      // that we will still need to respect the order, since it also dictates the order in which
      // listener methods are called. We can revisit this later, particularly since the only user of
      // BatchUpdateListener is MergeOp, which only uses one order.
      switch (order) {
        case REPO_BEFORE_DB:
          for (NoteDbBatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (NoteDbBatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          listener.afterUpdateRefs();
          for (NoteDbBatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(dryrun), dryrun);
          }
          listener.afterUpdateChanges();
          break;
        case DB_BEFORE_REPO:
          for (NoteDbBatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(dryrun), dryrun);
          }
          listener.afterUpdateChanges();
          for (NoteDbBatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (NoteDbBatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          listener.afterUpdateRefs();
          break;
        default:
          throw new IllegalStateException("invalid execution order: " + order);
      }

      ChangeIndexer.allAsList(
              updates.stream().flatMap(u -> u.indexFutures.stream()).collect(toList()))
          .get();

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates
          .stream()
          .filter(u -> u.batchRefUpdate != null)
          .forEach(
              u -> u.gitRefUpdated.fire(u.project, u.batchRefUpdate, u.getAccount().orElse(null)));

      if (!dryrun) {
        for (NoteDbBatchUpdate u : updates) {
          u.executePostOps();
        }
      }
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  class ContextImpl implements Context {
    private Repository repoWrapper;

    @Override
    public Repository getRepository() throws IOException {
      if (repoWrapper == null) {
        repoWrapper = new ReadOnlyRepository(NoteDbBatchUpdate.this.getRepository());
      }
      return repoWrapper;
    }

    @Override
    public RevWalk getRevWalk() throws IOException {
      return NoteDbBatchUpdate.this.getRevWalk();
    }

    @Override
    public Project.NameKey getProject() {
      return project;
    }

    @Override
    public Timestamp getWhen() {
      return when;
    }

    @Override
    public TimeZone getTimeZone() {
      return tz;
    }

    @Override
    public ReviewDb getDb() {
      return db;
    }

    @Override
    public CurrentUser getUser() {
      return user;
    }

    @Override
    public Order getOrder() {
      return order;
    }
  }

  private class RepoContextImpl extends ContextImpl implements RepoContext {
    @Override
    public Repository getRepository() throws IOException {
      return NoteDbBatchUpdate.this.getRepository();
    }

    @Override
    public ObjectInserter getInserter() throws IOException {
      return NoteDbBatchUpdate.this.getObjectInserter();
    }

    @Override
    public void addRefUpdate(ReceiveCommand cmd) throws IOException {
      initRepository();
      commands.add(cmd);
    }
  }

  private class ChangeContextImpl extends ContextImpl implements ChangeContext {
    private final ChangeControl ctl;
    private final Map<PatchSet.Id, ChangeUpdate> updates;

    private boolean deleted;

    protected ChangeContextImpl(ChangeControl ctl) {
      this.ctl = checkNotNull(ctl);
      updates = new TreeMap<>(comparing(PatchSet.Id::get));
    }

    @Override
    public ChangeUpdate getUpdate(PatchSet.Id psId) {
      ChangeUpdate u = updates.get(psId);
      if (u == null) {
        u = changeUpdateFactory.create(ctl, when);
        if (newChanges.containsKey(ctl.getId())) {
          u.setAllowWriteToNewRef(true);
        }
        u.setPatchSetId(psId);
        updates.put(psId, u);
      }
      return u;
    }

    @Override
    public ChangeControl getControl() {
      return ctl;
    }

    @Override
    public void dontBumpLastUpdatedOn() {
      // Do nothing; NoteDb effectively updates timestamp if and only if a commit was written to the
      // change meta ref.
    }

    @Override
    public void deleteChange() {
      deleted = true;
    }
  }

  /** Per-change result status from {@link #executeChangeOps}. */
  private enum ChangeResult {
    SKIPPED,
    UPSERTED,
    DELETED;
  }

  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeIndexer indexer;
  private final GitReferenceUpdated gitRefUpdated;
  private final ReviewDb db;

  private List<CheckedFuture<?, IOException>> indexFutures;

  @Inject
  NoteDbBatchUpdate(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeNotes.Factory changeNotesFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeIndexer indexer,
      GitReferenceUpdated gitRefUpdated,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    super(repoManager, serverIdent, project, user, when);
    checkArgument(!db.changesTablesEnabled(), "expected Change tables to be disabled on %s", db);
    this.changeNotesFactory = changeNotesFactory;
    this.changeControlFactory = changeControlFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.indexer = indexer;
    this.gitRefUpdated = gitRefUpdated;
    this.db = db;
    this.indexFutures = new ArrayList<>();
  }

  @Override
  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), listener, requestId, false);
  }

  @Override
  protected Context newContext() {
    return new ContextImpl();
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      logDebug("Executing updateRepo on {} ops", ops.size());
      RepoContextImpl ctx = new RepoContextImpl();
      for (BatchUpdateOp op : ops.values()) {
        op.updateRepo(ctx);
      }

      logDebug("Executing updateRepo on {} RepoOnlyOps", repoOnlyOps.size());
      for (RepoOnlyOp op : repoOnlyOps) {
        op.updateRepo(ctx);
      }

      if (onSubmitValidators != null && commands != null && !commands.isEmpty()) {
        // Validation of refs has to take place here and not at the beginning
        // executeRefUpdates. Otherwise failing validation in a second
        // BatchUpdate object will happen *after* first object's
        // executeRefUpdates has finished, hence after first repo's refs have
        // been updated, which is too late.
        onSubmitValidators.validate(
            project,
            new ReadOnlyRepository(getRepository()),
            ctx.getInserter().newReader(),
            commands.getCommands());
      }

      // TODO(dborowitz): Don't flush when fusing phases.
      if (inserter != null) {
        logDebug("Flushing inserter");
        inserter.flush();
      } else {
        logDebug("No objects to flush");
      }
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  // TODO(dborowitz): Don't execute non-change ref updates separately when fusing phases.
  private void executeRefUpdates(boolean dryrun) throws IOException, RestApiException {
    if (commands == null || commands.isEmpty()) {
      logDebug("No ref updates to execute");
      return;
    }
    // May not be opened if the caller added ref updates but no new objects.
    initRepository();
    batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    commands.addTo(batchRefUpdate);
    logDebug("Executing batch of {} ref updates", batchRefUpdate.getCommands().size());
    if (dryrun) {
      return;
    }

    batchRefUpdate.execute(revWalk, NullProgressMonitor.INSTANCE);
    boolean ok = true;
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        ok = false;
        break;
      }
    }
    if (!ok) {
      throw new RestApiException("BatchRefUpdate failed: " + batchRefUpdate);
    }
  }

  private Map<Change.Id, ChangeResult> executeChangeOps(boolean dryrun) throws Exception {
    logDebug("Executing change ops");
    Map<Change.Id, ChangeResult> result =
        Maps.newLinkedHashMapWithExpectedSize(ops.keySet().size());
    Repository repo = getRepository();
    // TODO(dborowitz): Teach NoteDbUpdateManager to allow reusing the same inserter and batch ref
    // update as in executeUpdateRepo.
    try (ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader);
        NoteDbUpdateManager updateManager =
            updateManagerFactory
                .create(project)
                .setChangeRepo(repo, rw, ins, new ChainedReceiveCommands(repo))) {
      if (user.isIdentifiedUser()) {
        updateManager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
      }
      for (Map.Entry<Change.Id, Collection<BatchUpdateOp>> e : ops.asMap().entrySet()) {
        Change.Id id = e.getKey();
        ChangeContextImpl ctx = newChangeContext(id);
        boolean dirty = false;
        logDebug("Applying {} ops for change {}", e.getValue().size(), id);
        for (BatchUpdateOp op : e.getValue()) {
          dirty |= op.updateChange(ctx);
        }
        if (!dirty) {
          logDebug("No ops reported dirty, short-circuiting");
          result.put(id, ChangeResult.SKIPPED);
          continue;
        }
        for (ChangeUpdate u : ctx.updates.values()) {
          updateManager.add(u);
        }
        if (ctx.deleted) {
          logDebug("Change {} was deleted", id);
          updateManager.deleteChange(id);
          result.put(id, ChangeResult.DELETED);
        } else {
          result.put(id, ChangeResult.UPSERTED);
        }
      }

      if (!dryrun) {
        logDebug("Executing NoteDb updates");
        updateManager.execute();
      }
    }
    return result;
  }

  private ChangeContextImpl newChangeContext(Change.Id id) throws OrmException {
    logDebug("Opening change {} for update", id);
    Change c = newChanges.get(id);
    boolean isNew = c != null;
    if (!isNew) {
      // Pass a synthetic change into ChangeNotes.Factory, which will take care of checking for
      // existence and populating columns from the parsed notes state.
      // TODO(dborowitz): This dance made more sense when using Reviewdb; consider a nicer way.
      c = ChangeNotes.Factory.newNoteDbOnlyChange(project, id);
    } else {
      logDebug("Change {} is new", id);
    }
    ChangeNotes notes = changeNotesFactory.createForBatchUpdate(c, !isNew);
    ChangeControl ctl = changeControlFactory.controlFor(notes, user);
    return new ChangeContextImpl(ctl);
  }

  private void reindexChanges(Map<Change.Id, ChangeResult> updateResults, boolean dryrun) {
    if (dryrun) {
      return;
    }
    logDebug("Reindexing {} changes", updateResults.size());
    for (Map.Entry<Change.Id, ChangeResult> e : updateResults.entrySet()) {
      Change.Id id = e.getKey();
      switch (e.getValue()) {
        case UPSERTED:
          indexFutures.add(indexer.indexAsync(project, id));
          break;
        case DELETED:
          indexFutures.add(indexer.deleteAsync(id));
          break;
        case SKIPPED:
          break;
        default:
          throw new IllegalStateException("unexpected result: " + e.getValue());
      }
    }
  }

  private void executePostOps() throws Exception {
    ContextImpl ctx = new ContextImpl();
    for (BatchUpdateOp op : ops.values()) {
      op.postUpdate(ctx);
    }

    for (RepoOnlyOp op : repoOnlyOps) {
      op.postUpdate(ctx);
    }
  }
}
