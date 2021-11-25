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

package com.google.gerrit.server.patch.diff;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class LoadModifiedFilesList {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitModifiedFilesCache gitCache;

  @Inject
  LoadModifiedFilesList(GitModifiedFilesCache gitCache) {
    this.gitCache = gitCache;
  }

  public ImmutableList<ModifiedFile> loadModifiedFiles(ModifiedFilesCacheKey key, RevWalk rw)
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
    List<ModifiedFile> modifiedFiles = mergeRewrittenEntries(gitCache.get(gitKey));
    if (key.aCommit().equals(ObjectId.zeroId())) {
      return ImmutableList.copyOf(modifiedFiles);
    }
    RevCommit revCommitA = DiffUtil.getRevCommit(rw, key.aCommit());
    RevCommit revCommitB = DiffUtil.getRevCommit(rw, key.bCommit());
    if (DiffUtil.areRelated(revCommitA, revCommitB)) {
      return ImmutableList.copyOf(modifiedFiles);
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
            file -> Stream.concat(Streams.stream(file.oldPath()), Streams.stream(file.newPath())))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static boolean isTouched(Set<String> touchedFilePaths, ModifiedFile modifiedFile) {
    String oldFilePath = modifiedFile.oldPath().orElse(null);
    String newFilePath = modifiedFile.newPath().orElse(null);
    // One of the above file paths could be /dev/null but we need not explicitly check for this
    // value as the set of file paths shouldn't contain it.
    return touchedFilePaths.contains(oldFilePath) || touchedFilePaths.contains(newFilePath);
  }

  /**
   * Return the {@code modifiedFiles} input list while merging rewritten entries.
   *
   * <p>Background: In some cases, JGit returns two diff entries (ADDED/DELETED, RENAMED/DELETED,
   * etc...) for the same file path. This happens e.g. when a file's mode is changed between
   * patchsets, for example converting a symlink file to a regular file. We identify this case and
   * return a single modified file with changeType = {@link ChangeType#REWRITE}.
   */
  private static List<ModifiedFile> mergeRewrittenEntries(List<ModifiedFile> modifiedFiles) {
    List<ModifiedFile> result = new ArrayList<>();
    ListMultimap<String, ModifiedFile> byPath = ArrayListMultimap.create();
    modifiedFiles.stream()
        .forEach(
            f -> {
              if (f.changeType() == ChangeType.DELETED) {
                byPath.get(f.oldPath().get()).add(f);
              } else {
                byPath.get(f.newPath().get()).add(f);
              }
            });
    for (String path : byPath.keySet()) {
      List<ModifiedFile> entries = byPath.get(path);
      if (entries.size() == 1) {
        result.add(entries.get(0));
      } else {
        // More than one. Return a single REWRITE entry.
        result.add(entries.get(0).toBuilder().changeType(ChangeType.REWRITE).build());
      }
    }
    return result;
  }
}
