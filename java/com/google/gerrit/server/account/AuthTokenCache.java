// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.AllUsersRepository;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AuthTokenCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "tokens";

  record Key(Account.Id accountId, ObjectId revision) {
    Key {
      checkNotNull(accountId, "Account id cannot be null");
      revision = checkNotNull(revision, "Revision cannot be null").copy();
    }
  }

  private final LoadingCache<Key, List<AuthToken>> cache;
  private final Provider<Repository> allUsersRepository;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, AuthTokenCache.Key.class, new TypeLiteral<List<AuthToken>>() {})
            .loader(Loader.class);
        bind(AuthTokenCache.class);
      }
    };
  }

  @Inject
  AuthTokenCache(
      @Named(CACHE_NAME) LoadingCache<Key, List<AuthToken>> cache,
      @AllUsersRepository Provider<Repository> allUsersRepository) {
    this.cache = cache;
    this.allUsersRepository = allUsersRepository;
  }

  public List<AuthToken> get(Account.Id accountId) {
    try (Repository repo = allUsersRepository.get()) {
      Ref ref = repo.exactRef(RefNames.refsUsers(accountId));
      if (ref == null || ref.getObjectId() == null) {
        return ImmutableList.of();
      }
      return cache.get(new Key(accountId, ref.getObjectId()));
    } catch (IOException | ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Cannot load authentication tokens for %d", accountId.get());
      throw new StorageException(e);
    }
  }

  static class Loader extends CacheLoader<Key, List<AuthToken>> {
    private final Provider<Repository> allUsersRepository;
    private final AllUsersName allUsersName;
    private final VersionedAuthTokens.Factory authTokenFactory;

    @Inject
    Loader(
        @AllUsersRepository Provider<Repository> allUsersRepository,
        AllUsersName allUsersName,
        VersionedAuthTokens.Factory authTokenFactory) {
      this.allUsersRepository = allUsersRepository;
      this.allUsersName = allUsersName;
      this.authTokenFactory = authTokenFactory;
    }

    @Override
    public ImmutableList<AuthToken> load(Key key) throws Exception {
      try (Repository repo = allUsersRepository.get()) {
        VersionedAuthTokens tokens = authTokenFactory.create(key.accountId());
        tokens.load(allUsersName, repo, key.revision());
        return tokens.getTokens();
      }
    }
  }
}
