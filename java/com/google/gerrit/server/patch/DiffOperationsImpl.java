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

package com.google.gerrit.server.patch;

import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
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
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Provides different file diff operations. Uses the underlying Git/Gerrit caches to speed up the
 * diff computation.
 */
@Singleton
public class DiffOperationsImpl implements DiffOperations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableMap<DiffEntry.ChangeType, Patch.ChangeType> changeTypeMap =
      ImmutableMap.of(
          DiffEntry.ChangeType.ADD,
          Patch.ChangeType.ADDED,
          DiffEntry.ChangeType.MODIFY,
          Patch.ChangeType.MODIFIED,
          DiffEntry.ChangeType.DELETE,
          Patch.ChangeType.DELETED,
          DiffEntry.ChangeType.RENAME,
          Patch.ChangeType.RENAMED,
          DiffEntry.ChangeType.COPY,
          Patch.ChangeType.COPIED);

  private static final int RENAME_SCORE = 60;
  private static final DiffAlgorithm DEFAULT_DIFF_ALGORITHM =
      DiffAlgorithm.HISTOGRAM_WITH_FALLBACK_MYERS;
  private static final Whitespace DEFAULT_WHITESPACE = Whitespace.IGNORE_NONE;

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
  public Map<String, FileDiffOutput> listModifiedFilesAgainstParent(
      Project.NameKey project, ObjectId newCommit, int parent, DiffOptions diffOptions)
      throws DiffNotAvailableException {
    try {
      DiffParameters diffParams = computeDiffParameters(project, newCommit, parent);
      return getModifiedFiles(diffParams, diffOptions);
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          "Failed to evaluate the parent/base commit for commit " + newCommit, e);
    }
  }

  @Override
  public Map<String, ModifiedFile> loadModifiedFilesAgainstParent(
      Project.NameKey project,
      ObjectId newCommit,
      int parentNum,
      DiffOptions diffOptions,
      RevWalk revWalk,
      Config repoConfig)
      throws DiffNotAvailableException {
    try {
      DiffParameters diffParams = computeDiffParameters(project, newCommit, parentNum);
      return loadModifiedFilesWithoutCache(project, diffParams, revWalk, repoConfig);
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          String.format(
              "Failed to evaluate the parent/base commit for commit '%s' with parentNum=%d",
              newCommit, parentNum),
          e);
    }
  }

  @Override
  public Map<String, FileDiffOutput> listModifiedFiles(
      Project.NameKey project, ObjectId oldCommit, ObjectId newCommit, DiffOptions diffOptions)
      throws DiffNotAvailableException {
    DiffParameters params =
        DiffParameters.builder()
            .project(project)
            .newCommit(newCommit)
            .baseCommit(oldCommit)
            .comparisonType(ComparisonType.againstOtherPatchSet())
            .build();
    return getModifiedFiles(params, diffOptions);
  }

  @Override
  public Map<String, ModifiedFile> loadModifiedFiles(
      Project.NameKey project,
      ObjectId oldCommit,
      ObjectId newCommit,
      DiffOptions diffOptions,
      RevWalk revWalk,
      Config repoConfig)
      throws DiffNotAvailableException {
    DiffParameters params =
        DiffParameters.builder()
            .project(project)
            .newCommit(newCommit)
            .baseCommit(oldCommit)
            .comparisonType(ComparisonType.againstOtherPatchSet())
            .build();
    return loadModifiedFilesWithoutCache(project, params, revWalk, repoConfig);
  }

  @Override
  public FileDiffOutput getModifiedFileAgainstParent(
      Project.NameKey project,
      ObjectId newCommit,
      int parent,
      String fileName,
      @Nullable DiffPreferencesInfo.Whitespace whitespace)
      throws DiffNotAvailableException {
    try {
      DiffParameters diffParams = computeDiffParameters(project, newCommit, parent);
      FileDiffCacheKey key =
          createFileDiffCacheKey(
              project,
              diffParams.baseCommit(),
              newCommit,
              fileName,
              DEFAULT_DIFF_ALGORITHM,
              /* useTimeout= */ true,
              whitespace);
      return getModifiedFileForKey(key);
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
    FileDiffCacheKey key =
        createFileDiffCacheKey(
            project,
            oldCommit,
            newCommit,
            fileName,
            DEFAULT_DIFF_ALGORITHM,
            /* useTimeout= */ true,
            whitespace);
    return getModifiedFileForKey(key);
  }

  private ImmutableMap<String, FileDiffOutput> getModifiedFiles(
      DiffParameters diffParams, DiffOptions diffOptions) throws DiffNotAvailableException {
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
              project,
              oldCommit,
              newCommit,
              COMMIT_MSG,
              DEFAULT_DIFF_ALGORITHM,
              /* useTimeout= */ true,
              /* whitespace= */ null));

      if (cmp.isAgainstAutoMerge() || isMergeAgainstParent(cmp, project, newCommit)) {
        fileCacheKeys.add(
            createFileDiffCacheKey(
                project,
                oldCommit,
                newCommit,
                MERGE_LIST,
                DEFAULT_DIFF_ALGORITHM,
                /* useTimeout= */ true,
                /*whitespace = */ null));
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
                        DEFAULT_DIFF_ALGORITHM,
                        /* useTimeout= */ true,
                        /* whitespace= */ null))
            .forEach(fileCacheKeys::add);
      }
      return getModifiedFilesForKeys(fileCacheKeys, diffOptions);
    } catch (IOException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  private FileDiffOutput getModifiedFileForKey(FileDiffCacheKey key)
      throws DiffNotAvailableException {
    Map<String, FileDiffOutput> diffList =
        getModifiedFilesForKeys(ImmutableList.of(key), DiffOptions.DEFAULTS);
    return diffList.containsKey(key.newFilePath())
        ? diffList.get(key.newFilePath())
        : FileDiffOutput.empty(key.newFilePath(), key.oldCommit(), key.newCommit());
  }

  /**
   * Lookup the file diffs for the input {@code keys}. For results where the cache reports negative
   * results, e.g. due to timeouts in the cache loader, this method requests the diff again using
   * the fallback algorithm {@link DiffAlgorithm#HISTOGRAM_NO_FALLBACK}.
   */
  private ImmutableMap<String, FileDiffOutput> getModifiedFilesForKeys(
      List<FileDiffCacheKey> keys, DiffOptions diffOptions) throws DiffNotAvailableException {
    ImmutableMap<FileDiffCacheKey, FileDiffOutput> fileDiffs = fileDiffCache.getAll(keys);
    List<FileDiffCacheKey> fallbackKeys = new ArrayList<>();

    ImmutableList.Builder<FileDiffOutput> result = ImmutableList.builder();

    // Use the fallback diff algorithm for negative results
    for (FileDiffCacheKey key : fileDiffs.keySet()) {
      FileDiffOutput diff = fileDiffs.get(key);
      if (diff.isNegative()) {
        FileDiffCacheKey fallbackKey =
            createFileDiffCacheKey(
                key.project(),
                key.oldCommit(),
                key.newCommit(),
                key.newFilePath(),
                // Use the fallback diff algorithm
                DiffAlgorithm.HISTOGRAM_NO_FALLBACK,
                // We don't enforce timeouts with the fallback algorithm. Timeouts were introduced
                // because of a bug in JGit that happens only when the histogram algorithm uses
                // Myers as fallback. See https://issues.gerritcodereview.com/issues/40000618
                /* useTimeout= */ false,
                key.whitespace());
        fallbackKeys.add(fallbackKey);
      } else {
        result.add(diff);
      }
    }
    result.addAll(fileDiffCache.getAll(fallbackKeys).values());
    return mapByFilePath(result.build(), diffOptions);
  }

  /**
   * Map a collection of {@link FileDiffOutput} based on their file paths. The result map keys
   * represent the old file path for deleted files, or the new path otherwise.
   */
  private ImmutableMap<String, FileDiffOutput> mapByFilePath(
      ImmutableCollection<FileDiffOutput> fileDiffOutputs, DiffOptions diffOptions) {
    ImmutableMap.Builder<String, FileDiffOutput> diffs = ImmutableMap.builder();

    for (FileDiffOutput fileDiffOutput : fileDiffOutputs) {
      if (fileDiffOutput.isEmpty()
          || (diffOptions.skipFilesWithAllEditsDueToRebase() && allDueToRebase(fileDiffOutput))) {
        continue;
      }
      if (fileDiffOutput.changeType() == ChangeType.DELETED) {
        diffs.put(fileDiffOutput.oldPath().get(), fileDiffOutput);
      } else {
        diffs.put(fileDiffOutput.newPath().get(), fileDiffOutput);
      }
    }
    return diffs.build();
  }

  private static boolean allDueToRebase(FileDiffOutput fileDiffOutput) {
    return fileDiffOutput.allEditsDueToRebase()
        && !(fileDiffOutput.changeType() == ChangeType.RENAMED
            || fileDiffOutput.changeType() == ChangeType.COPIED);
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
      DiffAlgorithm diffAlgorithm,
      boolean useTimeout,
      @Nullable Whitespace whitespace) {
    whitespace = whitespace == null ? DEFAULT_WHITESPACE : whitespace;
    return FileDiffCacheKey.builder()
        .project(project)
        .oldCommit(aCommit)
        .newCommit(bCommit)
        .newFilePath(newPath)
        .renameScore(RENAME_SCORE)
        .diffAlgorithm(diffAlgorithm)
        .whitespace(whitespace)
        .useTimeout(useTimeout)
        .build();
  }

  /** Loads the modified file paths between two commits without inspecting the diff cache. */
  private static Map<String, ModifiedFile> loadModifiedFilesWithoutCache(
      Project.NameKey project, DiffParameters diffParams, RevWalk revWalk, Config repoConfig)
      throws DiffNotAvailableException {
    ObjectId newCommit = diffParams.newCommit();
    ObjectId oldCommit = diffParams.baseCommit();
    try {
      ObjectReader reader = revWalk.getObjectReader();
      List<DiffEntry> diffEntries;
      try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        df.setReader(reader, repoConfig);
        df.setDetectRenames(false);
        diffEntries = df.scan(oldCommit.equals(ObjectId.zeroId()) ? null : oldCommit, newCommit);
      }
      List<ModifiedFile> modifiedFiles =
          diffEntries.stream()
              .map(
                  entry ->
                      ModifiedFile.builder()
                          .changeType(toChangeType(entry.getChangeType()))
                          .oldPath(getGitPath(entry.getOldPath()))
                          .newPath(getGitPath(entry.getNewPath()))
                          .build())
              .collect(Collectors.toList());
      return DiffUtil.mergeRewrittenModifiedFiles(modifiedFiles).stream()
          .collect(ImmutableMap.toImmutableMap(ModifiedFile::getDefaultPath, Function.identity()));
    } catch (IOException e) {
      throw new DiffNotAvailableException(
          String.format(
              "Failed to compute the modified files for project '%s',"
                  + " old commit '%s', new commit '%s'.",
              project, oldCommit.name(), newCommit.name()),
          e);
    }
  }

  private static Optional<String> getGitPath(String path) {
    return path.equals(DiffEntry.DEV_NULL) ? Optional.empty() : Optional.of(path);
  }

  private static Patch.ChangeType toChangeType(DiffEntry.ChangeType changeType) {
    if (!changeTypeMap.containsKey(changeType)) {
      throw new IllegalArgumentException("Unsupported type " + changeType);
    }
    return changeTypeMap.get(changeType);
  }

  @AutoValue
  abstract static class DiffParameters {
    abstract Project.NameKey project();

    abstract ObjectId newCommit();

    /**
     * Base commit represents the old commit of the diff. For diffs against the root commit, this
     * should be set to {@link ObjectId#zeroId()}.
     */
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
    if (parent > 0) {
      RevCommit baseCommit = baseCommitUtil.getBaseCommit(project, newCommit, parent);
      if (baseCommit == null) {
        // The specified parent doesn't exist or is not supported, fall back to comparing against
        // the root.
        result.baseCommit(ObjectId.zeroId());
        result.comparisonType(ComparisonType.againstRoot());
        return result.build();
      }

      result.baseCommit(baseCommit);
      result.comparisonType(ComparisonType.againstParent(parent));
      return result.build();
    }
    int numParents = baseCommitUtil.getNumParents(project, newCommit);
    if (numParents == 0) {
      result.baseCommit(ObjectId.zeroId());
      result.comparisonType(ComparisonType.againstRoot());
      return result.build();
    }
    if (numParents == 1) {
      result.baseCommit(baseCommitUtil.getBaseCommit(project, newCommit, parent));
      result.comparisonType(ComparisonType.againstParent(1));
      return result.build();
    }
    if (numParents > 2) {
      logger.atFine().log(
          "Diff against auto-merge for merge commits "
              + "with more than two parents is not supported. Commit %s has %d parents."
              + " Falling back to the diff against the first parent.",
          newCommit, numParents);
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
