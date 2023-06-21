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

package com.google.gerrit.server.patch.gitfilediff;

import static java.util.function.Function.identity;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.patch.DiffExecutor;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.util.git.CloseablePool;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Implementation of the {@link GitFileDiffCache} */
@Singleton
public class GitFileDiffCacheImpl implements GitFileDiffCache {
  private static final String GIT_DIFF = "git_file_diff";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(GitFileDiffCache.class).to(GitFileDiffCacheImpl.class);
        persist(GIT_DIFF, GitFileDiffCacheKey.class, GitFileDiff.class)
            .maximumWeight(10 << 20)
            .weigher(GitFileDiffWeigher.class)
            .keySerializer(GitFileDiffCacheKey.Serializer.INSTANCE)
            .valueSerializer(GitFileDiff.Serializer.INSTANCE)
            .version(3)
            .loader(GitFileDiffCacheImpl.Loader.class);
      }
    };
  }

  @Singleton
  static class Metrics {
    final Counter0 timeouts;

    @Inject
    Metrics(MetricMaker metricMaker) {
      timeouts =
          metricMaker.newCounter(
              "caches/diff/timeouts",
              new Description(
                      "Total number of git file diff computations that resulted in timeouts.")
                  .setRate()
                  .setUnit("count"));
    }
  }

  /** Enum for the supported diff algorithms for the file diff computation. */
  public enum DiffAlgorithm {
    HISTOGRAM_WITH_FALLBACK_MYERS,
    HISTOGRAM_NO_FALLBACK
  }

  /** Creates a new JGit diff algorithm instance using the Gerrit's {@link DiffAlgorithm} enum. */
  public static class DiffAlgorithmFactory {
    public static org.eclipse.jgit.diff.DiffAlgorithm create(DiffAlgorithm diffAlgorithm) {
      HistogramDiff result = new HistogramDiff();
      if (diffAlgorithm.equals(DiffAlgorithm.HISTOGRAM_NO_FALLBACK)) {
        result.setFallbackAlgorithm(null);
      }
      return result;
    }
  }

  private final LoadingCache<GitFileDiffCacheKey, GitFileDiff> cache;

  @Inject
  public GitFileDiffCacheImpl(
      @Named(GIT_DIFF) LoadingCache<GitFileDiffCacheKey, GitFileDiff> cache) {
    this.cache = cache;
  }

  @Override
  public GitFileDiff get(GitFileDiffCacheKey key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public ImmutableMap<GitFileDiffCacheKey, GitFileDiff> getAll(Iterable<GitFileDiffCacheKey> keys)
      throws DiffNotAvailableException {
    try {
      return cache.getAll(keys);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class Loader extends CacheLoader<GitFileDiffCacheKey, GitFileDiff> {
    private final GitRepositoryManager repoManager;
    private final ExecutorService diffExecutor;
    private final long timeoutMillis;
    private final Metrics metrics;

    @Inject
    public Loader(
        @GerritServerConfig Config cfg,
        GitRepositoryManager repoManager,
        @DiffExecutor ExecutorService de,
        Metrics metrics) {
      this.repoManager = repoManager;
      this.diffExecutor = de;
      this.timeoutMillis =
          ConfigUtil.getTimeUnit(
              cfg,
              "cache",
              GIT_DIFF,
              "timeout",
              TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
              TimeUnit.MILLISECONDS);
      this.metrics = metrics;
    }

    @Override
    public GitFileDiff load(GitFileDiffCacheKey key) throws IOException, DiffNotAvailableException {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading a single key from git file diff cache",
              Metadata.builder()
                  .diffAlgorithm(key.diffAlgorithm().name())
                  .filePath(key.newFilePath())
                  .build())) {
        return loadAll(ImmutableList.of(key)).get(key);
      }
    }

    @Override
    public Map<GitFileDiffCacheKey, GitFileDiff> loadAll(
        Iterable<? extends GitFileDiffCacheKey> keys)
        throws IOException, DiffNotAvailableException {
      try (TraceTimer timer =
          TraceContext.newTimer("Loading multiple keys from git file diff cache")) {
        ImmutableMap.Builder<GitFileDiffCacheKey, GitFileDiff> result =
            ImmutableMap.builderWithExpectedSize(Iterables.size(keys));

        Map<Project.NameKey, List<GitFileDiffCacheKey>> byProject =
            Streams.stream(keys)
                .distinct()
                .collect(Collectors.groupingBy(GitFileDiffCacheKey::project));

        for (Map.Entry<Project.NameKey, List<GitFileDiffCacheKey>> entry : byProject.entrySet()) {
          try (Repository repo = repoManager.openRepository(entry.getKey())) {

            // Grouping keys by diff options because each group of keys will be processed with a
            // separate call to JGit using the DiffFormatter object.
            Map<DiffOptions, List<GitFileDiffCacheKey>> optionsGroups =
                entry.getValue().stream().collect(Collectors.groupingBy(DiffOptions::fromKey));

            for (Map.Entry<DiffOptions, List<GitFileDiffCacheKey>> group :
                optionsGroups.entrySet()) {
              result.putAll(loadAllImpl(repo, group.getKey(), group.getValue()));
            }
          }
        }
        return result.build();
      }
    }

    /**
     * Loads the git file diffs for all keys of the same repository, and having the same diff {@code
     * options}.
     *
     * @return The git file diffs for all input keys.
     */
    private Map<GitFileDiffCacheKey, GitFileDiff> loadAllImpl(
        Repository repo, DiffOptions options, List<GitFileDiffCacheKey> keys)
        throws IOException, DiffNotAvailableException {
      ImmutableMap.Builder<GitFileDiffCacheKey, GitFileDiff> result =
          ImmutableMap.builderWithExpectedSize(keys.size());
      Map<GitFileDiffCacheKey, String> filePaths =
          keys.stream().collect(Collectors.toMap(identity(), GitFileDiffCacheKey::newFilePath));
      try (CloseablePool<DiffFormatter> diffPool =
          new CloseablePool<>(() -> createDiffFormatter(options, repo))) {
        ListMultimap<String, DiffEntry> diffEntries;
        try (CloseablePool<DiffFormatter>.Handle formatter = diffPool.get()) {
          diffEntries = loadDiffEntries(formatter.get(), options, filePaths.values());
        }
        for (GitFileDiffCacheKey key : filePaths.keySet()) {
          String newFilePath = filePaths.get(key);
          if (!diffEntries.containsKey(newFilePath)) {
            result.put(
                key,
                GitFileDiff.empty(
                    AbbreviatedObjectId.fromObjectId(key.oldTree()),
                    AbbreviatedObjectId.fromObjectId(key.newTree()),
                    newFilePath));
            continue;
          }
          List<DiffEntry> entries = diffEntries.get(newFilePath);
          if (entries.size() == 1) {
            result.put(key, createGitFileDiff(entries.get(0), key, diffPool));
          } else {
            // Handle when JGit returns two {Added, Deleted} entries for the same file. This
            // happens, for example, when a file's mode is changed between patchsets (e.g.
            // converting a symlink to a regular file). We combine both diff entries into a single
            // entry with {changeType = Rewrite}.
            List<GitFileDiff> gitDiffs = new ArrayList<>();
            for (DiffEntry entry : diffEntries.get(newFilePath)) {
              gitDiffs.add(createGitFileDiff(entry, key, diffPool));
            }
            result.put(key, createRewriteEntry(gitDiffs));
          }
        }
        return result.build();
      }
    }

    private static ListMultimap<String, DiffEntry> loadDiffEntries(
        DiffFormatter diffFormatter, DiffOptions diffOptions, Collection<String> filePaths)
        throws IOException {
      Set<String> filePathsSet = ImmutableSet.copyOf(filePaths);
      List<DiffEntry> diffEntries =
          diffFormatter.scan(
              diffOptions.oldTree().equals(ObjectId.zeroId()) ? null : diffOptions.oldTree(),
              diffOptions.newTree());

      return diffEntries.stream()
          .filter(d -> filePathsSet.contains(extractPath(d)))
          .collect(
              Multimaps.toMultimap(
                  Loader::extractPath,
                  identity(),
                  MultimapBuilder.treeKeys().arrayListValues()::build));
    }

    private static DiffFormatter createDiffFormatter(DiffOptions diffOptions, Repository repo) {
      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setRepository(repo);
        RawTextComparator cmp = comparatorFor(diffOptions.whitespace());
        diffFormatter.setDiffComparator(cmp);
        if (diffOptions.renameScore() != -1) {
          diffFormatter.setDetectRenames(true);
          diffFormatter.getRenameDetector().setRenameScore(diffOptions.renameScore());
        }
        diffFormatter.setDiffAlgorithm(DiffAlgorithmFactory.create(diffOptions.diffAlgorithm()));
        diffFormatter.getRenameDetector().setSkipContentRenamesForBinaryFiles(true);
        return diffFormatter;
      }
    }

    private static RawTextComparator comparatorFor(Whitespace ws) {
      switch (ws) {
        case IGNORE_ALL:
          return RawTextComparator.WS_IGNORE_ALL;

        case IGNORE_TRAILING:
          return RawTextComparator.WS_IGNORE_TRAILING;

        case IGNORE_LEADING_AND_TRAILING:
          return RawTextComparator.WS_IGNORE_CHANGE;

        case IGNORE_NONE:
        default:
          return RawTextComparator.DEFAULT;
      }
    }

    /**
     * Create a {@link GitFileDiff}. The result depends on the value of the {@code useTimeout} field
     * of the {@code key} parameter.
     *
     * <ul>
     *   <li>If {@code useTimeout} is true, the computation is performed with timeout enforcement
     *       (identified by {@link #timeoutMillis}). If the timeout is exceeded, this method returns
     *       a negative result using {@link GitFileDiff#createNegative(AbbreviatedObjectId,
     *       AbbreviatedObjectId, String)}.
     *   <li>If {@code useTimeouts} is false, the computation is performed synchronously without
     *       timeout enforcement.
     */
    private GitFileDiff createGitFileDiff(
        DiffEntry diffEntry, GitFileDiffCacheKey key, CloseablePool<DiffFormatter> diffPool)
        throws IOException {
      if (!key.useTimeout()) {
        try (CloseablePool<DiffFormatter>.Handle formatter = diffPool.get()) {
          FileHeader fileHeader = formatter.get().toFileHeader(diffEntry);
          return GitFileDiff.create(diffEntry, fileHeader);
        }
      }
      // This submits the DiffFormatter to a different thread. The CloseablePool and our usage of it
      // ensures that any DiffFormatter instance and the ObjectReader it references internally is
      // only used by a single thread concurrently. However, ObjectReaders have a reference to
      // Repository which might not be thread safe (FileRepository is, DfsRepository might not).
      // This could lead to a race condition.
      Future<GitFileDiff> fileDiffFuture =
          diffExecutor.submit(
              () -> {
                try (CloseablePool<DiffFormatter>.Handle formatter = diffPool.get()) {
                  return GitFileDiff.create(diffEntry, formatter.get().toFileHeader(diffEntry));
                }
              });
      try {
        // We employ the timeout because of a bug in Myers diff in JGit. See
        // https://issues.gerritcodereview.com/issues/40000618 for more details. The bug may happen
        // if the algorithm used in diffs is HISTOGRAM_WITH_FALLBACK_MYERS.
        return fileDiffFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException | TimeoutException e) {
        // If timeout happens, create a negative result
        metrics.timeouts.increment();
        return GitFileDiff.createNegative(
            AbbreviatedObjectId.fromObjectId(key.oldTree()),
            AbbreviatedObjectId.fromObjectId(key.newTree()),
            key.newFilePath());
      } catch (ExecutionException e) {
        // If there was an error computing the result, carry it
        // up to the caller so the cache knows this key is invalid.
        Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
        throw new IOException(e.getMessage(), e.getCause());
      }
    }

    /**
     * Extract the file path from a {@link DiffEntry}. Returns the old file path if the entry
     * corresponds to a deleted file, otherwise it returns the new file path.
     */
    private static String extractPath(DiffEntry diffEntry) {
      return diffEntry.getChangeType().equals(ChangeType.DELETE)
          ? diffEntry.getOldPath()
          : diffEntry.getNewPath();
    }
  }

  /**
   * Create a single {@link GitFileDiff} with {@link com.google.gerrit.entities.Patch.ChangeType}
   * equals {@link com.google.gerrit.entities.Patch.ChangeType#REWRITE}, assuming the input list
   * contains two entries.
   *
   * @param gitDiffs input list of exactly two {@link GitFileDiff} for same file path.
   * @return a single {@link GitFileDiff} with change type equals {@link
   *     com.google.gerrit.entities.Patch.ChangeType#REWRITE}.
   * @throws DiffNotAvailableException if input list contains git diffs with change types other than
   *     {ADDED, DELETED}. This is a JGit error.
   */
  private static GitFileDiff createRewriteEntry(List<GitFileDiff> gitDiffs)
      throws DiffNotAvailableException {
    if (gitDiffs.size() != 2) {
      throw new DiffNotAvailableException(
          String.format(
              "JGit error: found %d dff entries for same file path %s",
              gitDiffs.size(), gitDiffs.get(0).getDefaultPath()));
    }
    // Convert the first entry (prioritized according to change type enum order) to REWRITE
    gitDiffs.sort(Comparator.comparingInt(o -> o.changeType().ordinal()));
    return gitDiffs.get(0).toBuilder().changeType(Patch.ChangeType.REWRITE).build();
  }

  /** An entity representing the options affecting the diff computation. */
  @AutoValue
  abstract static class DiffOptions {
    /** Convert a {@link GitFileDiffCacheKey} input to a {@link DiffOptions}. */
    static DiffOptions fromKey(GitFileDiffCacheKey key) {
      return create(
          key.oldTree(), key.newTree(), key.renameScore(), key.whitespace(), key.diffAlgorithm());
    }

    private static DiffOptions create(
        ObjectId oldTree,
        ObjectId newTree,
        Integer renameScore,
        Whitespace whitespace,
        DiffAlgorithm diffAlgorithm) {
      return new AutoValue_GitFileDiffCacheImpl_DiffOptions(
          oldTree, newTree, renameScore, whitespace, diffAlgorithm);
    }

    abstract ObjectId oldTree();

    abstract ObjectId newTree();

    abstract Integer renameScore();

    abstract Whitespace whitespace();

    abstract DiffAlgorithm diffAlgorithm();
  }
}
