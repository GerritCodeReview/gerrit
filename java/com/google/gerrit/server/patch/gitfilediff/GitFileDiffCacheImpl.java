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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.entities.Weighable;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Implementation of the {@link GitFileDiffCache} */
public class GitFileDiffCacheImpl implements GitFileDiffCache {
  private static final String GIT_DIFF = "git_file_diff";

  private static final Map<FileMode, Patch.FileMode> fileModeMap =
      ImmutableMap.of(
          FileMode.TREE,
          Patch.FileMode.TREE,
          FileMode.SYMLINK,
          Patch.FileMode.SYMLINK,
          FileMode.REGULAR_FILE,
          Patch.FileMode.REGULAR_FILE,
          FileMode.EXECUTABLE_FILE,
          Patch.FileMode.EXECUTABLE_FILE,
          FileMode.MISSING,
          Patch.FileMode.MISSING);

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(GitFileDiffCache.class).to(GitFileDiffCacheImpl.class);
        persist(GIT_DIFF, Key.class, GitFileDiff.class)
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

  private final LoadingCache<Key, GitFileDiff> cache;

  @Inject
  public GitFileDiffCacheImpl(@Named(GIT_DIFF) LoadingCache<Key, GitFileDiff> cache) {
    this.cache = cache;
  }

  @Override
  public GitFileDiff get(Key key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public ImmutableMap<Key, GitFileDiff> getAll(Iterable<Key> keys)
      throws DiffNotAvailableException {
    try {
      return cache.getAll(keys);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class Loader extends CacheLoader<Key, GitFileDiff> {
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
    public GitFileDiff load(Key key) throws IOException {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<Key, GitFileDiff> loadAll(Iterable<? extends Key> keys) throws IOException {
      ImmutableMap.Builder<Key, GitFileDiff> result =
          ImmutableMap.builderWithExpectedSize(Iterables.size(keys));

      Map<Project.NameKey, List<Key>> byProject =
          Streams.stream(keys).distinct().collect(Collectors.groupingBy(Key::project));

      for (Map.Entry<Project.NameKey, List<Key>> entry : byProject.entrySet()) {
        try (Repository repo = repoManager.openRepository(entry.getKey());
            ObjectReader reader = repo.newObjectReader()) {

          Map<DiffOptions, List<Key>> groupMap =
              entry.getValue().stream().collect(Collectors.groupingBy(DiffOptions::fromKey));

          for (Map.Entry<DiffOptions, List<Key>> group : groupMap.entrySet()) {
            result.putAll(loadForSameDiffOptions(repo, reader, group.getKey(), group.getValue()));
          }
        }
      }
      return result.build();
    }

    /**
     * Loads the git file diffs for all keys of the same repository, and having the same diff
     * options.
     *
     * @return The git file diffs for all input keys.
     */
    private Map<Key, GitFileDiff> loadForSameDiffOptions(
        Repository repo, ObjectReader reader, DiffOptions options, List<Key> keys)
        throws IOException {
      ImmutableMap.Builder<Key, GitFileDiff> result =
          ImmutableMap.builderWithExpectedSize(keys.size());
      Map<Key, String> filePaths = keys.stream().collect(toMap(identity(), Key::newFilePath));
      DiffFormatter formatter = createDiffFormatter(options, repo, reader);
      Map<String, DiffEntry> diffEntries = loadDiffEntries(formatter, options, filePaths.values());
      for (Key key : filePaths.keySet()) {
        String newFilePath = filePaths.get(key);
        if (!diffEntries.containsKey(newFilePath)) {
          result.put(key, GitFileDiff.empty());
        } else {
          result.put(key, toGitFileDiff(diffEntries.get(newFilePath), formatter));
        }
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
        RawTextComparator cmp = DiffUtil.comparatorFor(diffOptions.whitespace());
        diffFormatter.setDiffComparator(cmp);
        if (diffOptions.renameScore() != -1) {
          diffFormatter.setDetectRenames(true);
          diffFormatter.getRenameDetector().setRenameScore(diffOptions.renameScore());
        }
        diffFormatter.setDiffAlgorithm(DiffAlgorithmFactory.create(diffOptions.diffAlgorithm()));
        return diffFormatter;
      }
    }

    private GitFileDiff toGitFileDiff(DiffEntry diffEntry, DiffFormatter diffFormatter)
        throws IOException {
      FileHeader fileHeader = diffFormatter.toFileHeader(diffEntry);
      List<Edit> edits = fileHeader.toEditList();

      Patch.ChangeType changeType = FileHeaderUtil.getChangeType(fileHeader);

      return GitFileDiff.builder()
          .edits(ImmutableList.copyOf(edits))
          .fileHeader(new String(FileHeaderUtil.toByteArray(fileHeader), UTF_8))
          .oldPath(FileHeaderUtil.getOldPath(fileHeader, changeType))
          .newPath(FileHeaderUtil.getNewPath(fileHeader, changeType))
          .changeType(changeType)
          .patchType(FileHeaderUtil.getPatchType(fileHeader))
          .oldId(Optional.of(diffEntry.getOldId()))
          .newId(Optional.of(diffEntry.getNewId()))
          .oldMode(Optional.of(mapFileMode(diffEntry.getOldMode())))
          .newMode(Optional.of(mapFileMode(diffEntry.getNewMode())))
          .build();
    }

    private Patch.FileMode mapFileMode(FileMode jgitFileMode) {
      if (!fileModeMap.containsKey(jgitFileMode)) {
        throw new IllegalArgumentException("Unsupported type " + jgitFileMode);
      }
      return fileModeMap.get(jgitFileMode);
    }
  }

  @AutoValue
  abstract static class DiffOptions {
    static DiffOptions create(
        ObjectId oldTree,
        ObjectId newTree,
        Integer renameScore,
        Whitespace whitespace,
        DiffAlgorithm diffAlgorithm) {
      return new AutoValue_GitFileDiffCacheImpl_DiffOptions(
          oldTree, newTree, renameScore, whitespace, diffAlgorithm);
    }

    static DiffOptions fromKey(Key key) {
      return create(
          key.oldTree(), key.newTree(), key.renameScore(), key.whitespace(), key.diffAlgorithm());
    }

    abstract ObjectId oldTree();

    abstract ObjectId newTree();

    abstract Integer renameScore();

    abstract Whitespace whitespace();

    abstract DiffAlgorithm diffAlgorithm();
  }

  // TODO(ghareeb): Implement a key protobuf serializer
  @AutoValue
  public abstract static class Key implements Weighable {

    public abstract Project.NameKey project();

    public abstract ObjectId oldTree();

    public abstract ObjectId newTree();

    public abstract String newFilePath();

    public abstract Integer renameScore();

    public abstract DiffAlgorithm diffAlgorithm();

    public abstract DiffPreferencesInfo.Whitespace whitespace();

    @Override
    public int weight() {
      // TODO(ghareeb): implement proper weight method
      return 1;
    }

    public static Builder builder() {
      return new AutoValue_GitFileDiffCacheImpl_Key.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder project(NameKey value);

      public abstract Builder oldTree(ObjectId value);

      public abstract Builder newTree(ObjectId value);

      public abstract Builder newFilePath(String value);

      public abstract Builder renameScore(Integer value);

      public Builder disableRenameDetection() {
        renameScore(-1);
        return this;
      }

      public abstract Builder diffAlgorithm(DiffAlgorithm value);

      public abstract Builder whitespace(Whitespace value);

      public abstract Key build();
    }
  }
}
