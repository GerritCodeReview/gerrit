// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesCacheImpl.class);

  private static final String CACHE_NAME = "stars";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME,
            CacheKey.class,
            new TypeLiteral<HashBasedTable<Account.Id, Change.Id,
                HashSet<String>>>() {})
          .maximumWeight(1)
          .loader(Loader.class);
        DynamicItem.bind(binder(), StarredChangesCache.class)
          .to(StarredChangesCacheImpl.class);
      }
    };
  }

  private final LoadingCache<CacheKey,
      HashBasedTable<Account.Id, Change.Id, HashSet<String>>> cache;
  private final Lock cacheLock;

  @Inject
  StarredChangesCacheImpl(
      @Named(CACHE_NAME) LoadingCache<CacheKey,
          HashBasedTable<Account.Id, Change.Id, HashSet<String>>> cache) {
    this.cache = cache;
    this.cacheLock = new ReentrantLock(true /* fair */);
  }

  @Override
  public boolean isStarred(Account.Id accountId, Change.Id changeId,
      String label) {
    return getLabels(accountId, changeId).contains(label);
  }

  @Override
  public ImmutableSet<String> getLabels(Account.Id accountId,
      Change.Id changeId) {
    try {
      HashSet<String> labels = all().get(accountId, changeId);
      if (labels != null) {
        return ImmutableSet.copyOf(labels);
      } else {
        return ImmutableSet.of();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars for change %d by account %d",
          changeId.get(), accountId.get()), e);
      return ImmutableSet.of();
    }
  }

  @Override
  public Iterable<Change.Id> byAccount(Account.Id accountId,
      final String label) {
    try {
      Map<Change.Id, HashSet<String>> byAccount = all().rowMap().get(accountId);
      if (byAccount != null) {
        return Maps.filterEntries(byAccount,
            new Predicate<Map.Entry<Change.Id, HashSet<String>>>() {
              @Override
              public boolean apply(Map.Entry<Change.Id, HashSet<String>> e) {
                return e.getValue().contains(label);
              }
            }).keySet();
      } else {
        return Collections.emptySet();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars by account %d",
          accountId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public ImmutableMultimap<Change.Id, String> byAccount(Account.Id accountId) {
    try {
      Map<Change.Id, HashSet<String>> byAccount = all().rowMap().get(accountId);
      if (byAccount != null) {
        ImmutableMultimap.Builder<Change.Id, String> b =
            ImmutableMultimap.builder();
        for (Map.Entry<Change.Id, HashSet<String>> e : byAccount.entrySet()) {
          b.putAll(e.getKey(), e.getValue());
        }
        return b.build();
      } else {
        return ImmutableMultimap.of();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars by account %d",
          accountId.get()), e);
      return ImmutableMultimap.of();
    }
  }

  @Override
  public Iterable<Account.Id> byChange(Change.Id changeId, final String label) {
    try {
      Map<Account.Id, HashSet<String>> byChange =
          all().columnMap().get(changeId);
      if (byChange != null) {
        return Maps.filterEntries(byChange,
            new Predicate<Map.Entry<Account.Id, HashSet<String>>>() {
              @Override
              public boolean apply(Map.Entry<Account.Id, HashSet<String>> e) {
                return e.getValue().contains(label);
              }
            }).keySet();
      } else {
        return Collections.emptySet();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars for change %d",
          changeId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public ImmutableMultimap<Account.Id, String> byChange(Change.Id changeId) {
    try {
      Map<Account.Id, HashSet<String>> byChange =
          all().columnMap().get(changeId);
      if (byChange != null) {
        ImmutableMultimap.Builder<Account.Id, String> b =
            ImmutableMultimap.builder();
        for (Map.Entry<Account.Id, HashSet<String>> e : byChange.entrySet()) {
          b.putAll(e.getKey(), e.getValue());
        }
        return b.build();
      } else {
        return ImmutableMultimap.of();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars for change %d",
          changeId.get()), e);
      return ImmutableMultimap.of();
    }
  }

  private HashBasedTable<Account.Id, Change.Id, HashSet<String>> all()
      throws ExecutionException {
    return cache.get(CacheKey.ALL);
  }

  @Override
  public ImmutableSet<String> star(final Account.Id accountId,
      final Change.Id changeId, final String... labelList) {
    return new CacheUpdateOp<ImmutableSet<String>>() {
      @Override
      ImmutableSet<String> op(
          HashBasedTable<Account.Id, Change.Id, HashSet<String>> t) {
        HashSet<String> labels = t.get(accountId, changeId);
        if (labels == null) {
          labels = new HashSet<>();
          t.put(accountId, changeId, labels);
        }
        labels.addAll(Arrays.asList(labelList));
        return ImmutableSet.copyOf(labels);
      }
    }.run();
  }

  @Override
  public ImmutableSet<String> unstar(final Account.Id accountId,
      final Change.Id changeId, final String... labelList) {
    return new CacheUpdateOp<ImmutableSet<String>>() {
      @Override
      ImmutableSet<String> op(
          HashBasedTable<Account.Id, Change.Id, HashSet<String>> t) {
        HashSet<String> labels = t.get(accountId, changeId);
        if (labels == null) {
          return ImmutableSet.of();
        }
        labels.removeAll(Arrays.asList(labelList));
        if (labels.isEmpty()) {
          t.remove(accountId, changeId);
          return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(labels);
      }
    }.run();
  }

  @Override
  public Iterable<Account.Id> unstarAll(final Change.Id changeId) {
    return new CacheUpdateOp<Iterable<Account.Id>>() {
      @Override
      Iterable<Account.Id> op(
          HashBasedTable<Account.Id, Change.Id, HashSet<String>> t) {
        Map<Account.Id, HashSet<String>> removed =
            t.columnMap().remove(changeId);
        if (removed != null) {
          return removed.keySet();
        } else {
          return Collections.emptySet();
        }
      }
    }.run();
  }

  private abstract class CacheUpdateOp<T> {
    T run() {
      T result = null;
      cacheLock.lock();
      try {
        HashBasedTable<Account.Id, Change.Id, HashSet<String>> t =
            HashBasedTable.create(cache.get(CacheKey.ALL));
        result = op(t);
        cache.put(CacheKey.ALL, t);
      } catch (ExecutionException e) {
        log.warn("Cannot list starred changes", e);
      } finally {
        cacheLock.unlock();
      }
      return result;
    }

    abstract T op(HashBasedTable<Account.Id, Change.Id, HashSet<String>> t);
  }

  private static class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    static final CacheKey ALL = new CacheKey();

    private CacheKey() {
    }
  }

  private static class Loader extends CacheLoader<CacheKey,
      HashBasedTable<Account.Id, Change.Id, HashSet<String>>> {
    private final NotesMigration migration;
    private final Provider<ReviewDb> dbProvider;
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;

    @Inject
    Loader(
        NotesMigration migration,
        Provider<ReviewDb> dbProvider,
        GitRepositoryManager repoManager,
        AllUsersName allUsers) {
      this.migration = migration;
      this.dbProvider = dbProvider;
      this.repoManager = repoManager;
      this.allUsers = allUsers;
    }

    @Override
    public HashBasedTable<Account.Id, Change.Id, HashSet<String>>
        load(CacheKey key) throws Exception {
      HashBasedTable<Account.Id, Change.Id, HashSet<String>> t =
          HashBasedTable.create();
      if (!migration.readChanges()) {
        for (StarredChange starredChange :
            dbProvider.get().starredChanges().all()) {
          t.put(
              starredChange.getAccountId(),
              starredChange.getChangeId(),
              Sets.newHashSet(StarredChangesCache.DEFAULT_LABEL));
        }
      } else {
        try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
          for (String refPart : repo.getRefDatabase()
              .getRefs(RefNames.REFS_STARRED_CHANGES).keySet()) {
            t.put(
                Account.Id.fromRefPart(refPart),
                Change.Id.fromRefPart(refPart),
                new HashSet<>(StarredChangesUtil.readLabels(repo,
                    RefNames.REFS_STARRED_CHANGES + refPart)));
          }
        }
      }
      return t;
    }
  }
}
