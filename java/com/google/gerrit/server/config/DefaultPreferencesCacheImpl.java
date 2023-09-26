// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class DefaultPreferencesCacheImpl implements DefaultPreferencesCache {
  private static final String NAME = "default_preferences";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        // Bind an in-memory cache that allows exactly 1 value to be cached.
        cache(NAME, ObjectId.class, CachedPreferences.class).loader(Loader.class).maximumWeight(1);
        bind(DefaultPreferencesCacheImpl.class);
        bind(DefaultPreferencesCache.class).to(DefaultPreferencesCacheImpl.class);
      }
    };
  }

  private final GitRepositoryManager repositoryManager;
  private final AllUsersName allUsersName;
  private final LoadingCache<ObjectId, CachedPreferences> cache;

  @Inject
  DefaultPreferencesCacheImpl(
      GitRepositoryManager repositoryManager,
      AllUsersName allUsersName,
      @Named(NAME) LoadingCache<ObjectId, CachedPreferences> cache) {
    this.repositoryManager = repositoryManager;
    this.allUsersName = allUsersName;
    this.cache = cache;
  }

  @Override
  public CachedPreferences get() {
    try (Repository allUsersRepo = repositoryManager.openRepository(allUsersName)) {
      Ref ref = allUsersRepo.exactRef(RefNames.REFS_USERS_DEFAULT);
      if (ref == null) {
        return EMPTY;
      }
      return get(ref.getObjectId());
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public CachedPreferences get(ObjectId rev) {
    try {
      return cache.get(rev);
    } catch (ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Singleton
  private static class Loader extends CacheLoader<ObjectId, CachedPreferences> {
    private final GitRepositoryManager repositoryManager;
    private final AllUsersName allUsersName;

    @Inject
    Loader(GitRepositoryManager repositoryManager, AllUsersName allUsersName) {
      this.repositoryManager = repositoryManager;
      this.allUsersName = allUsersName;
    }

    @Override
    public CachedPreferences load(ObjectId key) throws IOException, ConfigInvalidException {
      try (Repository allUsersRepo = repositoryManager.openRepository(allUsersName)) {
        VersionedDefaultPreferences versionedDefaultPreferences = new VersionedDefaultPreferences();
        versionedDefaultPreferences.load(allUsersName, allUsersRepo, key);
        return CachedPreferences.fromLegacyConfig(versionedDefaultPreferences.getConfig());
      }
    }
  }
}
