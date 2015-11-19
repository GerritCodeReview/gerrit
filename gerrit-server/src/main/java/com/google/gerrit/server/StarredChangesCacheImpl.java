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

@Singleton
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesCacheImpl.class);

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
        defaultStarredChangesCache =
            sysInjector.createChildInjector(new AbstractModule() {
              @Override
              protected void configure() {
                bind(StarredChangesCacheImpl.class);
              }
            }).getInstance(StarredChangesCacheImpl.class);
      }
      return defaultStarredChangesCache;
    }
  }

  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  private final HashMultimapPair<Account.Id, Change.Id> cache;

  @Inject
  StarredChangesCacheImpl(
      NotesMigration migration,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      AllUsersName allUsers) {
    this.migration = migration;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.allUsers = allUsers;

    this.cache = HashMultimapPair.create();
    try {
      load();
    } catch (OrmException | IOException e) {
      log.error("Failed to load starred changes cache", e);
    }
  }

  private void load() throws OrmException, IOException {
    if (!migration.readChanges()) {
      for (StarredChange starredChange :
          dbProvider.get().starredChanges().all()) {
        cache.put(
            starredChange.getAccountId(),
            starredChange.getChangeId());
      }
    } else {
      try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
        for (String refPart : repo.getRefDatabase()
            .getRefs(RefNames.REFS_STARRED_CHANGES).keySet()) {
          cache.put(
              Account.Id.fromRefPart(refPart),
              Change.Id.fromRefPart(refPart));
        }
      }
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
