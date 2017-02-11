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

package com.google.gerrit.server.index.group;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.SiteIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.List;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AllGroupsIndexer extends SiteIndexer<AccountGroup.UUID, AccountGroup, GroupIndex> {
  private static final Logger log = LoggerFactory.getLogger(AllGroupsIndexer.class);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ListeningExecutorService executor;
  private final GroupCache groupCache;

  @Inject
  AllGroupsIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      GroupCache groupCache) {
    this.schemaFactory = schemaFactory;
    this.executor = executor;
    this.groupCache = groupCache;
  }

  @Override
  public SiteIndexer.Result indexAll(GroupIndex index) {
    ProgressMonitor progress = new TextProgressMonitor(new PrintWriter(progressOut));
    progress.start(2);
    Stopwatch sw = Stopwatch.createStarted();
    List<AccountGroup.UUID> uuids;
    try {
      uuids = collectGroups(progress);
    } catch (OrmException e) {
      log.error("Error collecting groups", e);
      return new SiteIndexer.Result(sw, false, 0, 0);
    }
    return reindexGroups(index, uuids, progress);
  }

  private SiteIndexer.Result reindexGroups(
      GroupIndex index, List<AccountGroup.UUID> uuids, ProgressMonitor progress) {
    progress.beginTask("Reindexing groups", uuids.size());
    List<ListenableFuture<?>> futures = new ArrayList<>(uuids.size());
    AtomicBoolean ok = new AtomicBoolean(true);
    final AtomicInteger done = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();
    Stopwatch sw = Stopwatch.createStarted();
    for (final AccountGroup.UUID uuid : uuids) {
      final String desc = "group " + uuid;
      ListenableFuture<?> future =
          executor.submit(
              new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                  try {
                    AccountGroup oldGroup = groupCache.get(uuid);
                    if (oldGroup != null) {
                      groupCache.evict(oldGroup);
                    }
                    index.replace(groupCache.get(uuid));
                    verboseWriter.println("Reindexed " + desc);
                    done.incrementAndGet();
                  } catch (Exception e) {
                    failed.incrementAndGet();
                    throw e;
                  }
                  return null;
                }
              });
      addErrorListener(future, desc, progress, ok);
      futures.add(future);
    }

    try {
      Futures.successfulAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      log.error("Error waiting on group futures", e);
      return new SiteIndexer.Result(sw, false, 0, 0);
    }

    progress.endTask();
    return new SiteIndexer.Result(sw, ok.get(), done.get(), failed.get());
  }

  private List<AccountGroup.UUID> collectGroups(ProgressMonitor progress) throws OrmException {
    progress.beginTask("Collecting groups", ProgressMonitor.UNKNOWN);
    List<AccountGroup.UUID> uuids = new ArrayList<>();
    try (ReviewDb db = schemaFactory.open()) {
      for (AccountGroup group : db.accountGroups().all()) {
        uuids.add(group.getGroupUUID());
      }
    }
    progress.endTask();
    return uuids;
  }
}
