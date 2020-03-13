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

package com.google.gerrit.server.account;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Repository;

public class DefaultPreferencesCacheImpl implements DefaultPreferencesCache {
  public static final String NAME = "default_user_preferences";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(NAME, String.class, new TypeLiteral<UserPreferences.Default>() {})
            .loader(Loader.class)
            .expireAfterWrite(Duration.ofMinutes(30));

        bind(DefaultPreferencesCacheImpl.class);
        bind(DefaultPreferencesCache.class).to(DefaultPreferencesCacheImpl.class);
      }
    };
  }

  private final LoadingCache<String, UserPreferences.Default> cache;

  @Inject
  DefaultPreferencesCacheImpl(@Named(NAME) LoadingCache<String, UserPreferences.Default> cache) {
    this.cache = cache;
  }

  @Override
  public UserPreferences.Default get() throws StorageException {
    try {
      return cache.get(NAME);
    } catch (ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void invalidate() {
    cache.invalidateAll();
  }

  private static class Loader extends CacheLoader<String, UserPreferences.Default> {
    private final AllUsersName allUsersName;
    private final GitRepositoryManager repoManager;

    @Inject
    Loader(AllUsersName allUsersName, GitRepositoryManager repoManager) {
      this.allUsersName = allUsersName;
      this.repoManager = repoManager;
    }

    @Override
    public UserPreferences.Default load(String s) throws Exception {
      try (Repository repo = repoManager.openRepository(allUsersName);
          MetaDataUpdate md =
              new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, repo)) {
        VersionedPreferences versionedPreferences = VersionedPreferences.defaults();
        versionedPreferences.load(md);
        return UserPreferences.defaults(versionedPreferences.getPreferences());
      }
    }
  }
}
