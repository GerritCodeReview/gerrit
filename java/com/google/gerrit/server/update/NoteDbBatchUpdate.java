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
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * {@link BatchUpdate} implementation using only NoteDb that updates code refs and meta refs in a
 * single {@link org.eclipse.jgit.lib.BatchRefUpdate}.
 *
 * <p>Used when {@code noteDb.changes.disableReviewDb=true}, at which point ReviewDb is not
 * consulted during updates.
 */
public class NoteDbBatchUpdate extends BatchUpdate {
  public interface AssistedFactory {
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
      @SuppressWarnings("deprecation")
      List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> indexFutures =
          new ArrayList<>();
      List<ChangesHandle> handles = new ArrayList<>(updates.size());
      Order order = getOrder(updates, listener);
      try {
        switch (order) {
          case REPO_BEFORE_DB:
            for (NoteDbBatchUpdate u : updates) {
              u.executeUpdateRepo();
            }
            listener.afterUpdateRepos();
            for (NoteDbBatchUpdate u : updates) {
              handles.add(u.executeChangeOps(dryrun));
            }
            for (ChangesHandle h : handles) {
              h.execute();
              indexFutures.addAll(h.startIndexFutures());
            }
            listener.afterUpdateRefs();
            listener.afterUpdateChanges();
            break;

          case DB_BEFORE_REPO:
            // Call updateChange for each op before updateRepo, but defer executing the
            // NoteDbUpdateManager until after calling updateRepo. They share an inserter and
            // BatchRefUpdate, so it will all execute as a single batch. But we have to let
            // NoteDbUpdateManager actually execute the update, since it has to interleave it
            // properly with All-Users updates.
            //
            // TODO(dborowitz): This may still result in multiple updates to All-Users, but that's
            // currently not a big deal because multi-change batches generally aren't affecting
            // drafts anyway.
            for (NoteDbBatchUpdate u : updates) {
              handles.add(u.executeChangeOps(dryrun));
            }
            for (NoteDbBatchUpdate u : updates) {
              u.executeUpdateRepo();
            }
            for (ChangesHandle h : handles) {
              // TODO(dborowitz): This isn't quite good enough: in theory updateRepo may want to
              // see the results of change meta commands, but they aren't actually added to the
              // BatchUpdate until the body of execute. To fix this, execute needs to be split up
              // into a method that returns a BatchRefUpdate before execution. Not a big deal at the
              // moment, because this order is only used for deleting changes, and those updateRepo
              // implementations definitely don't need to observe the updated change meta refs.
              h.execute();
              indexFutures.addAll(h.startIndexFutures());
            }
            break;
          default:
            throw new IllegalStateException("invalid execution order: " + order);
        }
      } finally {
        for (ChangesHandle h : handles) {
          h.close();
        }
      }

      ChangeIndexer.allAsList(indexFutures).get();

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
    @Override
    public RepoView getRepoView() throws IOException {
      return NoteDbBatchUpdate.this.getRepoView();
    }

    @Override
    public RevWalk getRevWalk() throws IOException {
      return getRepoView().getRevWalk();
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
    public ObjectInserter getInserter() throws IOException {
      return getRepoView().getInserterWrapper();
    }

    @Override
    public void addRefUpdate(ReceiveCommand cmd) throws IOException {
      getRepoView().getCommands().add(cmd);
    }
  }

  private class ChangeContextImpl extends ContextImpl implements ChangeContext {
    private final ChangeNotes notes;
    private final Map<PatchSet.Id, ChangeUpdate> updates;

    private boolean deleted;

    protected ChangeContextImpl(ChangeNotes notes) {
      this.notes = checkNotNull(notes);
      updates = new TreeMap<>(comparing(PatchSet.Id::get));
    }

    @Override
    public ChangeUpdate getUpdate(PatchSet.Id psId) {
      ChangeUpdate u = updates.get(psId);
      if (u == null) {
        u = changeUpdateFactory.create(notes, user, when);
        if (newChanges.containsKey(notes.getChangeId())) {
          u.setAllowWriteToNewRef(true);
        }
        u.setPatchSetId(psId);
        updates.put(psId, u);
      }
      return u;
    }

    @Override
    public ChangeNotes getNotes() {
      return notes;
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
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeIndexer indexer;
  private final GitReferenceUpdated gitRefUpdated;
  private final ReviewDb db;

  @Inject
  NoteDbBatchUpdate(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeNotes.Factory changeNotesFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeIndexer indexer,
      GitReferenceUpdated gitRefUpdated,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    super(repoManager, serverIdent, project, user, when);
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.indexer = indexer;
    this.gitRefUpdated = gitRefUpdated;
    this.db = db;
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

      if (onSubmitValidators != null && !getRefUpdates().isEmpty()) {
        // Validation of refs has to take place here and not at the beginning of executeRefUpdates.
        // Otherwise, failing validation in a second BatchUpdate object will happen *after* the
        // first update's executeRefUpdates has finished, hence after first repo's refs have been
        // updated, which is too late.
        onSubmitValidators.validate(
            project, ctx.getRevWalk().getObjectReader(), repoView.getCommands());
      }
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private class ChangesHandle implements AutoCloseable {
    private final NoteDbUpdateManager manager;
    private final boolean dryrun;
    private final Map<Change.Id, ChangeResult> results;

    ChangesHandle(NoteDbUpdateManager manager, boolean dryrun) {
      this.manager = manager;
      this.dryrun = dryrun;
      results = new HashMap<>();
    }

    @Override
    public void close() {
      manager.close();
    }

    void setResult(Change.Id id, ChangeResult result) {
      ChangeResult old = results.putIfAbsent(id, result);
      checkArgument(old == null, "result for change %s already set: %s", id, old);
    }

    void execute() throws OrmException, IOException {
      NoteDbBatchUpdate.this.batchRefUpdate = manager.execute(dryrun);
    }

    @SuppressWarnings("deprecation")
    List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> startIndexFutures() {
      if (dryrun) {
        return ImmutableList.of();
      }
      logDebug("Reindexing {} changes", results.size());
      List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> indexFutures =
          new ArrayList<>(results.size());
      for (Map.Entry<Change.Id, ChangeResult> e : results.entrySet()) {
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
      return indexFutures;
    }
  }

  private ChangesHandle executeChangeOps(boolean dryrun) throws Exception {
    logDebug("Executing change ops");
    initRepository();
    Repository repo = repoView.getRepository();
    checkState(
        repo.getRefDatabase().performsAtomicTransactions(),
        "cannot use NoteDb with a repository that does not support atomic batch ref updates: %s",
        repo);

    ChangesHandle handle =
        new ChangesHandle(
            updateManagerFactory
                .create(project)
                .setChangeRepo(
                    repo, repoView.getRevWalk(), repoView.getInserter(), repoView.getCommands()),
            dryrun);
    if (user.isIdentifiedUser()) {
      handle.manager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
    }
    handle.manager.setRefLogMessage(refLogMessage);
    handle.manager.setPushCertificate(pushCert);
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
        handle.setResult(id, ChangeResult.SKIPPED);
        continue;
      }
      for (ChangeUpdate u : ctx.updates.values()) {
        handle.manager.add(u);
      }
      if (ctx.deleted) {
        logDebug("Change {} was deleted", id);
        handle.manager.deleteChange(id);
        handle.setResult(id, ChangeResult.DELETED);
      } else {
        handle.setResult(id, ChangeResult.UPSERTED);
      }
    }
    return handle;
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
    return new ChangeContextImpl(notes);
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
