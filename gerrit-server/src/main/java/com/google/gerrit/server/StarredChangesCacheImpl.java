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

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
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
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

@Singleton
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesCacheImpl.class);

  private static final String CACHE_NAME = "starred_changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(StarredChangesChacheProvider.class).in(SINGLETON);
      }
    };
  }

  public static class StarredChangesChacheProvider
      implements Provider<StarredChangesCache> {

    private final DynamicItem<StarredChangesCache> starredChangesCache;
    private final Injector sysInjector;

    private StarredChangesCache defaultStarredChangesCache;

    @Inject
    StarredChangesChacheProvider(
        DynamicItem<StarredChangesCache> starredChangesCache,
        Injector sysInjector) {
      this.starredChangesCache = starredChangesCache;
      this.sysInjector = sysInjector;
    }

    @Override
    public StarredChangesCache get() {
      StarredChangesCache cache = starredChangesCache.get();
      if (cache != null) {
        // starred-changes cache provided by plugin
        return cache;
      }

      if (defaultStarredChangesCache == null) {
        // create default starred-changes cache
        defaultStarredChangesCache = new StarredChangesCacheImpl(
            sysInjector.createChildInjector(new CacheModule() {
              @Override
              protected void configure() {
                persist(CACHE_NAME, CacheKey.class,
                    new TypeLiteral<HashBasedBiTable<
                        Account.Id, Change.Id, TreeSet<String>>>() {})
                        .maximumWeight(1).loader(Loader.class);
              }
            }).getInstance(Key.get(
                new TypeLiteral<LoadingCache<CacheKey,
                    HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>>>>() {},
                Names.named(CACHE_NAME))));
      }
      return defaultStarredChangesCache;
    }
  }

  private final LoadingCache<CacheKey,
      HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>>> cache;

  private StarredChangesCacheImpl(
      LoadingCache<CacheKey,
          HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>>> cache) {
    this.cache = cache;
  }

  @Override
  public boolean isStarred(Account.Id accountId, Change.Id changeId,
      String label) {
    return getLabels(accountId, changeId).contains(label);
  }

  @Override
  public ImmutableSortedSet<String> getLabels(Account.Id accountId,
      Change.Id changeId) {
    try {
      TreeSet<String> labels = all().get(accountId, changeId);
      if (labels != null) {
        return ImmutableSortedSet.copyOf(labels);
      } else {
        return ImmutableSortedSet.of();
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars for change %d by account %d",
          changeId.get(), accountId.get()), e);
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public Iterable<Change.Id> byAccount(Account.Id accountId,
      final String label) {
    try {
      return Maps.filterEntries(all().getMapByFirstKey(accountId),
          new Predicate<Map.Entry<Change.Id, TreeSet<String>>>() {
            @Override
            public boolean apply(Map.Entry<Change.Id, TreeSet<String>> e) {
              return e.getValue().contains(label);
            }
          }).keySet();
    } catch (ExecutionException e) {
      log.warn(String.format(
          "Cannot lookup stars by account %d", accountId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public ImmutableMultimap<Change.Id, String> byAccount(Account.Id accountId) {
    try {
      ImmutableMultimap.Builder<Change.Id, String> b =
          ImmutableMultimap.builder();
      for (Map.Entry<Change.Id, TreeSet<String>> entry : all()
          .getMapByFirstKey(accountId).entrySet()) {
        b.putAll(entry.getKey(), entry.getValue());
      }
      return b.build();
    } catch (ExecutionException e) {
      log.warn(String.format(
          "Cannot lookup stars by account %d", accountId.get()), e);
      return ImmutableMultimap.of();
    }
  }

  @Override
  public Iterable<Account.Id> byChange(Change.Id changeId, final String label) {
    try {
      return Maps.filterEntries(all().getMapBySecondKey(changeId),
          new Predicate<Map.Entry<Account.Id, TreeSet<String>>>() {
            @Override
            public boolean apply(Map.Entry<Account.Id, TreeSet<String>> e) {
              return e.getValue().contains(label);
            }
          }).keySet();
    } catch (ExecutionException e) {
      log.warn(String.format(
          "Cannot lookup stars for change %d", changeId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public ImmutableMultimap<Account.Id, String> byChange(Change.Id changeId) {
    try {
      ImmutableMultimap.Builder<Account.Id, String> b =
          ImmutableMultimap.builder();
      for (Map.Entry<Account.Id, TreeSet<String>> entry : all()
          .getMapBySecondKey(changeId).entrySet()) {
        b.putAll(entry.getKey(), entry.getValue());
      }
      return b.build();
    } catch (ExecutionException e) {
      log.warn(String.format(
          "Cannot lookup stars for change %d", changeId.get()), e);
      return ImmutableMultimap.of();
    }
  }

  @Override
  public ImmutableSortedSet<String> star(Account.Id accountId,
      Change.Id changeId, Set<String> labels) {
    try {
      HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>> all = all();
      synchronized (all) {
        TreeSet<String> currentLabels = all.get(accountId, changeId);
        if (currentLabels == null) {
          currentLabels = new TreeSet<>();
          all.put(accountId, changeId, currentLabels);
        }
        currentLabels.addAll(labels);
        return ImmutableSortedSet.copyOf(currentLabels);
      }
    } catch (ExecutionException e) {
      log.warn(String.format("Failed to star change %d by account %d",
          changeId.get(), accountId.get()), e);
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public ImmutableSortedSet<String> unstar(Account.Id accountId,
      Change.Id changeId, Set<String> labels) {
    try {
      HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>> all = all();
      synchronized (all) {
        TreeSet<String> currentLabels = all.get(accountId, changeId);
        if (currentLabels != null) {
          currentLabels.removeAll(labels);
          return ImmutableSortedSet.copyOf(currentLabels);
        } else {
          return ImmutableSortedSet.of();
        }
      }

    } catch (ExecutionException e) {
      log.warn(String.format("Failed to unstar change %d by account %d",
          changeId.get(), accountId.get()), e);
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public Iterable<Account.Id> unstarAll(Change.Id changeId) {
    try {
      return all().removeAllBySecondKey(changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Failed to unstar change %d by all accounts",
          changeId.get()), e);
      return Collections.emptySet();
    }
  }

  private HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>> all()
      throws ExecutionException {
    return cache.get(CacheKey.ALL);
  }

  private static class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    static final CacheKey ALL = new CacheKey();

    private CacheKey() {
    }
  }

  private static class Loader extends CacheLoader<CacheKey,
      HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>>> {
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
    public HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>> load(
        CacheKey key) throws Exception {
      HashBasedBiTable<Account.Id, Change.Id, TreeSet<String>> t =
          HashBasedBiTable.create();
      if (!migration.readChanges()) {
        for (StarredChange starredChange :
            dbProvider.get().starredChanges().all()) {
          t.put(
              starredChange.getAccountId(),
              starredChange.getChangeId(),
              StarredChangesUtil.DEFAULT_LABELS);
        }
      } else {
        try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
          for (String refPart : repo.getRefDatabase()
              .getRefs(RefNames.REFS_STARRED_CHANGES).keySet()) {
            t.put(
                Account.Id.fromRefPart(refPart),
                Change.Id.fromRefPart(refPart),
                StarredChangesUtil.readLabels(repo,
                    RefNames.REFS_STARRED_CHANGES + refPart));
          }
        }
      }
      return t;
    }
  }
}
