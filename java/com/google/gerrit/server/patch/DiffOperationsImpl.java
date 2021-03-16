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

import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Provides different file diff operations. Uses the underlying Git/Gerrit caches to speed up the
 * diff computation.
 */
public class DiffOperationsImpl implements DiffOperations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int RENAME_SCORE = 60;
  private static final DiffAlgorithm DEFAULT_DIFF_ALGORITHM = DiffAlgorithm.HISTOGRAM;
  private static final Whitespace DEFAULT_WHITESPACE = Whitespace.IGNORE_NONE;

  private final ModifiedFilesCache modifiedFilesCache;
  private final FileDiffCache fileDiffCache;
  private final BaseCommitUtil baseCommitUtil;
  private final long timeoutMillis;
  private final ExecutorService diffExecutor;

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
      BaseCommitUtil baseCommit,
      @DiffExecutor ExecutorService executor,
      @GerritServerConfig Config cfg) {
    this.modifiedFilesCache = modifiedFilesCache;
    this.fileDiffCache = fileDiffCache;
    this.baseCommitUtil = baseCommit;
    this.diffExecutor = executor;
    this.timeoutMillis =
        ConfigUtil.getTimeUnit(
            cfg,
            "cache",
            "diff",
            "timeout",
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
  }

  @Override
  public Map<String, FileDiffOutput> listModifiedFilesAgainstParent(
      Project.NameKey project, ObjectId newCommit, @Nullable Integer parent)
      throws DiffNotAvailableException {
    try {
      DiffParameters diffParams = computeDiffParameters(project, newCommit, parent);
      return listModifiedFilesWithTimeout(diffParams);
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          "Failed to evaluate the parent/base commit for commit " + newCommit, e);
    }
  }

  @Override
  public Map<String, FileDiffOutput> listModifiedFiles(
      Project.NameKey project, ObjectId oldCommit, ObjectId newCommit)
      throws DiffNotAvailableException {
    DiffParameters params =
        DiffParameters.builder()
            .project(project)
            .newCommit(newCommit)
            .baseCommit(oldCommit)
            .comparisonType(ComparisonType.againstOtherPatchSet())
            .build();
    return listModifiedFilesWithTimeout(params);
  }

  @Override
  public FileDiffOutput getModifiedFileAgainstParent(
      Project.NameKey project,
      ObjectId newCommit,
      @Nullable Integer parent,
      String fileName,
      @Nullable DiffPreferencesInfo.Whitespace whitespace)
      throws DiffNotAvailableException {
    try {
      DiffParameters diffParams = computeDiffParameters(project, newCommit, parent);
      FileDiffCacheKey key =
          createFileDiffCacheKey(project, diffParams.baseCommit(), newCommit, fileName, whitespace);
      return getModifiedFileWithTimeout(key, diffParams);
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          "Failed to evaluate the parent/base commit for commit " + newCommit, e);
    }
  }

  @Override
  public FileDiffOutput getModifiedFile(
      Project.NameKey project,
      ObjectId oldCommit,
      ObjectId newCommit,
      String fileName,
      @Nullable DiffPreferencesInfo.Whitespace whitespace)
      throws DiffNotAvailableException {
    DiffParameters params = // used for logging only
        DiffParameters.builder()
            .project(project)
            .baseCommit(oldCommit)
            .newCommit(newCommit)
            .comparisonType(ComparisonType.againstOtherPatchSet())
            .build();
    FileDiffCacheKey key =
        createFileDiffCacheKey(project, oldCommit, newCommit, fileName, whitespace);
    return getModifiedFileWithTimeout(key, params);
  }

  private Map<String, FileDiffOutput> listModifiedFilesWithTimeout(DiffParameters params)
      throws DiffNotAvailableException {
    Future<DiffResult> task =
        diffExecutor.submit(
            () -> {
              Map<String, FileDiffOutput> modifiedFiles = getModifiedFiles(params);
              return DiffResult.create(null, modifiedFiles);
            });
    DiffResult diffResult = execDiffWithTimeout(task, params);
    return diffResult.modifiedFiles();
  }

  private FileDiffOutput getModifiedFileWithTimeout(FileDiffCacheKey key, DiffParameters params)
      throws DiffNotAvailableException {
    Future<DiffResult> task =
        diffExecutor.submit(
            () -> {
              Map<String, FileDiffOutput> diffList = getModifiedFilesForKeys(ImmutableList.of(key));
              FileDiffOutput fileDiffOutput =
                  diffList.containsKey(key.newFilePath())
                      ? diffList.get(key.newFilePath())
                      : FileDiffOutput.empty(key.newFilePath(), key.oldCommit(), key.newCommit());
              return DiffResult.create(fileDiffOutput, null);
            });
    DiffResult result = execDiffWithTimeout(task, params);
    return result.fileDiff();
  }

  /** Executes a diff task by employing a timeout. */
  private DiffResult execDiffWithTimeout(Future<DiffResult> task, DiffParameters params)
      throws DiffNotAvailableException {
    try {
      return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      throw new DiffNotAvailableException(
          String.format(
              "Timeout reached while computing diff for project %s, old commit %s, new commit %s",
              params.project(), params.baseCommit().name(), params.newCommit().name()),
          e);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  private ImmutableMap<String, FileDiffOutput> getModifiedFiles(DiffParameters diffParams)
      throws DiffNotAvailableException {
    try {
      Project.NameKey project = diffParams.project();
      ObjectId newCommit = diffParams.newCommit();
      ObjectId oldCommit = diffParams.baseCommit();
      ComparisonType cmp = diffParams.comparisonType();

      ImmutableList<ModifiedFile> modifiedFiles =
          modifiedFilesCache.get(createModifiedFilesKey(project, oldCommit, newCommit));

      List<FileDiffCacheKey> fileCacheKeys = new ArrayList<>();
      fileCacheKeys.add(
          createFileDiffCacheKey(
              project, oldCommit, newCommit, COMMIT_MSG, /* whitespace= */ null));

      if (cmp.isAgainstAutoMerge() || isMergeAgainstParent(cmp, project, newCommit)) {
        fileCacheKeys.add(
            createFileDiffCacheKey(
                project, oldCommit, newCommit, MERGE_LIST, /*whitespace = */ null));
      }

      if (diffParams.skipFiles() == null) {
        modifiedFiles.stream()
            .map(
                entity ->
                    createFileDiffCacheKey(
                        project,
                        oldCommit,
                        newCommit,
                        entity.newPath().isPresent()
                            ? entity.newPath().get()
                            : entity.oldPath().get(),
                        /* whitespace= */ null))
            .forEach(fileCacheKeys::add);
      }
      return getModifiedFilesForKeys(fileCacheKeys);
    } catch (IOException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  private ImmutableMap<String, FileDiffOutput> getModifiedFilesForKeys(List<FileDiffCacheKey> keys)
      throws DiffNotAvailableException {
    ImmutableMap.Builder<String, FileDiffOutput> files = ImmutableMap.builder();
    ImmutableMap<FileDiffCacheKey, FileDiffOutput> fileDiffs = fileDiffCache.getAll(keys);

    for (FileDiffOutput fileDiffOutput : fileDiffs.values()) {
      if (fileDiffOutput.isEmpty() || allDueToRebase(fileDiffOutput)) {
        continue;
      }
      if (fileDiffOutput.changeType() == ChangeType.DELETED) {
        files.put(fileDiffOutput.oldPath().get(), fileDiffOutput);
      } else {
        files.put(fileDiffOutput.newPath().get(), fileDiffOutput);
      }
    }
    return files.build();
  }

  private static boolean allDueToRebase(FileDiffOutput fileDiffOutput) {
    return fileDiffOutput.allEditsDueToRebase()
        && (!(fileDiffOutput.changeType() == ChangeType.RENAMED
            || fileDiffOutput.changeType() == ChangeType.COPIED));
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
      Project.NameKey project,
      ObjectId aCommit,
      ObjectId bCommit,
      String newPath,
      @Nullable Whitespace whitespace) {
    whitespace = whitespace == null ? DEFAULT_WHITESPACE : whitespace;
    return FileDiffCacheKey.builder()
        .project(project)
        .oldCommit(aCommit)
        .newCommit(bCommit)
        .newFilePath(newPath)
        .renameScore(RENAME_SCORE)
        .diffAlgorithm(DEFAULT_DIFF_ALGORITHM)
        .whitespace(whitespace)
        .build();
  }

  /**
   * All interface methods create their results using this class. This is used so that the timeout
   * method {@link #execDiffWithTimeout(Future, DiffParameters)} could be reused by all interface
   * methods.
   */
  @AutoValue
  abstract static class DiffResult {
    static DiffResult create(
        @Nullable FileDiffOutput fileDiff, @Nullable Map<String, FileDiffOutput> modifiedFiles) {
      return new AutoValue_DiffOperationsImpl_DiffResult(fileDiff, modifiedFiles);
    }

    @Nullable
    abstract FileDiffOutput fileDiff();

    @Nullable
    abstract Map<String, FileDiffOutput> modifiedFiles();
  }

  @AutoValue
  abstract static class DiffParameters {
    abstract Project.NameKey project();

    abstract ObjectId newCommit();

    abstract ObjectId baseCommit();

    abstract ComparisonType comparisonType();

    @Nullable
    abstract Integer parent();

    /** Compute the diff for {@value Patch#COMMIT_MSG} and {@link Patch#MERGE_LIST} only. */
    @Nullable
    abstract Boolean skipFiles();

    static Builder builder() {
      return new AutoValue_DiffOperationsImpl_DiffParameters.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder project(Project.NameKey project);

      abstract Builder newCommit(ObjectId newCommit);

      abstract Builder baseCommit(ObjectId baseCommit);

      abstract Builder parent(@Nullable Integer parent);

      abstract Builder skipFiles(@Nullable Boolean skipFiles);

      abstract Builder comparisonType(ComparisonType comparisonType);

      public abstract DiffParameters build();
    }
  }

  /** Compute Diff parameters - the base commit and the comparison type - using the input args. */
  private DiffParameters computeDiffParameters(
      Project.NameKey project, ObjectId newCommit, Integer parent) throws IOException {
    DiffParameters.Builder result =
        DiffParameters.builder().project(project).newCommit(newCommit).parent(parent);
    if (parent != null) {
      result.baseCommit(baseCommitUtil.getBaseCommit(project, newCommit, parent));
      result.comparisonType(ComparisonType.againstParent(parent));
      return result.build();
    }
    int numParents = baseCommitUtil.getNumParents(project, newCommit);
    if (numParents == 1) {
      result.baseCommit(baseCommitUtil.getBaseCommit(project, newCommit, parent));
      result.comparisonType(ComparisonType.againstParent(1));
      return result.build();
    }
    if (numParents > 2) {
      logger.atFine().log(
          "Diff against auto-merge for merge commits "
              + "with more than two parents is not supported. Commit "
              + newCommit
              + " has "
              + numParents
              + " parents. Falling back to the diff against the first parent.");
      result.baseCommit(baseCommitUtil.getBaseCommit(project, newCommit, 1).getId());
      result.comparisonType(ComparisonType.againstParent(1));
      result.skipFiles(true);
    } else {
      result.baseCommit(baseCommitUtil.getBaseCommit(project, newCommit, null));
      result.comparisonType(ComparisonType.againstAutoMerge());
    }
    return result.build();
  }
}
