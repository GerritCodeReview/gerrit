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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.util.RequestId;
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
import java.util.TimeZone;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger log = LoggerFactory.getLogger(BatchUpdate.class);

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(ReviewDbBatchUpdate.AssistedFactory.class);
      }
    };
  }

  @Singleton
  public static class Factory {
    private final ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory;

    @Inject
    Factory(ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory) {
      this.reviewDbBatchUpdateFactory = reviewDbBatchUpdateFactory;
    }

    public BatchUpdate create(
        ReviewDb db, Project.NameKey project, CurrentUser user, Timestamp when) {
      return reviewDbBatchUpdateFactory.create(db, project, user, when);
    }

    public void execute(
        Collection<BatchUpdate> updates,
        BatchUpdateListener listener,
        @Nullable RequestId requestId,
        boolean dryRun)
        throws UpdateException, RestApiException {
      // It's safe to downcast all members of the input collection in this case, because the only
      // way a caller could have gotten any BatchUpdates in the first place is to call the create
      // method above, which always returns instances of the type we expect. Just to be safe,
      // copy them into an ImmutableList so there is no chance the callee can pollute the input
      // collection.
      @SuppressWarnings({"rawtypes", "unchecked"})
      ImmutableList<ReviewDbBatchUpdate> reviewDbUpdates =
          (ImmutableList) ImmutableList.copyOf(updates);
      ReviewDbBatchUpdate.execute(reviewDbUpdates, listener, requestId, dryRun);
    }
  }

  protected static Order getOrder(Collection<? extends BatchUpdate> updates) {
    Order o = null;
    for (BatchUpdate u : updates) {
      if (o == null) {
        o = u.order;
      } else if (u.order != o) {
        throw new IllegalArgumentException("cannot mix execution orders");
      }
    }
    return o;
  }

  protected static boolean getUpdateChangesInParallel(Collection<? extends BatchUpdate> updates) {
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

  protected GitRepositoryManager repoManager;

  protected final Project.NameKey project;
  protected final CurrentUser user;
  protected final Timestamp when;
  protected final TimeZone tz;

  protected final ListMultimap<Change.Id, BatchUpdateOp> ops =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  protected final Map<Change.Id, Change> newChanges = new HashMap<>();
  protected final List<RepoOnlyOp> repoOnlyOps = new ArrayList<>();

  protected Repository repo;
  protected ObjectInserter inserter;
  protected RevWalk revWalk;
  protected ChainedReceiveCommands commands;
  protected BatchRefUpdate batchRefUpdate;
  protected Order order;
  protected OnSubmitValidators onSubmitValidators;
  protected RequestId requestId;
  protected String refLogMessage;

  private boolean updateChangesInParallel;
  private boolean closeRepo;

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
    if (closeRepo) {
      revWalk.getObjectReader().close();
      revWalk.close();
      inserter.close();
      repo.close();
    }
  }

  public abstract void execute(BatchUpdateListener listener)
      throws UpdateException, RestApiException;

  public abstract void execute() throws UpdateException, RestApiException;

  protected abstract Context newContext();

  public BatchUpdate setRequestId(RequestId requestId) {
    this.requestId = requestId;
    return this;
  }

  public BatchUpdate setRepository(Repository repo, RevWalk revWalk, ObjectInserter inserter) {
    checkState(this.repo == null, "repo already set");
    closeRepo = false;
    this.repo = checkNotNull(repo, "repo");
    this.revWalk = checkNotNull(revWalk, "revWalk");
    this.inserter = checkNotNull(inserter, "inserter");
    commands = new ChainedReceiveCommands(repo);
    return this;
  }

  public BatchUpdate setRefLogMessage(String refLogMessage) {
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

  /** Execute {@link BatchUpdateOp#updateChange(ChangeContext)} in parallel for each change. */
  public BatchUpdate updateChangesInParallel() {
    this.updateChangesInParallel = true;
    return this;
  }

  protected void initRepository() throws IOException {
    if (repo == null) {
      this.repo = repoManager.openRepository(project);
      closeRepo = true;
      inserter = repo.newObjectInserter();
      revWalk = new RevWalk(inserter.newReader());
      commands = new ChainedReceiveCommands(repo);
    }
  }

  protected CurrentUser getUser() {
    return user;
  }

  protected Repository getRepository() throws IOException {
    initRepository();
    return repo;
  }

  protected RevWalk getRevWalk() throws IOException {
    initRepository();
    return revWalk;
  }

  protected ObjectInserter getObjectInserter() throws IOException {
    initRepository();
    return inserter;
  }

  public Collection<ReceiveCommand> getRefUpdates() {
    return commands.getCommands().values();
  }

  public BatchUpdate addOp(Change.Id id, BatchUpdateOp op) {
    checkArgument(!(op instanceof InsertChangeOp), "use insertChange");
    checkNotNull(op);
    ops.put(id, op);
    return this;
  }

  public BatchUpdate addRepoOnlyOp(RepoOnlyOp op) {
    checkArgument(!(op instanceof BatchUpdateOp), "use addOp()");
    repoOnlyOps.add(op);
    return this;
  }

  public BatchUpdate insertChange(InsertChangeOp op) {
    Context ctx = newContext();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
    return this;
  }

  protected void logDebug(String msg, Throwable t) {
    if (requestId != null && log.isDebugEnabled()) {
      log.debug(requestId + msg, t);
    }
  }

  protected void logDebug(String msg, Object... args) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (requestId != null && log.isDebugEnabled()) {
      log.debug(requestId + msg, args);
    }
  }
}
