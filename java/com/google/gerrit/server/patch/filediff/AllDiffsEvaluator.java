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

package com.google.gerrit.server.patch.filediff;

import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiff;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCache;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheKey;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A helper class that computes the four {@link GitFileDiff}s for a list of {@link
 * FileDiffCacheKey}s:
 *
 * <ul>
 *   <li>old commit vs. new commit
 *   <li>old parent vs. old commit
 *   <li>new parent vs. new commit
 *   <li>old parent vs. new parent
 * </ul>
 *
 * The four {@link GitFileDiff} are stored in the entity class {@link AllFileGitDiffs}. We use these
 * diffs to identify the edits due to rebase using the {@link EditTransformer} class.
 */
class AllDiffsEvaluator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RevWalk rw;
  private final GitFileDiffCache gitCache;

  interface Factory {
    AllDiffsEvaluator create(RevWalk rw);
  }

  @Inject
  private AllDiffsEvaluator(GitFileDiffCache gitCache, @Assisted RevWalk rw) {
    this.gitCache = gitCache;
    this.rw = rw;
  }

  Map<AugmentedFileDiffCacheKey, AllFileGitDiffs> execute(
      List<AugmentedFileDiffCacheKey> augmentedKeys) throws DiffNotAvailableException {
    ImmutableMap.Builder<AugmentedFileDiffCacheKey, AllFileGitDiffs> keyToAllDiffs =
        ImmutableMap.builderWithExpectedSize(augmentedKeys.size());

    List<AugmentedFileDiffCacheKey> keysWithRebaseEdits =
        augmentedKeys.stream().filter(k -> !k.ignoreRebase()).collect(Collectors.toList());

    // TODO(ghareeb): as an enhancement, you can batch these calls as follows.
    // First batch: "old commit vs. new commit" and "new parent vs. new commit"
    // Second batch: "old parent vs. old commit" and "old parent vs. new parent"

    Map<FileDiffCacheKey, GitDiffEntity> mainDiffs =
        computeGitFileDiffs(
            createGitKeys(
                augmentedKeys,
                k -> k.key().oldCommit(),
                k -> k.key().newCommit(),
                k -> k.key().newFilePath()));

    Map<FileDiffCacheKey, GitDiffEntity> oldVsParentDiffs =
        computeGitFileDiffs(
            createGitKeys(
                keysWithRebaseEdits,
                k -> k.oldParentId().get(), // oldParent is set for keysWithRebaseEdits
                k -> k.key().oldCommit(),
                k -> mainDiffs.get(k.key()).gitDiff().oldPath().orElse(null)));

    Map<FileDiffCacheKey, GitDiffEntity> newVsParentDiffs =
        computeGitFileDiffs(
            createGitKeys(
                keysWithRebaseEdits,
                k -> k.newParentId().get(), // newParent is set for keysWithRebaseEdits
                k -> k.key().newCommit(),
                k -> k.key().newFilePath()));

    Map<FileDiffCacheKey, GitDiffEntity> parentsDiffs =
        computeGitFileDiffs(
            createGitKeys(
                keysWithRebaseEdits,
                k -> k.oldParentId().get(),
                k -> k.newParentId().get(),
                k -> {
                  GitFileDiff newVsParDiff = newVsParentDiffs.get(k.key()).gitDiff();
                  // TODO(ghareeb): Follow up on replacing key.newFilePath as a fallback.
                  // If the file was added between newParent and newCommit, we actually wouldn't
                  // need to have to determine the oldParent vs. newParent diff as nothing in
                  // that file could be an edit due to rebase anymore. Only if the returned diff
                  // is empty, the oldParent vs. newParent diff becomes relevant again (e.g. to
                  // identify a file deletion which was due to rebase. Check if the structure
                  // can be improved to make this clearer. Can we maybe even skip the diff in
                  // the first situation described?
                  return newVsParDiff.oldPath().orElse(k.key().newFilePath());
                }));

    for (AugmentedFileDiffCacheKey augmentedKey : augmentedKeys) {
      FileDiffCacheKey key = augmentedKey.key();
      AllFileGitDiffs.Builder builder =
          AllFileGitDiffs.builder().augmentedKey(augmentedKey).mainDiff(mainDiffs.get(key));

      if (augmentedKey.ignoreRebase()) {
        keyToAllDiffs.put(augmentedKey, builder.build());
        continue;
      }

      if (oldVsParentDiffs.containsKey(key) && !oldVsParentDiffs.get(key).gitDiff().isEmpty()) {
        builder.oldVsParentDiff(Optional.of(oldVsParentDiffs.get(key)));
      }

      if (newVsParentDiffs.containsKey(key) && !newVsParentDiffs.get(key).gitDiff().isEmpty()) {
        builder.newVsParentDiff(Optional.of(newVsParentDiffs.get(key)));
      }

      if (parentsDiffs.containsKey(key) && !parentsDiffs.get(key).gitDiff().isEmpty()) {
        builder.parentVsParentDiff(Optional.of(parentsDiffs.get(key)));
      }

      keyToAllDiffs.put(augmentedKey, builder.build());
    }
    return keyToAllDiffs.build();
  }

  /**
   * Computes the git diff for the git keys of the input map {@code keys} parameter. The computation
   * uses the underlying {@link GitFileDiffCache}.
   */
  private Map<FileDiffCacheKey, GitDiffEntity> computeGitFileDiffs(
      Map<FileDiffCacheKey, GitFileDiffCacheKey> keys) throws DiffNotAvailableException {
    ImmutableMap.Builder<FileDiffCacheKey, GitDiffEntity> result =
        ImmutableMap.builderWithExpectedSize(keys.size());
    ImmutableMap<GitFileDiffCacheKey, GitFileDiff> gitDiffs = gitCache.getAll(keys.values());
    for (FileDiffCacheKey key : keys.keySet()) {
      GitFileDiffCacheKey gitKey = keys.get(key);
      GitFileDiff gitFileDiff = gitDiffs.get(gitKey);
      result.put(key, GitDiffEntity.create(gitKey, gitFileDiff));
    }
    return result.build();
  }

  /**
   * Convert a list of {@link AugmentedFileDiffCacheKey} to their corresponding {@link
   * GitFileDiffCacheKey} which can be used to call the underlying {@link GitFileDiffCache}.
   *
   * @param keys a list of input {@link AugmentedFileDiffCacheKey}s.
   * @param aCommitFn a function to compute the aCommit that will be used in the git diff.
   * @param bCommitFn a function to compute the bCommit that will be used in the git diff.
   * @param newPathFn a function to compute the new path of the git key.
   * @return a map of the input {@link FileDiffCacheKey} to the {@link GitFileDiffCacheKey}.
   */
  private Map<FileDiffCacheKey, GitFileDiffCacheKey> createGitKeys(
      List<AugmentedFileDiffCacheKey> keys,
      Function<AugmentedFileDiffCacheKey, ObjectId> aCommitFn,
      Function<AugmentedFileDiffCacheKey, ObjectId> bCommitFn,
      Function<AugmentedFileDiffCacheKey, String> newPathFn) {
    Map<FileDiffCacheKey, GitFileDiffCacheKey> result = new HashMap<>();
    for (AugmentedFileDiffCacheKey key : keys) {
      try {
        String path = newPathFn.apply(key);
        if (path != null) {
          result.put(
              key.key(),
              createGitKey(key.key(), aCommitFn.apply(key), bCommitFn.apply(key), path, rw));
        }
      } catch (IOException e) {
        // TODO(ghareeb): This implies that the output keys may not have the same size as the input.
        // Check the caller's code path about the correctness of the computation in this case. If
        // errors are rare, it may be better to throw an exception and fail the whole computation.
        logger.atWarning().log("Failed to compute the git key for key %s: %s", key, e.getMessage());
      }
    }
    return result;
  }

  /** Returns the {@link GitFileDiffCacheKey} for the {@code key} input parameter. */
  private GitFileDiffCacheKey createGitKey(
      FileDiffCacheKey key, ObjectId aCommit, ObjectId bCommit, String pathNew, RevWalk rw)
      throws IOException {
    ObjectId oldTreeId =
        aCommit.equals(EMPTY_TREE_ID) ? EMPTY_TREE_ID : DiffUtil.getTreeId(rw, aCommit);
    ObjectId newTreeId = DiffUtil.getTreeId(rw, bCommit);
    return GitFileDiffCacheKey.builder()
        .project(key.project())
        .oldTree(oldTreeId)
        .newTree(newTreeId)
        .newFilePath(pathNew == null ? key.newFilePath() : pathNew)
        .renameScore(key.renameScore())
        .diffAlgorithm(key.diffAlgorithm())
        .whitespace(key.whitespace())
        .useTimeout(key.useTimeout())
        .build();
  }
}
