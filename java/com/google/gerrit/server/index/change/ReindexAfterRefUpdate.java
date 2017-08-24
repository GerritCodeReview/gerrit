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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.QueueProvider.QueueType;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReindexAfterRefUpdate implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory.getLogger(ReindexAfterRefUpdate.class);

  private final OneOffRequestContext requestContext;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeIndexer.Factory indexerFactory;
  private final ChangeIndexCollection indexes;
  private final ChangeNotes.Factory notesFactory;
  private final AllUsersName allUsersName;
  private final AccountCache accountCache;
  private final ListeningExecutorService executor;
  private final boolean enabled;

  @Inject
  ReindexAfterRefUpdate(
      @GerritServerConfig Config cfg,
      OneOffRequestContext requestContext,
      Provider<InternalChangeQuery> queryProvider,
      ChangeIndexer.Factory indexerFactory,
      ChangeIndexCollection indexes,
      ChangeNotes.Factory notesFactory,
      AllUsersName allUsersName,
      AccountCache accountCache,
      @IndexExecutor(QueueType.BATCH) ListeningExecutorService executor) {
    this.requestContext = requestContext;
    this.queryProvider = queryProvider;
    this.indexerFactory = indexerFactory;
    this.indexes = indexes;
    this.notesFactory = notesFactory;
    this.allUsersName = allUsersName;
    this.accountCache = accountCache;
    this.executor = executor;
    this.enabled = cfg.getBoolean("index", null, "reindexAfterRefUpdate", true);
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    if (allUsersName.get().equals(event.getProjectName())) {
      Account.Id accountId = Account.Id.fromRef(event.getRefName());
      if (accountId != null) {
        try {
          accountCache.evict(accountId);
        } catch (IOException e) {
          log.error(String.format("Reindex account %s failed.", accountId), e);
        }
      }
    }

    if (!enabled
        || event.getRefName().startsWith(RefNames.REFS_CHANGES)
        || event.getRefName().startsWith(RefNames.REFS_DRAFT_COMMENTS)
        || event.getRefName().startsWith(RefNames.REFS_USERS)) {
      return;
    }
    Futures.addCallback(
        executor.submit(new GetChanges(event)),
        new FutureCallback<List<Change>>() {
          @Override
          public void onSuccess(List<Change> changes) {
            for (Change c : changes) {
              // Don't retry indefinitely; if this fails changes may be stale.
              @SuppressWarnings("unused")
              Future<?> possiblyIgnoredError = executor.submit(new Index(event, c.getId()));
            }
          }

          @Override
          public void onFailure(Throwable ignored) {
            // Logged by {@link GetChanges#call()}.
          }
        },
        directExecutor());
  }

  private abstract class Task<V> implements Callable<V> {
    protected Event event;

    protected Task(Event event) {
      this.event = event;
    }

    @Override
    public final V call() throws Exception {
      try (ManualRequestContext ctx = requestContext.open()) {
        return impl(ctx);
      } catch (Exception e) {
        log.error("Failed to reindex changes after " + event, e);
        throw e;
      }
    }

    protected abstract V impl(RequestContext ctx) throws Exception;
  }

  private class GetChanges extends Task<List<Change>> {
    private GetChanges(Event event) {
      super(event);
    }

    @Override
    protected List<Change> impl(RequestContext ctx) throws OrmException {
      String ref = event.getRefName();
      Project.NameKey project = new Project.NameKey(event.getProjectName());
      if (ref.equals(RefNames.REFS_CONFIG)) {
        return asChanges(queryProvider.get().byProjectOpen(project));
      }
      return asChanges(queryProvider.get().byBranchNew(new Branch.NameKey(project, ref)));
    }

    @Override
    public String toString() {
      return "Get changes to reindex caused by "
          + event.getRefName()
          + " update of project "
          + event.getProjectName();
    }
  }

  private class Index extends Task<Void> {
    private final Change.Id id;

    Index(Event event, Change.Id id) {
      super(event);
      this.id = id;
    }

    @Override
    protected Void impl(RequestContext ctx) throws OrmException, IOException {
      // Reload change, as some time may have passed since GetChanges.
      ReviewDb db = ctx.getReviewDbProvider().get();
      try {
        Change c =
            notesFactory
                .createChecked(db, new Project.NameKey(event.getProjectName()), id)
                .getChange();
        indexerFactory.create(executor, indexes).index(db, c);
      } catch (NoSuchChangeException e) {
        indexerFactory.create(executor, indexes).delete(id);
      }
      return null;
    }

    @Override
    public String toString() {
      return "Index change " + id.get() + " of project " + event.getProjectName();
    }
  }
}
