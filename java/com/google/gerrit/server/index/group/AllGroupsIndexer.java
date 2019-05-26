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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsNoteDbConsistencyChecker;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

@Singleton
public class AllGroupsIndexer extends SiteIndexer<AccountGroup.UUID, InternalGroup, GroupIndex> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ListeningExecutorService executor;
  private final GroupCache groupCache;
  private final Groups groups;

  @Inject
  AllGroupsIndexer(
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      GroupCache groupCache,
      Groups groups) {
    this.executor = executor;
    this.groupCache = groupCache;
    this.groups = groups;
  }

  @Override
  public SiteIndexer.Result indexAll(GroupIndex index) {
    ProgressMonitor progress = new TextProgressMonitor(newPrintWriter(progressOut));
    progress.start(2);
    Stopwatch sw = Stopwatch.createStarted();
    List<AccountGroup.UUID> uuids;
    try {
      uuids = collectGroups(progress);
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Error collecting groups");
      return new SiteIndexer.Result(sw, false, 0, 0);
    }
    return reindexGroups(index, uuids, progress);
  }

  private SiteIndexer.Result reindexGroups(
      GroupIndex index, List<AccountGroup.UUID> uuids, ProgressMonitor progress) {
    progress.beginTask("Reindexing groups", uuids.size());
    List<ListenableFuture<?>> futures = new ArrayList<>(uuids.size());
    AtomicBoolean ok = new AtomicBoolean(true);
    AtomicInteger done = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    Stopwatch sw = Stopwatch.createStarted();
    for (AccountGroup.UUID uuid : uuids) {
      String desc = "group " + uuid;
      ListenableFuture<?> future =
          executor.submit(
              () -> {
                try {
                  groupCache.evict(uuid);
                  Optional<InternalGroup> internalGroup = groupCache.get(uuid);
                  if (internalGroup.isPresent()) {
                    index.replace(internalGroup.get());
                  } else {
                    index.delete(uuid);

                    // The UUID here is read from group name notes. If it fails to load from group
                    // cache, there exists an inconsistency.
                    GroupsNoteDbConsistencyChecker.logFailToLoadFromGroupRefAsWarning(uuid);
                  }
                  verboseWriter.println("Reindexed " + desc);
                  done.incrementAndGet();
                } catch (Exception e) {
                  failed.incrementAndGet();
                  throw e;
                }
                return null;
              });
      addErrorListener(future, desc, progress, ok);
      futures.add(future);
    }

    try {
      Futures.successfulAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      logger.atSevere().withCause(e).log("Error waiting on group futures");
      return new SiteIndexer.Result(sw, false, 0, 0);
    }

    progress.endTask();
    return new SiteIndexer.Result(sw, ok.get(), done.get(), failed.get());
  }

  private List<AccountGroup.UUID> collectGroups(ProgressMonitor progress)
      throws IOException, ConfigInvalidException {
    progress.beginTask("Collecting groups", ProgressMonitor.UNKNOWN);
    try {
      return groups.getAllGroupReferences().map(GroupReference::getUUID).collect(toImmutableList());
    } finally {
      progress.endTask();
    }
  }
}
