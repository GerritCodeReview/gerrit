// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.QueueProvider.QueueType;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Config;

/**
 * Listener for ref update events that reindexes entities in case the updated Git reference was used
 * to compute contents of an index document.
 *
 * <p>Reindexes any open changes that has a destination branch that was updated to ensure that
 * 'mergeable' is still current.
 *
 * <p>Will reindex accounts when the account's NoteDb ref changes.
 */
public class ReindexAfterRefUpdate implements GitBatchRefUpdateListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final OneOffRequestContext requestContext;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeIndexer.Factory indexerFactory;
  private final ChangeIndexCollection indexes;
  private final AllUsersName allUsersName;
  private final Provider<AccountIndexer> indexer;
  private final ListeningExecutorService executor;
  private final boolean enabled;

  @Inject
  ReindexAfterRefUpdate(
      @GerritServerConfig Config cfg,
      OneOffRequestContext requestContext,
      Provider<InternalChangeQuery> queryProvider,
      ChangeIndexer.Factory indexerFactory,
      ChangeIndexCollection indexes,
      AllUsersName allUsersName,
      Provider<AccountIndexer> indexer,
      @IndexExecutor(QueueType.BATCH) ListeningExecutorService executor) {
    this.requestContext = requestContext;
    this.queryProvider = queryProvider;
    this.indexerFactory = indexerFactory;
    this.indexes = indexes;
    this.allUsersName = allUsersName;
    this.indexer = indexer;
    this.executor = executor;
    this.enabled = MergeabilityComputationBehavior.fromConfig(cfg).includeInIndex();
  }

  @Override
  public void onGitBatchRefUpdate(GitBatchRefUpdateListener.Event event) {
    if (allUsersName.get().equals(event.getProjectName())) {
      for (UpdatedRef ref : event.getUpdatedRefs()) {
        if (!RefNames.REFS_CONFIG.equals(ref.getRefName())) {
          if (ref.getRefName().startsWith(RefNames.REFS_STARRED_CHANGES)) {
            break;
          }
          Account.Id accountId = Account.Id.fromRef(ref.getRefName());
          if (accountId != null) {
            indexer.get().index(accountId);
          }
        }
      }
      // The update is in All-Users and not on refs/meta/config. So it's not a change. Return early.
      return;
    }

    for (UpdatedRef ref : event.getUpdatedRefs()) {
      if (!enabled
          || ref.getRefName().startsWith(RefNames.REFS_CHANGES)
          || ref.getRefName().startsWith(RefNames.REFS_DRAFT_COMMENTS)
          || ref.getRefName().startsWith(RefNames.REFS_USERS)) {
        continue;
      }
      Futures.addCallback(
          executor.submit(new GetChanges(event.getProjectName(), ref)),
          new FutureCallback<List<Change>>() {
            @Override
            public void onSuccess(List<Change> changes) {
              for (Change c : changes) {
                @SuppressWarnings("unused")
                Future<?> possiblyIgnoredError =
                    indexerFactory.create(executor, indexes).indexAsync(c.getProject(), c.getId());
              }
            }

            @Override
            public void onFailure(Throwable ignored) {
              // Logged by {@link GetChanges#call()}.
            }
          },
          directExecutor());
    }
  }

  private abstract class Task<V> implements Callable<V> {
    protected UpdatedRef updatedRef;

    protected Task(UpdatedRef updatedRef) {
      this.updatedRef = updatedRef;
    }

    @Override
    public final V call() throws Exception {
      try (ManualRequestContext ctx = requestContext.open()) {
        return impl(ctx);
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Failed to reindex changes after %s", updatedRef);
        throw e;
      }
    }

    protected abstract V impl(RequestContext ctx) throws Exception;

    @SuppressWarnings("unused")
    protected abstract void remove();
  }

  private class GetChanges extends Task<List<Change>> {
    protected String projectName;

    private GetChanges(String projectName, UpdatedRef updatedRef) {
      super(updatedRef);
      this.projectName = projectName;
    }

    @Override
    protected List<Change> impl(RequestContext ctx) {
      String ref = updatedRef.getRefName();
      Project.NameKey project = Project.nameKey(projectName);
      if (ref.equals(RefNames.REFS_CONFIG)) {
        return asChanges(queryProvider.get().byProjectOpen(project));
      }
      return asChanges(queryProvider.get().byBranchNew(BranchNameKey.create(project, ref)));
    }

    @Override
    public String toString() {
      return "Get changes to reindex caused by "
          + updatedRef.getRefName()
          + " update of project "
          + projectName;
    }

    @Override
    protected void remove() {}
  }
}
