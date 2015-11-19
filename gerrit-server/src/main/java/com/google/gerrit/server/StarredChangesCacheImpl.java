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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import java.util.Collections;
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
        persist(CACHE_NAME,
            CacheKey.class,
            new TypeLiteral<HashMultimapPair<Account.Id, Change.Id>>() {})
          .maximumWeight(1)
          .loader(Loader.class);
        DynamicItem.bind(binder(), StarredChangesCache.class)
          .to(StarredChangesCacheImpl.class);
      }
    };
  }

  private final LoadingCache<CacheKey,
      HashMultimapPair<Account.Id, Change.Id>> cache;

  @Inject
  StarredChangesCacheImpl(
      @Named(CACHE_NAME) LoadingCache<CacheKey,
      HashMultimapPair<Account.Id, Change.Id>> cache) {
    this.cache = cache;
  }

  @Override
  public boolean isStarred(Account.Id accountId, Change.Id changeId) {
    try {
      return all().contains(accountId, changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup star for change %d by account %d",
          changeId.get(), accountId.get()), e);
      return false;
    }
  }

  @Override
  public Iterable<Change.Id> byAccount(Account.Id accountId) {
    try {
      return all().getByFirstKey(accountId);
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars by account %d",
          accountId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public Iterable<Account.Id> byChange(Change.Id changeId) {
    try {
      return all().getBySecondKey(changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup stars for change %d",
          changeId.get()), e);
      return Collections.emptySet();
    }
  }

  @Override
  public void star(Account.Id accountId, Change.Id changeId) {
    try {
      all().put(accountId, changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Failed to star change %d by account %d",
          changeId.get(), accountId.get()), e);
    }
  }

  @Override
  public void unstar(final Account.Id accountId, final Change.Id changeId) {
    try {
      all().remove(accountId, changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Failed to unstar change %d by account %d",
          changeId.get(), accountId.get()), e);
    }
  }

  @Override
  public Iterable<Account.Id> unstarAll(final Change.Id changeId) {
    try {
      return all().removeAllBySecondKey(changeId);
    } catch (ExecutionException e) {
      log.warn(String.format("Failed to unstar change %d by all accounts",
          changeId.get()), e);
      return Collections.emptySet();
    }
  }

  private HashMultimapPair<Account.Id, Change.Id> all()
      throws ExecutionException {
    return cache.get(CacheKey.ALL);
  }

  private static class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    static final CacheKey ALL = new CacheKey();

    private CacheKey() {
    }
  }

  private static class Loader extends
      CacheLoader<CacheKey, HashMultimapPair<Account.Id, Change.Id>> {
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
    public HashMultimapPair<Account.Id, Change.Id> load(CacheKey key)
        throws Exception {
      HashMultimapPair<Account.Id, Change.Id> t = HashMultimapPair.create();
      if (!migration.readChanges()) {
        for (StarredChange starredChange :
            dbProvider.get().starredChanges().all()) {
          t.put(
              starredChange.getAccountId(),
              starredChange.getChangeId());
        }
      } else {
        try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
          for (String refPart : repo.getRefDatabase()
              .getRefs(RefNames.REFS_STARRED_CHANGES).keySet()) {
            t.put(
                Account.Id.fromRefPart(refPart),
                Change.Id.fromRefPart(refPart));
          }
        }
      }
      return t;
    }
  }
}
