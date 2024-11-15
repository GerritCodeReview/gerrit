// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesLoader;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to load the files that have been modified between two commits.
 *
 * <p>Rename detection is off unless {@link #withRenameDetection(int)} is called.
 *
 * <p>The commits and their trees are looked up via the {@link RevWalk} instance that is provided to
 * the {@link #load(com.google.gerrit.entities.Project.NameKey, Config, RevWalk, ObjectId,
 * ObjectId)} method, unless the modified files for the trees of the commits should be retrieved
 * from the {@link GitModifiedFilesCache} (see {@link
 * Factory#createWithRetrievingModifiedFilesForTreesFromGitModifiedFilesCache()} in which case the
 * trees are looked up via a new {@link RevWalk} instance that is created by {@code
 * GitModifiedFilesCacheImpl.Loader}. Looking up the trees from a new {@link RevWalk} instance only
 * succeeds if they were already fully persisted in the repository, i.e., if these are not newly
 * created trees or tree which have been created in memory. This means using the {@link
 * GitModifiedFilesCache} is expected to cause {@link MissingObjectException}s for the commit trees
 * that are newly created or that were created in memory only.
 */
public class ModifiedFilesLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final GitModifiedFilesCache gitModifiedFilesCache;

    @Inject
    Factory(GitModifiedFilesCache gitModifiedFilesCache) {
      this.gitModifiedFilesCache = gitModifiedFilesCache;
    }

    /**
     * Creates a {@link ModifiedFilesLoader} instance that looks up the commits and their trees via
     * the {@link RevWalk} instance that is provided to the {@link
     * #load(com.google.gerrit.entities.Project.NameKey, Config, RevWalk, ObjectId, ObjectId)}
     * method.
     */
    public ModifiedFilesLoader create() {
      return new ModifiedFilesLoader(/* gitModifiedFilesCache= */ null);
    }

    /**
     * Creates a {@link ModifiedFilesLoader} instance that retrieves the modified files for the
     * trees of the commits from the {@link GitModifiedFilesCache}.
     *
     * <p>Retrieving modified files for the trees from the {@link GitModifiedFilesCache} means that
     * the trees are loaded via a new {@link RevWalk} instance (that is created by {@code
     * GitModifiedFilesCacheImpl.Loader}), and not by the {@link RevWalk} instance that is given to
     * the {@link #load(com.google.gerrit.entities.Project.NameKey, Config, RevWalk, ObjectId,
     * ObjectId)} method. Looking up the trees from a new {@link RevWalk} instance only succeeds if
     * they were already fully persisted in the repository, i.e., if these are not newly created
     * trees or tree which have been created in memory. This means using the {@link
     * GitModifiedFilesCache} is expected to cause {@link MissingObjectException}s for the commit
     * trees that are newly created or that were created in memory only. Also see the javadoc on
     * this class.
     */
    ModifiedFilesLoader createWithRetrievingModifiedFilesForTreesFromGitModifiedFilesCache() {
      return new ModifiedFilesLoader(gitModifiedFilesCache);
    }
  }

  @Nullable private final GitModifiedFilesCache gitModifiedFilesCache;

  @Nullable private Integer renameScore = null;

  ModifiedFilesLoader(@Nullable GitModifiedFilesCache gitModifiedFilesCache) {
    this.gitModifiedFilesCache = gitModifiedFilesCache;
  }

  /**
   * Enables rename detection
   *
   * @param renameScore the score that should be used for the rename detection.
   */
  @CanIgnoreReturnValue
  public ModifiedFilesLoader withRenameDetection(int renameScore) {
    checkState(renameScore >= 0);
    this.renameScore = renameScore;
    return this;
  }

  /**
   * Loads the files that have been modified between {@code baseCommit} and {@code newCommit}.
   *
   * <p>The commits and the commit trees are looked up via the given {@code revWalk} instance,
   * unless the modified files for the trees of the commits should be retrieved from the {@link
   * GitModifiedFilesCache} (see {@link
   * Factory#createWithRetrievingModifiedFilesForTreesFromGitModifiedFilesCache()} in which case the
   * trees are looked up via a new {@link RevWalk} instance that is created by {@code
   * GitModifiedFilesCacheImpl.Loader}. Also see the javadoc on this class.
   */
  public ImmutableList<ModifiedFile> load(
      Project.NameKey project,
      Config repoConfig,
      RevWalk revWalk,
      ObjectId baseCommit,
      ObjectId newCommit)
      throws DiffNotAvailableException {
    try {
      ObjectId baseTree =
          baseCommit.equals(ObjectId.zeroId())
              ? ObjectId.zeroId()
              : DiffUtil.getTreeId(revWalk, baseCommit);
      ObjectId newTree = DiffUtil.getTreeId(revWalk, newCommit);
      ImmutableList<ModifiedFile> modifiedFiles =
          ImmutableList.sortedCopyOf(
              comparing(f -> f.getDefaultPath()),
              DiffUtil.mergeRewrittenModifiedFiles(
                  getModifiedFiles(
                      project, repoConfig, revWalk.getObjectReader(), baseTree, newTree)));
      if (baseCommit.equals(ObjectId.zeroId())) {
        return modifiedFiles;
      }
      RevCommit revCommitBase = DiffUtil.getRevCommit(revWalk, baseCommit);
      RevCommit revCommitNew = DiffUtil.getRevCommit(revWalk, newCommit);
      if (DiffUtil.areRelated(revCommitBase, revCommitNew)) {
        return modifiedFiles;
      }
      Set<String> touchedFiles =
          getTouchedFilesWithParents(
              project,
              repoConfig,
              revWalk,
              baseCommit,
              revCommitBase.getParent(0).getId(),
              newCommit,
              revCommitNew.getParent(0).getId());
      return modifiedFiles.stream()
          .filter(f -> isTouched(touchedFiles, f))
          .collect(toImmutableList());
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          String.format(
              "Failed to get files that have been modified between commit %s and commit %s in"
                  + " project %s",
              baseCommit.name(), newCommit.name(), project),
          e);
    }
  }

  /**
   * Returns the paths of files that were modified between the base and new commits versus their
   * parents (i.e. base commit vs. its parent, and new commit vs. its parent).
   *
   * @return The list of modified files between the base/new commits and their parents
   */
  private Set<String> getTouchedFilesWithParents(
      Project.NameKey project,
      Config repoConfig,
      RevWalk revWalk,
      ObjectId baseCommit,
      ObjectId parentOfBase,
      ObjectId newCommit,
      ObjectId parentOfNew)
      throws IOException {
    try {
      ImmutableList<ModifiedFile> oldVsBase =
          getModifiedFiles(
              project,
              repoConfig,
              revWalk.getObjectReader(),
              DiffUtil.getTreeId(revWalk, parentOfBase),
              DiffUtil.getTreeId(revWalk, baseCommit));
      ImmutableList<ModifiedFile> newVsBase =
          getModifiedFiles(
              project,
              repoConfig,
              revWalk.getObjectReader(),
              DiffUtil.getTreeId(revWalk, parentOfNew),
              DiffUtil.getTreeId(revWalk, newCommit));
      return Sets.union(getOldAndNewPaths(oldVsBase), getOldAndNewPaths(newVsBase));
    } catch (DiffNotAvailableException e) {
      logger.atWarning().log(
          "Failed to retrieve the touched files' commits (%s, %s) and parents (%s, %s): %s",
          baseCommit, newCommit, parentOfBase, parentOfNew, e.getMessage());
      return ImmutableSet.of();
    }
  }

  /**
   * Get the files that have been modified between {@code baseTree} and {@code newTree}.
   *
   * <p>The modified files are loaded through {@link GitModifiedFilesLoader} unless it was requested
   * to retrieve them from {@link GitModifiedFilesCache} (see {@link
   * Factory#createWithRetrievingModifiedFilesForTreesFromGitModifiedFilesCache()})
   */
  private ImmutableList<ModifiedFile> getModifiedFiles(
      Project.NameKey project,
      Config repoConfig,
      ObjectReader reader,
      ObjectId baseTree,
      ObjectId newTree)
      throws IOException, DiffNotAvailableException {
    if (gitModifiedFilesCache != null) {
      GitModifiedFilesCacheKey.Builder cacheKeyBuilder =
          GitModifiedFilesCacheKey.builder().project(project).aTree(baseTree).bTree(newTree);
      if (renameScore != null) {
        cacheKeyBuilder.renameScore(renameScore);
      } else {
        cacheKeyBuilder.disableRenameDetection();
      }
      return gitModifiedFilesCache.get(cacheKeyBuilder.build());
    }

    GitModifiedFilesLoader gitModifiedFilesLoader = new GitModifiedFilesLoader();
    if (renameScore != null) {
      gitModifiedFilesLoader.withRenameDetection(renameScore);
    }
    return gitModifiedFilesLoader.load(repoConfig, reader, baseTree, newTree);
  }

  private ImmutableSet<String> getOldAndNewPaths(List<ModifiedFile> files) {
    return files.stream()
        .flatMap(file -> Stream.concat(file.oldPath().stream(), file.newPath().stream()))
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
