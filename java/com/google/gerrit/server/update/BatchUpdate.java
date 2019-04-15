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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.flogger.LazyArgs.lazy;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.TooManyUpdatesException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Helper for a set of change updates that should be applied to the NoteDb database.
 *
 * <p>An update operation can be divided into three phases:
 *
 * <ol>
 *   <li>Git reference updates
 *   <li>Review metadata updates
 *   <li>Post-update steps
 *   <li>
 * </ol>
 *
 * A single conceptual operation, such as a REST API call or a merge operation, may make multiple
 * changes at each step, which all need to be serialized relative to each other. Moreover, for
 * consistency, the git ref updates must be visible to the review metadata updates, since for
 * example the metadata might refer to newly-created patch set refs. In NoteDb, this is accomplished
 * by combining these two phases into a single {@link BatchRefUpdate}.
 *
 * <p>Similarly, all post-update steps, such as sending email, must run only after all storage
 * mutations have completed.
 */
public class BatchUpdate implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(BatchUpdate.Factory.class);
      }
    };
  }

  // TODO(dborowitz): Make this package-private to force all callers to use RetryHelper.
  public interface Factory {
    BatchUpdate create(Project.NameKey project, CurrentUser user, Timestamp when);
  }

  public static void execute(
      Collection<BatchUpdate> updates, BatchUpdateListener listener, boolean dryrun)
      throws UpdateException, RestApiException {
    requireNonNull(listener);
    if (updates.isEmpty()) {
      return;
    }

    checkDifferentProject(updates);

    try {
      @SuppressWarnings("deprecation")
      List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> indexFutures =
          new ArrayList<>();
      List<ChangesHandle> handles = new ArrayList<>(updates.size());
      try {
        for (BatchUpdate u : updates) {
          u.executeUpdateRepo();
        }
        listener.afterUpdateRepos();
        for (BatchUpdate u : updates) {
          handles.add(u.executeChangeOps(dryrun));
        }
        for (ChangesHandle h : handles) {
          h.execute();
          indexFutures.addAll(h.startIndexFutures());
        }
        listener.afterUpdateRefs();
        listener.afterUpdateChanges();
      } finally {
        for (ChangesHandle h : handles) {
          h.close();
        }
      }

      ChangeIndexer.allAsList(indexFutures).get();

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates.stream()
          .filter(u -> u.batchRefUpdate != null)
          .forEach(
              u -> u.gitRefUpdated.fire(u.project, u.batchRefUpdate, u.getAccount().orElse(null)));

      if (!dryrun) {
        for (BatchUpdate u : updates) {
          u.executePostOps();
        }
      }
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  private static void checkDifferentProject(Collection<BatchUpdate> updates) {
    Multiset<Project.NameKey> projectCounts =
        updates.stream().map(u -> u.project).collect(toImmutableMultiset());
    checkArgument(
        projectCounts.entrySet().size() == updates.size(),
        "updates must all be for different projects, got: %s",
        projectCounts);
  }

  private static void wrapAndThrowException(Exception e) throws UpdateException, RestApiException {
    Throwables.throwIfUnchecked(e);

    // Propagate REST API exceptions thrown by operations; they commonly throw exceptions like
    // ResourceConflictException to indicate an atomic update failure.
    Throwables.throwIfInstanceOf(e, UpdateException.class);
    Throwables.throwIfInstanceOf(e, RestApiException.class);

    // Convert other common non-REST exception types with user-visible messages to corresponding
    // REST exception types
    if (e instanceof InvalidChangeOperationException || e instanceof TooManyUpdatesException) {
      throw new ResourceConflictException(e.getMessage(), e);
    } else if (e instanceof NoSuchChangeException
        || e instanceof NoSuchRefException
        || e instanceof NoSuchProjectException) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    }

    // Otherwise, wrap in a generic UpdateException, which does not include a user-visible message.
    throw new UpdateException(e);
  }

  class ContextImpl implements Context {
    @Override
    public RepoView getRepoView() throws IOException {
      return BatchUpdate.this.getRepoView();
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
    public CurrentUser getUser() {
      return user;
    }

    @Override
    public NotifyResolver.Result getNotify(Change.Id changeId) {
      NotifyHandling notifyHandling = perChangeNotifyHandling.get(changeId);
      return notifyHandling != null ? notify.withHandling(notifyHandling) : notify;
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

    ChangeContextImpl(ChangeNotes notes) {
      this.notes = requireNonNull(notes);
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

  private final GitRepositoryManager repoManager;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeIndexer indexer;
  private final GitReferenceUpdated gitRefUpdated;

  private final Project.NameKey project;
  private final CurrentUser user;
  private final Timestamp when;
  private final TimeZone tz;

  private final ListMultimap<Change.Id, BatchUpdateOp> ops =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  private final Map<Change.Id, Change> newChanges = new HashMap<>();
  private final List<RepoOnlyOp> repoOnlyOps = new ArrayList<>();
  private final Map<Change.Id, NotifyHandling> perChangeNotifyHandling = new HashMap<>();

  private RepoView repoView;
  private BatchRefUpdate batchRefUpdate;
  private OnSubmitValidators onSubmitValidators;
  private PushCertificate pushCert;
  private String refLogMessage;
  private NotifyResolver.Result notify = NotifyResolver.Result.all();

  @Inject
  BatchUpdate(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeNotes.Factory changeNotesFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeIndexer indexer,
      GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    this.repoManager = repoManager;
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.indexer = indexer;
    this.gitRefUpdated = gitRefUpdated;
    this.project = project;
    this.user = user;
    this.when = when;
    tz = serverIdent.getTimeZone();
  }

  @Override
  public void close() {
    if (repoView != null) {
      repoView.close();
    }
  }

  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), listener, false);
  }

  public void execute() throws UpdateException, RestApiException {
    execute(BatchUpdateListener.NONE);
  }

  public BatchUpdate setRepository(Repository repo, RevWalk revWalk, ObjectInserter inserter) {
    checkState(this.repoView == null, "repo already set");
    repoView = new RepoView(repo, revWalk, inserter);
    return this;
  }

  public BatchUpdate setPushCertificate(@Nullable PushCertificate pushCert) {
    this.pushCert = pushCert;
    return this;
  }

  public BatchUpdate setRefLogMessage(@Nullable String refLogMessage) {
    this.refLogMessage = refLogMessage;
    return this;
  }

  /**
   * Set the default notification settings for all changes in the batch.
   *
   * @param notify notification settings.
   * @return this.
   */
  public BatchUpdate setNotify(NotifyResolver.Result notify) {
    this.notify = requireNonNull(notify);
    return this;
  }

  /**
   * Override the {@link NotifyHandling} on a per-change basis.
   *
   * <p>Only the handling enum can be overridden; all changes share the same value for {@link
   * com.google.gerrit.server.change.NotifyResolver.Result#accounts()}.
   *
   * @param changeId change ID.
   * @param notifyHandling notify handling.
   * @return this.
   */
  public BatchUpdate setNotifyHandling(Change.Id changeId, NotifyHandling notifyHandling) {
    this.perChangeNotifyHandling.put(changeId, requireNonNull(notifyHandling));
    return this;
  }

  /**
   * Add a validation step for intended ref operations, which will be performed at the end of {@link
   * RepoOnlyOp#updateRepo(RepoContext)} step.
   */
  public BatchUpdate setOnSubmitValidators(OnSubmitValidators onSubmitValidators) {
    this.onSubmitValidators = onSubmitValidators;
    return this;
  }

  private void initRepository() throws IOException {
    if (repoView == null) {
      repoView = new RepoView(repoManager, project);
    }
  }

  private RepoView getRepoView() throws IOException {
    initRepository();
    return repoView;
  }

  private Optional<AccountState> getAccount() {
    return user.isIdentifiedUser()
        ? Optional.of(user.asIdentifiedUser().state())
        : Optional.empty();
  }

  public Map<String, ReceiveCommand> getRefUpdates() {
    return repoView != null ? repoView.getCommands().getCommands() : ImmutableMap.of();
  }

  public BatchUpdate addOp(Change.Id id, BatchUpdateOp op) {
    checkArgument(!(op instanceof InsertChangeOp), "use insertChange");
    requireNonNull(op);
    ops.put(id, op);
    return this;
  }

  public BatchUpdate addRepoOnlyOp(RepoOnlyOp op) {
    checkArgument(!(op instanceof BatchUpdateOp), "use addOp()");
    repoOnlyOps.add(op);
    return this;
  }

  public BatchUpdate insertChange(InsertChangeOp op) throws IOException {
    Context ctx = new ContextImpl();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
    return this;
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      logDebug("Executing updateRepo on %d ops", ops.size());
      RepoContextImpl ctx = new RepoContextImpl();
      for (BatchUpdateOp op : ops.values()) {
        op.updateRepo(ctx);
      }

      logDebug("Executing updateRepo on %d RepoOnlyOps", repoOnlyOps.size());
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
      BatchUpdate.this.batchRefUpdate = manager.execute(dryrun);
    }

    @SuppressWarnings("deprecation")
    List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> startIndexFutures() {
      if (dryrun) {
        return ImmutableList.of();
      }
      logDebug("Reindexing %d changes", results.size());
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
      logDebug(
          "Applying %d ops for change %s: %s",
          e.getValue().size(),
          id,
          lazy(() -> e.getValue().stream().map(op -> op.getClass().getName()).collect(toSet())));
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
        logDebug("Change %s was deleted", id);
        handle.manager.deleteChange(id);
        handle.setResult(id, ChangeResult.DELETED);
      } else {
        handle.setResult(id, ChangeResult.UPSERTED);
      }
    }
    return handle;
  }

  private ChangeContextImpl newChangeContext(Change.Id id) throws OrmException {
    logDebug("Opening change %s for update", id);
    Change c = newChanges.get(id);
    boolean isNew = c != null;
    if (!isNew) {
      // Pass a synthetic change into ChangeNotes.Factory, which will take care of checking for
      // existence and populating columns from the parsed notes state.
      // TODO(dborowitz): This dance made more sense when using Reviewdb; consider a nicer way.
      c = ChangeNotes.Factory.newChange(project, id);
    } else {
      logDebug("Change %s is new", id);
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

  private static void logDebug(String msg) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg);
    }
  }

  private static void logDebug(String msg, @Nullable Object arg) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg, arg);
    }
  }

  private static void logDebug(
      String msg, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg, arg1, arg2, arg3);
    }
  }
}
