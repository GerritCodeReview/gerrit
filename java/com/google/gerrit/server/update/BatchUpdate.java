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
import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Helper for a set of updates that should be applied for a site.
 *
 * <p>An update operation can be divided into three phases:
 *
 * <ol>
 *   <li>Git reference updates
 *   <li>Database updates
 *   <li>Post-update steps
 *   <li>
 * </ol>
 *
 * A single conceptual operation, such as a REST API call or a merge operation, may make multiple
 * changes at each step, which all need to be serialized relative to each other. Moreover, for
 * consistency, <em>all</em> git ref updates must be performed before <em>any</em> database updates,
 * since database updates might refer to newly-created patch set refs. And all post-update steps,
 * such as hooks, should run only after all storage mutations have completed.
 *
 * <p>Depending on the backend used, each step might support batching, for example in a {@code
 * BatchRefUpdate} or one or more database transactions. All operations in one phase must complete
 * successfully before proceeding to the next phase.
 */
public abstract class BatchUpdate implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(ReviewDbBatchUpdate.AssistedFactory.class);
        factory(NoteDbBatchUpdate.AssistedFactory.class);
      }
    };
  }

  @Singleton
  public static class Factory {
    private final NotesMigration migration;
    private final ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory;
    private final NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory;

    // TODO(dborowitz): Make this non-injectable to force all callers to use RetryHelper.
    @Inject
    Factory(
        NotesMigration migration,
        ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
        NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory) {
      this.migration = migration;
      this.reviewDbBatchUpdateFactory = reviewDbBatchUpdateFactory;
      this.noteDbBatchUpdateFactory = noteDbBatchUpdateFactory;
    }

    public BatchUpdate create(
        ReviewDb db, Project.NameKey project, CurrentUser user, Timestamp when) {
      if (migration.disableChangeReviewDb()) {
        return noteDbBatchUpdateFactory.create(db, project, user, when);
      }
      return reviewDbBatchUpdateFactory.create(db, project, user, when);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void execute(
        Collection<BatchUpdate> updates, BatchUpdateListener listener, boolean dryRun)
        throws UpdateException, RestApiException {
      requireNonNull(listener);
      checkDifferentProject(updates);
      // It's safe to downcast all members of the input collection in this case, because the only
      // way a caller could have gotten any BatchUpdates in the first place is to call the create
      // method above, which always returns instances of the type we expect. Just to be safe,
      // copy them into an ImmutableList so there is no chance the callee can pollute the input
      // collection.
      if (migration.disableChangeReviewDb()) {
        ImmutableList<NoteDbBatchUpdate> noteDbUpdates =
            (ImmutableList) ImmutableList.copyOf(updates);
        NoteDbBatchUpdate.execute(noteDbUpdates, listener, dryRun);
      } else {
        ImmutableList<ReviewDbBatchUpdate> reviewDbUpdates =
            (ImmutableList) ImmutableList.copyOf(updates);
        ReviewDbBatchUpdate.execute(reviewDbUpdates, listener, dryRun);
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
  }

  static Order getOrder(Collection<? extends BatchUpdate> updates, BatchUpdateListener listener) {
    Order o = null;
    for (BatchUpdate u : updates) {
      if (o == null) {
        o = u.order;
      } else if (u.order != o) {
        throw new IllegalArgumentException("cannot mix execution orders");
      }
    }
    if (o != Order.REPO_BEFORE_DB) {
      checkArgument(
          listener == BatchUpdateListener.NONE,
          "BatchUpdateListener not supported for order %s",
          o);
    }
    return o;
  }

  static boolean getUpdateChangesInParallel(Collection<? extends BatchUpdate> updates) {
    checkArgument(!updates.isEmpty());
    Boolean p = null;
    for (BatchUpdate u : updates) {
      if (p == null) {
        p = u.updateChangesInParallel;
      } else if (u.updateChangesInParallel != p) {
        throw new IllegalArgumentException("cannot mix parallel and non-parallel operations");
      }
    }
    // Properly implementing this would involve hoisting the parallel loop up
    // even further. As of this writing, the only user is ReceiveCommits,
    // which only executes a single BatchUpdate at a time. So bail for now.
    checkArgument(
        !p || updates.size() <= 1,
        "cannot execute ChangeOps in parallel with more than 1 BatchUpdate");
    return p;
  }

  static void wrapAndThrowException(Exception e) throws UpdateException, RestApiException {
    Throwables.throwIfUnchecked(e);

    // Propagate REST API exceptions thrown by operations; they commonly throw exceptions like
    // ResourceConflictException to indicate an atomic update failure.
    Throwables.throwIfInstanceOf(e, UpdateException.class);
    Throwables.throwIfInstanceOf(e, RestApiException.class);

    // Convert other common non-REST exception types with user-visible messages to corresponding
    // REST exception types
    if (e instanceof InvalidChangeOperationException) {
      throw new ResourceConflictException(e.getMessage(), e);
    } else if (e instanceof NoSuchChangeException
        || e instanceof NoSuchRefException
        || e instanceof NoSuchProjectException) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    }

    // Otherwise, wrap in a generic UpdateException, which does not include a user-visible message.
    throw new UpdateException(e);
  }

  protected GitRepositoryManager repoManager;

  protected final Project.NameKey project;
  protected final CurrentUser user;
  protected final Timestamp when;
  protected final TimeZone tz;

  protected final ListMultimap<Change.Id, BatchUpdateOp> ops =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  protected final Map<Change.Id, Change> newChanges = new HashMap<>();
  protected final List<RepoOnlyOp> repoOnlyOps = new ArrayList<>();

  protected RepoView repoView;
  protected BatchRefUpdate batchRefUpdate;
  protected Order order;
  protected OnSubmitValidators onSubmitValidators;
  protected PushCertificate pushCert;
  protected String refLogMessage;

  private boolean updateChangesInParallel;

  protected BatchUpdate(
      GitRepositoryManager repoManager,
      PersonIdent serverIdent,
      Project.NameKey project,
      CurrentUser user,
      Timestamp when) {
    this.repoManager = repoManager;
    this.project = project;
    this.user = user;
    this.when = when;
    tz = serverIdent.getTimeZone();
    order = Order.REPO_BEFORE_DB;
  }

  @Override
  public void close() {
    if (repoView != null) {
      repoView.close();
    }
  }

  public abstract void execute(BatchUpdateListener listener)
      throws UpdateException, RestApiException;

  public void execute() throws UpdateException, RestApiException {
    execute(BatchUpdateListener.NONE);
  }

  protected abstract Context newContext();

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

  public BatchUpdate setOrder(Order order) {
    this.order = order;
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

  /**
   * Execute {@link BatchUpdateOp#updateChange(ChangeContext)} in parallel for each change.
   *
   * <p>This improves performance of writing to multiple changes in separate ReviewDb transactions.
   * When only NoteDb is used, updates to all changes are written in a single batch ref update, so
   * parallelization is not used and this option is ignored.
   */
  public BatchUpdate updateChangesInParallel() {
    this.updateChangesInParallel = true;
    return this;
  }

  protected void initRepository() throws IOException {
    if (repoView == null) {
      repoView = new RepoView(repoManager, project);
    }
  }

  protected RepoView getRepoView() throws IOException {
    initRepository();
    return repoView;
  }

  protected CurrentUser getUser() {
    return user;
  }

  protected Optional<AccountState> getAccount() {
    return user.isIdentifiedUser()
        ? Optional.of(user.asIdentifiedUser().state())
        : Optional.empty();
  }

  protected RevWalk getRevWalk() throws IOException {
    initRepository();
    return repoView.getRevWalk();
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
    Context ctx = newContext();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
    return this;
  }

  protected static void logDebug(String msg, Throwable t) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().withCause(t).log("%s", msg);
    }
  }

  protected static void logDebug(String msg) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg);
    }
  }

  protected static void logDebug(String msg, @Nullable Object arg) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg, arg);
    }
  }

  protected static void logDebug(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg, arg1, arg2);
    }
  }

  protected static void logDebug(
      String msg, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (RequestId.isSet()) {
      logger.atFine().log(msg, arg1, arg2, arg3);
    }
  }
}
