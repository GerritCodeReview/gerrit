//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.filediff;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Cache for the single file diff between two commits for a single file path. This cache adds extra
 * Gerrit logic such as identifying edits due to rebase.
 *
 * <p>If the {@link FileDiffCacheKey#oldCommit()} is equal to {@link ObjectId#zeroId()}, the git
 * diff will be evaluated against the empty tree.
 */
@Singleton
public class FileDiffCacheImpl implements FileDiffCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIFF = "gerrit_file_diff";

  private final LoadingCache<FileDiffCacheKey, FileDiffOutput> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(FileDiffCache.class).to(FileDiffCacheImpl.class);

        factory(AllDiffsEvaluator.Factory.class);

        persist(DIFF, FileDiffCacheKey.class, FileDiffOutput.class)
            .maximumWeight(10 << 20)
            .weigher(FileDiffWeigher.class)
            .version(8)
            .keySerializer(FileDiffCacheKey.Serializer.INSTANCE)
            .valueSerializer(FileDiffOutput.Serializer.INSTANCE)
            .loader(FileDiffLoader.class);
      }
    };
  }

  @Inject
  public FileDiffCacheImpl(@Named(DIFF) LoadingCache<FileDiffCacheKey, FileDiffOutput> cache) {
    this.cache = cache;
  }

  @Override
  public FileDiffOutput get(FileDiffCacheKey key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public ImmutableMap<FileDiffCacheKey, FileDiffOutput> getAll(Iterable<FileDiffCacheKey> keys)
      throws DiffNotAvailableException {
    try {
      ImmutableMap<FileDiffCacheKey, FileDiffOutput> result = cache.getAll(keys);
      if (result.size() != Iterables.size(keys)) {
        throw new DiffNotAvailableException(
            String.format(
                "Failed to load the value for all %d keys. Returned "
                    + "map contains only %d values",
                Iterables.size(keys), result.size()));
      }
      return result;
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class FileDiffLoader extends CacheLoader<FileDiffCacheKey, FileDiffOutput> {
    private final GitRepositoryManager repoManager;
    private final FileDiffCacheLoader fileDiffCacheLoader;

    @Inject
    FileDiffLoader(GitRepositoryManager manager, FileDiffCacheLoader fileDiffCacheLoader) {
      this.repoManager = manager;
      this.fileDiffCacheLoader = fileDiffCacheLoader;
    }

    @Override
    public FileDiffOutput load(FileDiffCacheKey key) throws IOException, DiffNotAvailableException {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading a single key from file diff cache",
              Metadata.builder().filePath(key.newFilePath()).build())) {
        return loadAll(ImmutableList.of(key)).get(key);
      }
    }

    @Override
    public Map<FileDiffCacheKey, FileDiffOutput> loadAll(Iterable<? extends FileDiffCacheKey> keys)
        throws DiffNotAvailableException {
      try (TraceTimer timer = TraceContext.newTimer("Loading multiple keys from file diff cache")) {
        ImmutableMap.Builder<FileDiffCacheKey, FileDiffOutput> result = ImmutableMap.builder();

        Map<Project.NameKey, List<FileDiffCacheKey>> keysByProject =
            Streams.stream(keys)
                .distinct()
                .collect(Collectors.groupingBy(FileDiffCacheKey::project));

        for (Project.NameKey project : keysByProject.keySet()) {
          try (Repository repo = repoManager.openRepository(project);
              ObjectReader reader = repo.newObjectReader();
              RevWalk rw = new RevWalk(reader)) {
            result.putAll(fileDiffCacheLoader.loadKeysFromRepository(keys, rw));
          } catch (IOException e) {
            logger.atWarning().log("Failed to open the repository %s: %s", project, e.getMessage());
          }
        }
        return result.build();
      }
    }
  }
}
