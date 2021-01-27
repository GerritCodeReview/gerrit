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

package com.google.gerrit.server.patch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.patch.diff.ModifiedFilesCache;
import com.google.gerrit.server.patch.diff.ModifiedFilesCacheImpl;
import com.google.gerrit.server.patch.diff.ModifiedFilesCacheKey;
import com.google.gerrit.server.patch.filediff.FileDiffCache;
import com.google.gerrit.server.patch.filediff.FileDiffCacheImpl;
import com.google.gerrit.server.patch.filediff.FileDiffCacheKey;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheImpl;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Provides different file diff operations. Uses the underlying Git/Gerrit caches to speed up the
 * diff computation.
 */
public class DiffOperationsImpl implements DiffOperations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int RENAME_SCORE = 60;
  private static final DiffAlgorithm DEFAULT_DIFF_ALGORITHM = DiffAlgorithm.HISTOGRAM;

  private final ModifiedFilesCache modifiedFilesCache;
  private final FileDiffCache fileDiffCache;
  private final BaseCommitUtil baseCommitUtil;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(DiffOperations.class).to(DiffOperationsImpl.class);
        install(GitModifiedFilesCacheImpl.module());
        install(ModifiedFilesCacheImpl.module());
        install(GitFileDiffCacheImpl.module());
        install(FileDiffCacheImpl.module());
      }
    };
  }

  @Inject
  public DiffOperationsImpl(
      ModifiedFilesCache modifiedFilesCache,
      FileDiffCache fileDiffCache,
      BaseCommitUtil baseCommit) {
    this.modifiedFilesCache = modifiedFilesCache;
    this.fileDiffCache = fileDiffCache;
    this.baseCommitUtil = baseCommit;
  }

  @Override
  public Map<String, FileDiffOutput> getModifiedFilesAgainstParentOrAutoMerge(
      Project.NameKey project, ObjectId newCommit, @Nullable Integer parent)
      throws DiffNotAvailableException {
    try {
      if (parent != null) {
        RevObject base = baseCommitUtil.getBaseCommit(project, newCommit, parent);
        return getModifiedFiles(project, base, newCommit, ComparisonType.againstParent(parent));
      }
      int numParents = baseCommitUtil.getNumParents(project, newCommit);
      if (numParents == 1) {
        RevObject base = baseCommitUtil.getBaseCommit(project, newCommit, parent);
        ComparisonType cmp = ComparisonType.againstParent(1);
        return getModifiedFiles(project, base, newCommit, cmp);
      }
      if (numParents > 2) {
        logger.atFine().log(
            "Diff against auto-merge for merge commits "
                + "with more than two parents is not supported. Commit "
                + newCommit
                + " has "
                + numParents
                + " parents. Falling back to the diff against the first parent.");
        ObjectId firstParentId = baseCommitUtil.getBaseCommit(project, newCommit, 1).getId();
        ImmutableList.Builder<FileDiffCacheKey> keys = ImmutableList.builder();
        keys.add(createFileDiffCacheKey(project, firstParentId, newCommit, Patch.COMMIT_MSG));
        keys.add(createFileDiffCacheKey(project, firstParentId, newCommit, Patch.MERGE_LIST));
        return getModifiedFilesForKeys(keys.build());
      }
      RevObject autoMerge = baseCommitUtil.getBaseCommit(project, newCommit, null);
      return getModifiedFiles(project, autoMerge, newCommit, ComparisonType.againstAutoMerge());
    } catch (IOException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public Map<String, FileDiffOutput> getModifiedFilesBetweenPatchsets(
      Project.NameKey project, ObjectId oldCommit, ObjectId newCommit)
      throws DiffNotAvailableException {
    ComparisonType cmp = ComparisonType.againstOtherPatchSet();
    return getModifiedFiles(project, oldCommit, newCommit, cmp);
  }

  private Map<String, FileDiffOutput> getModifiedFiles(
      Project.NameKey project, ObjectId oldCommit, ObjectId newCommit, ComparisonType cmp)
      throws DiffNotAvailableException {
    try {
      ImmutableList<ModifiedFile> modifiedFiles =
          modifiedFilesCache.get(createModifiedFilesKey(project, oldCommit, newCommit));

      List<FileDiffCacheKey> fileCacheKeys =
          modifiedFiles.stream()
              .map(
                  entity ->
                      createFileDiffCacheKey(
                          project,
                          oldCommit,
                          newCommit,
                          entity.newPath().isPresent()
                              ? entity.newPath().get()
                              : entity.oldPath().get()))
              .collect(Collectors.toList());

      fileCacheKeys.add(createFileDiffCacheKey(project, oldCommit, newCommit, Patch.COMMIT_MSG));

      if (cmp.isAgainstAutoMerge() || isMergeAgainstParent(cmp, project, newCommit)) {
        fileCacheKeys.add(createFileDiffCacheKey(project, oldCommit, newCommit, Patch.MERGE_LIST));
      }
      return getModifiedFilesForKeys(fileCacheKeys);
    } catch (IOException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  private Map<String, FileDiffOutput> getModifiedFilesForKeys(List<FileDiffCacheKey> keys)
      throws DiffNotAvailableException {
    ImmutableMap.Builder<String, FileDiffOutput> files = ImmutableMap.builder();
    ImmutableMap<FileDiffCacheKey, FileDiffOutput> fileDiffs = fileDiffCache.getAll(keys);

    for (Map.Entry<FileDiffCacheKey, FileDiffOutput> entry : fileDiffs.entrySet()) {
      FileDiffOutput fileDiffOutput = entry.getValue();
      if (fileDiffOutput.isEmpty() || allDueToRebase(fileDiffOutput)) {
        continue;
      }
      if (fileDiffOutput.changeType().get() == Patch.ChangeType.DELETED) {
        files.put(fileDiffOutput.oldPath().get(), fileDiffOutput);
      } else {
        files.put(fileDiffOutput.newPath().get(), fileDiffOutput);
      }
    }
    return files.build();
  }

  private static boolean allDueToRebase(FileDiffOutput fileDiffOutput) {
    return fileDiffOutput.allEditsDueToRebase()
        && (!(fileDiffOutput.changeType().get() == ChangeType.RENAMED
            || fileDiffOutput.changeType().get() == ChangeType.COPIED));
  }

  private boolean isMergeAgainstParent(ComparisonType cmp, Project.NameKey project, ObjectId commit)
      throws IOException {
    return (cmp.isAgainstParent() && baseCommitUtil.getNumParents(project, commit) > 1);
  }

  private static ModifiedFilesCacheKey createModifiedFilesKey(
      Project.NameKey project, ObjectId aCommit, ObjectId bCommit) {
    return ModifiedFilesCacheKey.builder()
        .project(project)
        .aCommit(aCommit)
        .bCommit(bCommit)
        .renameScore(RENAME_SCORE)
        .build();
  }

  private static FileDiffCacheKey createFileDiffCacheKey(
      Project.NameKey project, ObjectId aCommit, ObjectId bCommit, String newPath) {
    return FileDiffCacheKey.builder()
        .project(project)
        .oldCommit(aCommit)
        .newCommit(bCommit)
        .newFilePath(newPath)
        .renameScore(RENAME_SCORE)
        .diffAlgorithm(DEFAULT_DIFF_ALGORITHM)
        .whitespace(Whitespace.IGNORE_NONE)
        .build();
  }
}
