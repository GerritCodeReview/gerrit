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

package com.google.gerrit.server.patch.diff;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheImpl;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A cache for the list of Git modified files between 2 commits (patchsets) with extra Gerrit logic.
 *
 * <p>The loader of this cache wraps a {@link GitModifiedFilesCache} to retrieve the git modified
 * files.
 *
 * <p>If the {@link ModifiedFilesCacheKey#aCommit()} is equal to {@link ObjectId#zeroId()}, the diff
 * will be evaluated against the empty tree, and the result will be exactly the same as the caller
 * can get from {@link GitModifiedFilesCache#get(GitModifiedFilesCacheKey)}
 */
@Singleton
public class ModifiedFilesCacheImpl implements ModifiedFilesCache {
  private static final String MODIFIED_FILES = "modified_files";

  private final LoadingCache<ModifiedFilesCacheKey, ImmutableList<ModifiedFile>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ModifiedFilesCache.class).to(ModifiedFilesCacheImpl.class);

        // The documentation has some defaults and recommendations for setting the cache
        // attributes:
        // https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.
        // The cache is using the default disk limit as per section cache.<name>.diskLimit
        // in the cache documentation link.
        persist(
                ModifiedFilesCacheImpl.MODIFIED_FILES,
                ModifiedFilesCacheKey.class,
                new TypeLiteral<ImmutableList<ModifiedFile>>() {})
            .keySerializer(ModifiedFilesCacheKey.Serializer.INSTANCE)
            .valueSerializer(GitModifiedFilesCacheImpl.ValueSerializer.INSTANCE)
            .maximumWeight(10 << 20)
            .weigher(ModifiedFilesWeigher.class)
            .version(4)
            .loader(Loader.class);
      }
    };
  }

  @Inject
  public ModifiedFilesCacheImpl(
      @Named(ModifiedFilesCacheImpl.MODIFIED_FILES)
          LoadingCache<ModifiedFilesCacheKey, ImmutableList<ModifiedFile>> cache) {
    this.cache = cache;
  }

  @Override
  public ImmutableList<ModifiedFile> get(ModifiedFilesCacheKey key)
      throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (Exception e) {
      throw new DiffNotAvailableException(e);
    }
  }

  public Optional<ImmutableList<ModifiedFile>> getIfPresent(ModifiedFilesCacheKey key)
      throws DiffNotAvailableException {
    try {
      return Optional.ofNullable(cache.getIfPresent(key));
    } catch (Exception e) {
      throw new DiffNotAvailableException(e);
    }
  }

  public void put(ModifiedFilesCacheKey key, ImmutableList<ModifiedFile> modifiedFiles) {
    cache.put(key, modifiedFiles);
  }

  static class Loader extends CacheLoader<ModifiedFilesCacheKey, ImmutableList<ModifiedFile>> {
    private final GitRepositoryManager repoManager;
    private final ModifiedFilesLoader.Factory modifiedFilesLoaderFactory;

    @Inject
    Loader(
        GitRepositoryManager repoManager, ModifiedFilesLoader.Factory modifiedFilesLoaderFactory) {
      this.modifiedFilesLoaderFactory = modifiedFilesLoaderFactory;
      this.repoManager = repoManager;
    }

    @Override
    public ImmutableList<ModifiedFile> load(ModifiedFilesCacheKey key)
        throws IOException, DiffNotAvailableException {
      try (Repository repo = repoManager.openRepository(key.project());
          RevWalk revWalk = new RevWalk(repo.newObjectReader())) {
        ModifiedFilesLoader loader =
            modifiedFilesLoaderFactory
                .createWithRetrievingModifiedFilesForTreesFromGitModifiedFilesCache();
        if (key.renameDetectionEnabled()) {
          loader.withRenameDetection(key.renameScore());
        }
        return loader.load(key.project(), repo.getConfig(), revWalk, key.aCommit(), key.bCommit());
      }
    }
  }
}
