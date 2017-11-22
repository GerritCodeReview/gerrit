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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexUtils;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Config;

public class GroupIndexerImpl implements GroupIndexer {
  public interface Factory {
    GroupIndexerImpl create(GroupIndexCollection indexes);

    GroupIndexerImpl create(@Nullable GroupIndex index);
  }

  private final GroupCache groupCache;
  private final DynamicSet<GroupIndexedListener> indexedListener;
  private final StalenessChecker stalenessChecker;
  private final ListeningExecutorService batchExecutor;
  private final boolean autoReindexIfStale;
  @Nullable private final GroupIndexCollection indexes;
  @Nullable private final GroupIndex index;

  @AssistedInject
  GroupIndexerImpl(
      GroupCache groupCache,
      DynamicSet<GroupIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @GerritServerConfig Config config,
      @Assisted GroupIndexCollection indexes) {
    this.groupCache = groupCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(config);
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  GroupIndexerImpl(
      GroupCache groupCache,
      DynamicSet<GroupIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @GerritServerConfig Config config,
      @Assisted @Nullable GroupIndex index) {
    this.groupCache = groupCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(config);
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(AccountGroup.UUID uuid) throws IOException {
    for (Index<AccountGroup.UUID, InternalGroup> i : getWriteIndexes()) {
      Optional<InternalGroup> internalGroup = groupCache.get(uuid);
      if (internalGroup.isPresent()) {
        i.replace(internalGroup.get());
      } else {
        i.delete(uuid);
      }
    }
    fireGroupIndexedEvent(uuid.get());
    autoReindexIfStale(uuid);
  }

  private static boolean autoReindexIfStale(Config cfg) {
    return cfg.getBoolean("index", null, "autoReindexIfStale", true);
  }

  private void autoReindexIfStale(AccountGroup.UUID uuid) {
    if (autoReindexIfStale) {
      // Don't retry indefinitely; if this fails the group will be stale.
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError = reindexIfStale(uuid);
    }
  }

  /**
   * Asynchronously check if a group is stale, and reindex if it is.
   *
   * <p>Always run on the batch executor, even if this indexer instance is configured to use a
   * different executor.
   *
   * @param uuid the unique identifier of the group.
   * @return future for reindexing the group; returns true if the group was stale.
   */
  @SuppressWarnings("deprecation")
  public com.google.common.util.concurrent.CheckedFuture<Boolean, IOException> reindexIfStale(
      AccountGroup.UUID uuid) {
    Callable<Boolean> task =
        () -> {
          if (stalenessChecker.isStale(uuid)) {
            index(uuid);
            return true;
          }
          return false;
        };

    return Futures.makeChecked(
        Futures.nonCancellationPropagating(batchExecutor.submit(task)), IndexUtils.MAPPER);
  }

  private void fireGroupIndexedEvent(String uuid) {
    for (GroupIndexedListener listener : indexedListener) {
      listener.onGroupIndexed(uuid);
    }
  }

  private Collection<GroupIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.of();
  }
}
