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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
            .loader(ModifiedFilesLoader.class);
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

  static class ModifiedFilesLoader
      extends CacheLoader<ModifiedFilesCacheKey, ImmutableList<ModifiedFile>> {
    private final GitModifiedFilesCache gitCache;
    private final GitRepositoryManager repoManager;

    @Inject
    ModifiedFilesLoader(GitModifiedFilesCache gitCache, GitRepositoryManager repoManager) {
      this.gitCache = gitCache;
      this.repoManager = repoManager;
    }

    @Override
    public ImmutableList<ModifiedFile> load(ModifiedFilesCacheKey key)
        throws IOException, DiffNotAvailableException {
      try (Repository repo = repoManager.openRepository(key.project());
          RevWalk rw = new RevWalk(repo.newObjectReader())) {
        return loadModifiedFiles(key, rw);
      }
    }

    private ImmutableList<ModifiedFile> loadModifiedFiles(ModifiedFilesCacheKey key, RevWalk rw)
        throws IOException, DiffNotAvailableException {
      ObjectId aTree =
          key.aCommit().equals(ObjectId.zeroId())
              ? key.aCommit()
              : DiffUtil.getTreeId(rw, key.aCommit());
      ObjectId bTree = DiffUtil.getTreeId(rw, key.bCommit());
      GitModifiedFilesCacheKey gitKey =
          GitModifiedFilesCacheKey.builder()
              .project(key.project())
              .aTree(aTree)
              .bTree(bTree)
              .renameScore(key.renameScore())
              .build();
      ImmutableList<ModifiedFile> modifiedFiles =
          DiffUtil.mergeRewrittenModifiedFiles(gitCache.get(gitKey));
      if (key.aCommit().equals(ObjectId.zeroId())) {
        return modifiedFiles;
      }
      RevCommit revCommitA = DiffUtil.getRevCommit(rw, key.aCommit());
      RevCommit revCommitB = DiffUtil.getRevCommit(rw, key.bCommit());
      if (DiffUtil.areRelated(revCommitA, revCommitB)) {
        return modifiedFiles;
      }
      Set<String> touchedFiles =
          getTouchedFilesWithParents(
              key, revCommitA.getParent(0).getId(), revCommitB.getParent(0).getId(), rw);
      return modifiedFiles.stream()
          .filter(f -> isTouched(touchedFiles, f))
          .collect(toImmutableList());
    }

    /**
     * Returns the paths of files that were modified between the old and new commits versus their
     * parents (i.e. old commit vs. its parent, and new commit vs. its parent).
     *
     * @param key the {@link ModifiedFilesCacheKey} representing the commits we are diffing
     * @param rw a {@link RevWalk} for the repository
     * @return The list of modified files between the old/new commits and their parents
     */
    private Set<String> getTouchedFilesWithParents(
        ModifiedFilesCacheKey key, ObjectId parentOfA, ObjectId parentOfB, RevWalk rw)
        throws IOException {
      try {
        // TODO(ghareeb): as an enhancement: the 3 calls of the underlying git cache can be combined
        GitModifiedFilesCacheKey oldVsBaseKey =
            GitModifiedFilesCacheKey.create(
                key.project(), parentOfA, key.aCommit(), key.renameScore(), rw);
        List<ModifiedFile> oldVsBase = gitCache.get(oldVsBaseKey);

        GitModifiedFilesCacheKey newVsBaseKey =
            GitModifiedFilesCacheKey.create(
                key.project(), parentOfB, key.bCommit(), key.renameScore(), rw);
        List<ModifiedFile> newVsBase = gitCache.get(newVsBaseKey);

        return Sets.union(getOldAndNewPaths(oldVsBase), getOldAndNewPaths(newVsBase));
      } catch (DiffNotAvailableException e) {
        logger.atWarning().log(
            "Failed to retrieve the touched files' commits (%s, %s) and parents (%s, %s): %s",
            key.aCommit(), key.bCommit(), parentOfA, parentOfB, e.getMessage());
        return ImmutableSet.of();
      }
    }

    private ImmutableSet<String> getOldAndNewPaths(List<ModifiedFile> files) {
      return files.stream()
          .flatMap(
              file -> Stream.concat(file.oldPath().stream(), file.newPath().stream()))
          .collect(ImmutableSet.toImmutableSet());
    }

    private static boolean isTouched(Set<String> touchedFilePaths, ModifiedFile modifiedFile) {
      String oldFilePath = modifiedFile.oldPath().orElse(null);
      String newFilePath = modifiedFile.newPath().orElse(null);
      // One of the above file paths could be /dev/null but we need not explicitly check for this
      // value as the set of file paths shouldn't contain it.
      return touchedFilePaths.contains(oldFilePath) || touchedFilePaths.contains(newFilePath);
    }
  }
}
