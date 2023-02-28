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
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectChangeKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.AttentionSetObserver;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.LimitExceededException;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

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

  public interface Factory {
    BatchUpdate create(Project.NameKey project, CurrentUser user, Instant when);
  }

  public static void execute(
      Collection<BatchUpdate> updates, ImmutableList<BatchUpdateListener> listeners, boolean dryrun)
      throws UpdateException, RestApiException {
    requireNonNull(listeners);
    if (updates.isEmpty()) {
      return;
    }

    checkDifferentProject(updates);

    try {
      List<ListenableFuture<ChangeData>> indexFutures = new ArrayList<>();
      List<ChangesHandle> changesHandles = new ArrayList<>(updates.size());
      try {
        for (BatchUpdate u : updates) {
          u.executeUpdateRepo();
        }
        notifyAfterUpdateRepo(listeners);
        for (BatchUpdate u : updates) {
          changesHandles.add(u.executeChangeOps(listeners, dryrun));
        }
        for (ChangesHandle h : changesHandles) {
          h.execute();
          if (h.requiresReindex()) {
            indexFutures.addAll(h.startIndexFutures());
          }
        }
        notifyAfterUpdateRefs(listeners);
        notifyAfterUpdateChanges(listeners);
      } finally {
        for (ChangesHandle h : changesHandles) {
          h.close();
        }
      }

      Map<Change.Id, ChangeData> changeDatas =
          Futures.allAsList(indexFutures).get().stream()
              // filter out null values that were returned for change deletions
              .filter(Objects::nonNull)
              .collect(toMap(cd -> cd.change().getId(), Function.identity()));

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates.forEach(BatchUpdate::fireRefChangeEvent);

      if (!dryrun) {
        for (BatchUpdate u : updates) {
          u.executePostOps(changeDatas);
        }
      }
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  private static void notifyAfterUpdateRepo(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRepos();
    }
  }

  private static void notifyAfterUpdateRefs(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRefs();
    }
  }

  private static void notifyAfterUpdateChanges(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateChanges();
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
    // Convert common non-REST exception types with user-visible messages to corresponding REST
    // exception types.
    if (e instanceof InvalidChangeOperationException || e instanceof LimitExceededException) {
      throw new ResourceConflictException(e.getMessage(), e);
    } else if (e instanceof NoSuchChangeException
        || e instanceof NoSuchRefException
        || e instanceof NoSuchProjectException) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } else if (e instanceof CommentsRejectedException) {
      // SC_BAD_REQUEST is not ideal because it's not a syntactic error, but there is no better
      // status code and it's isolated in monitoring.
      throw new BadRequestException(e.getMessage(), e);
    }

    Throwables.throwIfUnchecked(e);

    // Propagate REST API exceptions thrown by operations; they commonly throw exceptions like
    // ResourceConflictException to indicate an atomic update failure.
    Throwables.throwIfInstanceOf(e, UpdateException.class);
    Throwables.throwIfInstanceOf(e, RestApiException.class);

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
    public Instant getWhen() {
      return when;
    }

    @Override
    public ZoneId getZoneId() {
      return zoneId;
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

    /**
     * Updates where the caller instructed us to create one NoteDb commit per update. Keyed by
     * PatchSet.Id only for convenience.
     */
    private final Map<PatchSet.Id, ChangeUpdate> defaultUpdates;

    /**
     * Updates where the caller allowed us to combine potentially multiple adjustments into a single
     * commit in NoteDb by re-using the same ChangeUpdate instance. Will still be one commit per
     * patch set.
     */
    private final ListMultimap<PatchSet.Id, ChangeUpdate> distinctUpdates;

    private boolean deleted;

    ChangeContextImpl(ChangeNotes notes) {
      this.notes = requireNonNull(notes);
      defaultUpdates = new TreeMap<>(comparing(PatchSet.Id::get));
      distinctUpdates = ArrayListMultimap.create();
    }

    @Override
    public ChangeUpdate getUpdate(PatchSet.Id psId) {
      ChangeUpdate u = defaultUpdates.get(psId);
      if (u == null) {
        u = getNewChangeUpdate(psId);
        defaultUpdates.put(psId, u);
      }
      return u;
    }

    @Override
    public ChangeUpdate getDistinctUpdate(PatchSet.Id psId) {
      ChangeUpdate u = getNewChangeUpdate(psId);
      distinctUpdates.put(psId, u);
      return u;
    }

    private ChangeUpdate getNewChangeUpdate(PatchSet.Id psId) {
      ChangeUpdate u = changeUpdateFactory.create(notes, user, when);
      if (newChanges.containsKey(notes.getChangeId())) {
        u.setAllowWriteToNewRef(true);
      }
      u.setPatchSetId(psId);
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

  private class PostUpdateContextImpl extends ContextImpl implements PostUpdateContext {
    private final Map<Change.Id, ChangeData> changeDatas;

    PostUpdateContextImpl(Map<Change.Id, ChangeData> changeDatas) {
      this.changeDatas = changeDatas;
    }

    @Override
    public ChangeData getChangeData(Project.NameKey projectName, Change.Id changeId) {
      return changeDatas.computeIfAbsent(
          changeId, id -> changeDataFactory.create(projectName, changeId));
    }

    @Override
    public ChangeData getChangeData(Change change) {
      return changeDatas.computeIfAbsent(change.getId(), id -> changeDataFactory.create(change));
    }
  }

  /** Per-change result status from {@link #executeChangeOps}. */
  private enum ChangeResult {
    SKIPPED,
    UPSERTED,
    DELETED
  }

  private final GitRepositoryManager repoManager;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeIndexer indexer;
  private final GitReferenceUpdated gitRefUpdated;

  private final Project.NameKey project;
  private final CurrentUser user;
  private final Instant when;
  private final ZoneId zoneId;

  private final ListMultimap<Change.Id, BatchUpdateOp> ops =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  private final Map<Change.Id, Change> newChanges = new HashMap<>();
  private final List<RepoOnlyOp> repoOnlyOps = new ArrayList<>();
  private final Map<Change.Id, NotifyHandling> perChangeNotifyHandling = new HashMap<>();

  private RepoView repoView;
  private BatchRefUpdate batchRefUpdate;
  private ImmutableListMultimap<ProjectChangeKey, AttentionSetUpdate> attentionSetUpdates;

  private boolean executed;
  private OnSubmitValidators onSubmitValidators;
  private PushCertificate pushCert;
  private String refLogMessage;
  private NotifyResolver.Result notify = NotifyResolver.Result.all();
  // Batch operations doesn't need observer
  private AttentionSetObserver attentionSetObserver;

  @Inject
  BatchUpdate(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory changeNotesFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeIndexer indexer,
      GitReferenceUpdated gitRefUpdated,
      AttentionSetObserver attentionSetObserver,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Instant when) {
    this.repoManager = repoManager;
    this.changeDataFactory = changeDataFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.indexer = indexer;
    this.gitRefUpdated = gitRefUpdated;
    this.project = project;
    this.user = user;
    this.when = when;
    this.attentionSetObserver = attentionSetObserver;
    zoneId = serverIdent.getZoneId();
  }

  @Override
  public void close() {
    if (repoView != null) {
      repoView.close();
    }
  }

  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), ImmutableList.of(listener), false);
  }

  public void execute() throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), ImmutableList.of(), false);
  }

  public boolean isExecuted() {
    return executed;
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

  public Project.NameKey getProject() {
    return project;
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

  /**
   * Return the references successfully updated by this BatchUpdate with their command. In dryrun,
   * we assume all updates were successful.
   */
  public Map<BranchNameKey, ReceiveCommand> getSuccessfullyUpdatedBranches(boolean dryrun) {
    return getRefUpdates().entrySet().stream()
        .filter(entry -> dryrun || entry.getValue().getResult() == Result.OK)
        .collect(
            toMap(entry -> BranchNameKey.create(project, entry.getKey()), Map.Entry::getValue));
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
      for (Map.Entry<Change.Id, BatchUpdateOp> op : ops.entries()) {
        try (TraceContext.TraceTimer ignored =
            TraceContext.newTimer(
                op.getClass().getSimpleName() + "#updateRepo",
                Metadata.builder()
                    .projectName(project.get())
                    .changeId(op.getKey().get())
                    .build())) {
          op.getValue().updateRepo(ctx);
        }
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

  private void fireRefChangeEvent() {
    if (batchRefUpdate != null) {
      gitRefUpdated.fire(project, batchRefUpdate, getAccount().orElse(null));
    }
  }

  private void fireAttentionSetUpdateEvents(PostUpdateContext ctx) {
    for (ProjectChangeKey key : attentionSetUpdates.keySet()) {
      ChangeData change = ctx.getChangeData(key.projectName(), key.changeId());
      AccountState account = getAccount().orElse(null);
      for (AttentionSetUpdate update : attentionSetUpdates.get(key)) {
        attentionSetObserver.fire(change, account, update, ctx.getWhen());
      }
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

    void execute() throws IOException {
      BatchUpdate.this.batchRefUpdate = manager.execute(dryrun);
      BatchUpdate.this.executed = manager.isExecuted();
      BatchUpdate.this.attentionSetUpdates = manager.attentionSetUpdates();
    }

    boolean requiresReindex() {
      // We do not need to reindex changes if there are no ref updates, or if updated refs
      // are all draft comment refs (since draft fields are not stored in the change index).
      BatchRefUpdate bru = BatchUpdate.this.batchRefUpdate;
      return !(bru == null
          || bru.getCommands().isEmpty()
          || bru.getCommands().stream()
              .allMatch(cmd -> RefNames.isRefsDraftsComments(cmd.getRefName())));
    }

    ImmutableList<ListenableFuture<ChangeData>> startIndexFutures() {
      if (dryrun) {
        return ImmutableList.of();
      }
      logDebug("Reindexing %d changes", results.size());
      ImmutableList.Builder<ListenableFuture<ChangeData>> indexFutures =
          ImmutableList.builderWithExpectedSize(results.size());
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
      return indexFutures.build();
    }
  }

  private ChangesHandle executeChangeOps(
      ImmutableList<BatchUpdateListener> batchUpdateListeners, boolean dryrun) throws Exception {
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
                .setBatchUpdateListeners(batchUpdateListeners)
                .setChangeRepo(
                    repo, repoView.getRevWalk(), repoView.getInserter(), repoView.getCommands()),
            dryrun);
    if (user.isIdentifiedUser()) {
      handle.manager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, zoneId));
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
        try (TraceContext.TraceTimer ignored =
            TraceContext.newTimer(
                op.getClass().getSimpleName() + "#updateChange",
                Metadata.builder().projectName(project.get()).changeId(id.get()).build())) {
          dirty |= op.updateChange(ctx);
        }
      }
      if (!dirty) {
        logDebug("No ops reported dirty, short-circuiting");
        handle.setResult(id, ChangeResult.SKIPPED);
        continue;
      }
      ctx.defaultUpdates.values().forEach(handle.manager::add);
      ctx.distinctUpdates.values().forEach(handle.manager::add);
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

  private ChangeContextImpl newChangeContext(Change.Id id) {
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

  private void executePostOps(Map<Change.Id, ChangeData> changeDatas) throws Exception {
    PostUpdateContextImpl ctx = new PostUpdateContextImpl(changeDatas);
    for (BatchUpdateOp op : ops.values()) {
      try (TraceContext.TraceTimer ignored =
          TraceContext.newTimer(op.getClass().getSimpleName() + "#postUpdate", Metadata.empty())) {
        op.postUpdate(ctx);
      }
    }

    for (RepoOnlyOp op : repoOnlyOps) {
      try (TraceContext.TraceTimer ignored =
          TraceContext.newTimer(op.getClass().getSimpleName() + "#postUpdate", Metadata.empty())) {
        op.postUpdate(ctx);
      }
    }
    try (TraceContext.TraceTimer ignored =
        TraceContext.newTimer("fireAttentionSetUpdates#postUpdate", Metadata.empty())) {
      fireAttentionSetUpdateEvents(ctx);
    }
  }

  private static void logDebug(String msg) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log("%s", msg);
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
