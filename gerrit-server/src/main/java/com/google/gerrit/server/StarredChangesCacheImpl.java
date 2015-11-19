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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Singleton
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesCacheImpl.class);

  public static Module module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        bind(StarredChangesChacheProvider.class).in(SINGLETON);
        listener().to(StarredChangesChacheProvider.class);
      }
    };
  }

  public static class StarredChangesChacheProvider
      implements Provider<StarredChangesCache>, LifecycleListener {

    private final DynamicItem<StarredChangesCache> starredChangesCache;
    private final Injector sysInjector;

    private StarredChangesCacheImpl defaultStarredChangesCache;
    private Future<?> asyncDefaultCacheLoader;

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
        defaultStarredChangesCache = createDefaultCache();
        defaultStarredChangesCache.load();
      } else {
        if (asyncDefaultCacheLoader != null) {
          if (!asyncDefaultCacheLoader.isDone()) {
            try {
              // wait until loading is done
              asyncDefaultCacheLoader.get();
            } catch (InterruptedException | ExecutionException e) {
              log.error("Failed to load starred changes cache", e);
            }
          }
          asyncDefaultCacheLoader = null;
        }
      }
      return defaultStarredChangesCache;
    }

    private StarredChangesCacheImpl createDefaultCache() {
      return sysInjector.createChildInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(StarredChangesCacheImpl.class);
        }
      }).getInstance(StarredChangesCacheImpl.class);
    }

    @Override
    public void start() {
      StarredChangesCache cache = starredChangesCache.get();
      if (cache == null) {
        // no starred-changes cache provided by plugin,
        // create default starred-changes cache and load it asynchronously
        defaultStarredChangesCache = createDefaultCache();
        asyncDefaultCacheLoader = defaultStarredChangesCache.loadAsync();
      }
    }

    @Override
    public void stop() {
    }
  }

  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final WorkQueue workQueue;
  private final HashMultimapPair<Account.Id, Change.Id> cache;

  @Inject
  StarredChangesCacheImpl(
      NotesMigration migration,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      WorkQueue workQueue) {
    this.migration = migration;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.workQueue = workQueue;
    this.cache = HashMultimapPair.create();
  }

  public Future<?> loadAsync() {
    return workQueue.getDefaultQueue().submit(new Runnable() {
      @Override
      public void run() {
        load();
      }
    });
  }

  public void load() {
    try {
      if (!migration.readChanges()) {
        for (StarredChange starredChange :
            dbProvider.get().starredChanges().all()) {
          cache.put(
              starredChange.getAccountId(),
              starredChange.getChangeId());
        }
        return;
      }

      try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
        for (String refPart : repo.getRefDatabase()
            .getRefs(RefNames.REFS_STARRED_CHANGES).keySet()) {
          cache.put(
              Account.Id.fromRefPart(refPart),
              Change.Id.fromRefPart(refPart));
        }
      }
    } catch (OrmException | IOException e) {
      log.error("Failed to load starred changes cache", e);
    }
  }

  @Override
  public boolean isStarred(Account.Id accountId, Change.Id changeId) {
    return cache.contains(accountId, changeId);
  }

  @Override
  public Iterable<Change.Id> byAccount(Account.Id accountId) {
    return cache.getByFirstKey(accountId);
  }

  @Override
  public Iterable<Account.Id> byChange(Change.Id changeId) {
    return cache.getBySecondKey(changeId);
  }

  @Override
  public void star(Account.Id accountId, Change.Id changeId) {
    cache.put(accountId, changeId);
  }

  @Override
  public void unstar(Account.Id accountId, Change.Id changeId) {
    cache.remove(accountId, changeId);
  }

  @Override
  public Iterable<Account.Id> unstarAll(Change.Id changeId) {
    return cache.removeAllBySecondKey(changeId);
  }
}
