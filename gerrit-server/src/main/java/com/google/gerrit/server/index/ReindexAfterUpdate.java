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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.QueueProvider.QueueType;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

public class ReindexAfterUpdate implements GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory
      .getLogger(ReindexAfterUpdate.class);

  private final ThreadLocalRequestContext tl;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeIndexer.Factory indexerFactory;
  private final IndexCollection indexes;
  private final ListeningExecutorService executor;

  @Inject
  ReindexAfterUpdate(
      ThreadLocalRequestContext tl,
      SchemaFactory<ReviewDb> schemaFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeIndexer.Factory indexerFactory,
      IndexCollection indexes,
      @IndexExecutor(QueueType.BATCH) ListeningExecutorService executor) {
    this.tl = tl;
    this.schemaFactory = schemaFactory;
    this.userFactory = userFactory;
    this.indexerFactory = indexerFactory;
    this.indexes = indexes;
    this.executor = executor;
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    Futures.transform(
        executor.submit(new GetChanges(event)),
        new AsyncFunction<List<Change>, List<Void>>() {
          @Override
          public ListenableFuture<List<Void>> apply(List<Change> changes) {
            List<ListenableFuture<Void>> result =
                Lists.newArrayListWithCapacity(changes.size());
            for (Change c : changes) {
              result.add(executor.submit(new Index(c)));
            }
            return Futures.allAsList(result);
          }
        });
  }

  private abstract class Task<V> implements Callable<V> {
    protected ReviewDb db;

    @Override
    public final V call() throws Exception {
      try {
        db = schemaFactory.open();
        return impl();
      } catch (Exception e) {
        log.error("Failed to reindex changes after ref update", e);
        throw e;
      } finally {
        if (db != null) {
          db.close();
        }
      }
    }

    protected abstract V impl() throws Exception;
  }

  private class GetChanges extends Task<List<Change>> {
    private final Event event;

    private GetChanges(Event event) {
      this.event = event;
    }

    @Override
    protected List<Change> impl() throws OrmException {
      String ref = event.getRefName();
      Project.NameKey project = new Project.NameKey(event.getProjectName());
      if (ref.equals(RefNames.REFS_CONFIG)) {
        return db.changes().byProjectOpenAll(project).toList();
      } else {
        return db.changes().byBranchOpenAll(new Branch.NameKey(project, ref))
            .toList();
      }
    }
  }

  private class Index extends Task<Void> {
    private final Change change;

    Index(Change change) {
      this.change = change;
    }

    @Override
    protected Void impl() throws IOException {
      RequestContext context = new RequestContext() {
        @Override
        public CurrentUser getCurrentUser() {
          return userFactory.create(change.getOwner());
        }

        @Override
        public Provider<ReviewDb> getReviewDbProvider() {
          return Providers.of(db);
        }
      };
      RequestContext old = tl.setContext(context);
      try {
        indexerFactory.create(executor, indexes).index(db, change);
        return null;
      } finally {
        tl.setContext(old);
      }
    }
  }
}
