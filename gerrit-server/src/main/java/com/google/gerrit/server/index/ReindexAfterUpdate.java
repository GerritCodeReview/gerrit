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

package com.google.gerrit.server.index;

import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.QueueProvider.QueueType;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

public class ReindexAfterUpdate implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory
      .getLogger(ReindexAfterUpdate.class);

  private final OneOffRequestContext requestContext;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeIndexer.Factory indexerFactory;
  private final IndexCollection indexes;
  private final ListeningExecutorService executor;

  @Inject
  ReindexAfterUpdate(
      OneOffRequestContext requestContext,
      Provider<InternalChangeQuery> queryProvider,
      ChangeIndexer.Factory indexerFactory,
      IndexCollection indexes,
      @IndexExecutor(QueueType.BATCH) ListeningExecutorService executor) {
    this.requestContext = requestContext;
    this.queryProvider = queryProvider;
    this.indexerFactory = indexerFactory;
    this.indexes = indexes;
    this.executor = executor;
  }

  @Override
  public void onGitReferenceUpdated(final Event event) {
    if (event.getRefName().startsWith(RefNames.REFS_CHANGES)
        || event.getRefName().startsWith(RefNames.REFS_DRAFT_COMMENTS)
        || event.getRefName().startsWith(RefNames.REFS_USERS)) {
      return;
    }
    Futures.transformAsync(
        executor.submit(new GetChanges(event)),
        new AsyncFunction<List<Change>, List<Void>>() {
          @Override
          public ListenableFuture<List<Void>> apply(List<Change> changes) {
            List<ListenableFuture<Void>> result =
                Lists.newArrayListWithCapacity(changes.size());
            for (Change c : changes) {
              result.add(executor.submit(new Index(event, c.getId())));
            }
            return Futures.allAsList(result);
          }
        });
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
      } else {
        return asChanges(queryProvider.get().byBranchOpen(
            new Branch.NameKey(project, ref)));
      }
    }

    @Override
    public String toString() {
      return "Get changes to reindex caused by " + event.getRefName()
          + " update of project " + event.getProjectName();
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
      Change c = db.changes().get(id);
      indexerFactory.create(executor, indexes).index(db, c);
      return null;
    }

    @Override
    public String toString() {
      return "Index change " + id.get() + " of project "
          + event.getProjectName();
    }
  }
}
