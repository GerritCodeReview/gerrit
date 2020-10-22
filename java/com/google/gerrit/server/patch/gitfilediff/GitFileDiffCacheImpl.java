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

package com.google.gerrit.server.patch.gitfilediff;

import static java.util.function.Function.identity;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Implementation of the {@link GitFileDiffCache} */
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
            .valueSerializer(GitFileDiff.Serializer.INSTANCE)
            .loader(GitFileDiffCacheImpl.Loader.class);
      }
    };
  }

  /** Enum for the supported diff algorithms for the file diff computation. */
  public enum DiffAlgorithm {
    HISTOGRAM,
    HISTOGRAM_WITHOUT_MYERS_FALLBACK
  }

  /** Creates a new JGit diff algorithm instance using the Gerrit's {@link DiffAlgorithm} enum. */
  public static class DiffAlgorithmFactory {
    public static org.eclipse.jgit.diff.DiffAlgorithm create(DiffAlgorithm diffAlgorithm) {
      switch (diffAlgorithm) {
        case HISTOGRAM:
          return new HistogramDiff();
        case HISTOGRAM_WITHOUT_MYERS_FALLBACK:
          HistogramDiff histogramDiff = new HistogramDiff();
          histogramDiff.setFallbackAlgorithm(null);
          return histogramDiff;
      }
      throw new IllegalArgumentException("Unsupported diff algorithm " + diffAlgorithm);
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
    /**
     * Extractor for the file path from a {@link DiffEntry}. Returns the old file path if the entry
     * corresponds to a deleted file, otherwise it returns the new file path.
     */
    private static final Function<DiffEntry, String> pathExtractor =
        (DiffEntry entry) ->
            entry.getChangeType().equals(ChangeType.DELETE)
                ? entry.getOldPath()
                : entry.getNewPath();

    private final GitRepositoryManager repoManager;

    @Inject
    public Loader(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    @Override
    public GitFileDiff load(GitFileDiffCacheKey key) throws IOException {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<GitFileDiffCacheKey, GitFileDiff> loadAll(
        Iterable<? extends GitFileDiffCacheKey> keys) throws IOException {
      ImmutableMap.Builder<GitFileDiffCacheKey, GitFileDiff> result =
          ImmutableMap.builderWithExpectedSize(Iterables.size(keys));

      Map<Project.NameKey, List<GitFileDiffCacheKey>> byProject =
          Streams.stream(keys)
              .distinct()
              .collect(Collectors.groupingBy(GitFileDiffCacheKey::project));

      for (Map.Entry<Project.NameKey, List<GitFileDiffCacheKey>> entry : byProject.entrySet()) {
        try (Repository repo = repoManager.openRepository(entry.getKey());
            ObjectReader reader = repo.newObjectReader()) {

          // Group the input keys by their options (rename score, diff algorithm, etc..).
          Map<DiffOptions, List<GitFileDiffCacheKey>> optionsGroups =
              entry.getValue().stream().collect(Collectors.groupingBy(DiffOptions::fromKey));

          for (Map.Entry<DiffOptions, List<GitFileDiffCacheKey>> group : optionsGroups.entrySet()) {
            result.putAll(loadAll(repo, reader, group.getKey(), group.getValue()));
          }
        }
      }
      return result.build();
    }

    /**
     * Loads the git file diffs for all keys of the same repository, and having the same diff {@code
     * options}.
     *
     * @return The git file diffs for all input keys.
     */
    private Map<GitFileDiffCacheKey, GitFileDiff> loadAll(
        Repository repo, ObjectReader reader, DiffOptions options, List<GitFileDiffCacheKey> keys)
        throws IOException {
      ImmutableMap.Builder<GitFileDiffCacheKey, GitFileDiff> result =
          ImmutableMap.builderWithExpectedSize(keys.size());
      Map<GitFileDiffCacheKey, String> filePaths =
          keys.stream().collect(Collectors.toMap(identity(), GitFileDiffCacheKey::newFilePath));
      DiffFormatter formatter = createDiffFormatter(options, repo, reader);
      Map<String, DiffEntry> diffEntries = loadDiffEntries(formatter, options, filePaths.values());
      for (GitFileDiffCacheKey key : filePaths.keySet()) {
        String newFilePath = filePaths.get(key);
        if (diffEntries.containsKey(newFilePath)) {
          result.put(key, GitFileDiff.create(diffEntries.get(newFilePath), formatter));
          continue;
        }
        result.put(
            key,
            GitFileDiff.empty(
                AbbreviatedObjectId.fromObjectId(key.oldTree()),
                AbbreviatedObjectId.fromObjectId(key.newTree()),
                newFilePath));
      }
      return result.build();
    }

    private static Map<String, DiffEntry> loadDiffEntries(
        DiffFormatter diffFormatter, DiffOptions diffOptions, Collection<String> filePaths)
        throws IOException {
      Set<String> filePathsSet = ImmutableSet.copyOf(filePaths);
      List<DiffEntry> diffEntries =
          diffFormatter.scan(diffOptions.oldTree(), diffOptions.newTree());

      return diffEntries.stream()
          .filter(d -> filePathsSet.contains(pathExtractor.apply(d)))
          .collect(Collectors.toMap(d -> pathExtractor.apply(d), identity()));
    }

    private static DiffFormatter createDiffFormatter(
        DiffOptions diffOptions, Repository repo, ObjectReader reader) {
      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(reader, repo.getConfig());
        RawTextComparator cmp = comparatorFor(diffOptions.whitespace());
        diffFormatter.setDiffComparator(cmp);
        if (diffOptions.renameScore() != -1) {
          diffFormatter.setDetectRenames(true);
          diffFormatter.getRenameDetector().setRenameScore(diffOptions.renameScore());
        }
        diffFormatter.setDiffAlgorithm(DiffAlgorithmFactory.create(diffOptions.diffAlgorithm()));
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
