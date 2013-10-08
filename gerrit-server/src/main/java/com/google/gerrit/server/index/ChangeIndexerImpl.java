// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.util.Providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public class ChangeIndexerImpl extends ChangeIndexer {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeIndexerImpl.class);

  private final IndexCollection indexes;
  private final ChangeIndex index;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ThreadLocalRequestContext context;

  @AssistedInject
  ChangeIndexerImpl(@IndexExecutor ListeningExecutorService executor,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext context,
      @Assisted ChangeIndex index) {
    super(executor);
    this.schemaFactory = schemaFactory;
    this.context = context;
    this.index = index;
    this.indexes = null;
  }

  @AssistedInject
  ChangeIndexerImpl(@IndexExecutor ListeningExecutorService executor,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext context,
      @Assisted IndexCollection indexes) {
    super(executor);
    this.schemaFactory = schemaFactory;
    this.context = context;
    this.index = null;
    this.indexes = indexes;
  }

  @Override
  protected Callable<Void> indexTask(ChangeData cd) {
    return new Task(cd, false);
  }

  @Override
  protected Callable<Void> deleteTask(ChangeData cd) {
    return new Task(cd, true);
  }

  private class Task implements Callable<Void> {
    private final ChangeData cd;
    private final boolean delete;

    private Task(ChangeData cd, boolean delete) {
      this.cd = cd;
      this.delete = delete;
    }

    @Override
    public Void call() throws Exception {
      try {
        final AtomicReference<Provider<ReviewDb>> dbRef =
            Atomics.newReference();
        RequestContext oldCtx = context.setContext(new RequestContext() {
          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            Provider<ReviewDb> db = dbRef.get();
            if (db == null) {
              try {
                db = Providers.of(schemaFactory.open());
              } catch (OrmException e) {
                ProvisionException pe =
                    new ProvisionException("error opening ReviewDb");
                pe.initCause(e);
                throw pe;
              }
              dbRef.set(db);
            }
            return db;
          }

          @Override
          public CurrentUser getCurrentUser() {
            throw new OutOfScopeException("No user during ChangeIndexer");
          }
        });
        try {
          if (indexes != null) {
            for (ChangeIndex i : indexes.getWriteIndexes()) {
              apply(i, cd);
            }
          } else {
            apply(index, cd);
          }
          return null;
        } finally  {
          context.setContext(oldCtx);
          Provider<ReviewDb> db = dbRef.get();
          if (db != null) {
            db.get().close();
          }
        }
      } catch (Exception e) {
        log.error(String.format(
            "Failed to index change %d in %s",
            cd.getId().get(), cd.getChange().getProject().get()), e);
        throw e;
      }
    }

    private void apply(ChangeIndex i, ChangeData cd) throws IOException {
      if (delete) {
        i.delete(cd);
      } else {
        i.replace(cd);
      }
    }

    @Override
    public String toString() {
      return "index-change-" + cd.getId().get();
    }
  }
}
