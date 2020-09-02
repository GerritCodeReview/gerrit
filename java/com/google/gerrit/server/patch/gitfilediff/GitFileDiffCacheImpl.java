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
//
//
//

package com.google.gerrit.server.patch.gitfilediff;

import static java.nio.charset.StandardCharsets.UTF_8;

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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Caches the pure Git diff for a single file path between two commits. The diff includes the
 * line-based edits within the file, the total number of inserted and deleted lines, as well as a
 * formatted file header.
 */
public class GitFileDiffCacheImpl implements GitFileDiffCache {
  private static final String GIT_DIFF = "git_file_diff";

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
          return MyersDiff.INSTANCE;
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
     * The {@link Key} contains a single file path identifying the file we want to get the diff for.
     * Clients should pass the new file path for all change types except DELETE, for which they pass
     * the old file path.
     *
     * <p>This pathExtractor is used for matching the files that we get from the git tree diff
     * (through {@link DiffFormatter} with the file path in the key.
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

      Map<Project.NameKey, List<Key>> byProject = groupByProject(keys);

      for (Project.NameKey project : byProject.keySet()) {
        try (Repository repo = repoManager.openRepository(project);
            ObjectReader reader = repo.newObjectReader()) {

          Map<Pair<ObjectId, ObjectId>, List<Key>> byTreePairs =
              groupByTreeIds(byProject.get(project));

          for (Pair<ObjectId, ObjectId> treeIds : byTreePairs.keySet()) {
            result.putAll(
                loadForSameTreeIds(
                    byTreePairs.get(treeIds), repo, reader, treeIds.getLeft(), treeIds.getRight()));
          }
        }
      }
      return result.build();
    }

    /**
     * Loads the git file diffs for all keys of the same repository, and having the same {@code
     * oldTreeId} and {@code newTreeId}.
     *
     * @return The git file diffs for all input keys.
     */
    private Map<Key, GitFileDiff> loadForSameTreeIds(
        List<Key> keys,
        Repository repo,
        ObjectReader reader,
        ObjectId oldTreeId,
        ObjectId newTreeId)
        throws IOException {
      Map<Key, GitFileDiff> result = new HashMap<>();
      Map<Pair<Whitespace, DiffAlgorithm>, List<Key>> grouped =
          groupByWhitespaceAndDiffAlgorithm(keys);
      for (Pair<Whitespace, DiffAlgorithm> group : grouped.keySet()) {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
          Map<Key, String> keysToFilePaths =
              grouped.get(group).stream()
                  .collect(Collectors.toMap(Function.identity(), Key::newFilePath));
          Map<String, DiffEntry> diffEntries =
              getGitTreeDiffForPaths(
                  repo,
                  reader,
                  diffFormatter,
                  group.getLeft(),
                  group.getRight(),
                  oldTreeId,
                  newTreeId,
                  ImmutableSet.copyOf(keysToFilePaths.values()));
          for (Key key : keysToFilePaths.keySet()) {
            String newFilePath = keysToFilePaths.get(key);
            if (!diffEntries.containsKey(newFilePath)) {
              result.put(key, GitFileDiff.empty());
            } else {
              result.put(key, toGitFileDiff(diffEntries.get(newFilePath), diffFormatter));
            }
          }
        }
      }
      return result;
    }

    private static Map<String, DiffEntry> getGitTreeDiffForPaths(
        Repository repo,
        ObjectReader reader,
        DiffFormatter df,
        DiffPreferencesInfo.Whitespace whitespace,
        DiffAlgorithm diffAlgorithm,
        ObjectId oldTree,
        ObjectId newTree,
        Set<String> filePaths)
        throws IOException {
      df.setReader(reader, repo.getConfig());
      RawTextComparator cmp = DiffUtil.comparatorFor(whitespace);
      df.setDiffComparator(cmp);
      // TODO(ghareeb): Adjust rename detection. Should vary according to the key
      df.setDetectRenames(true);
      df.setDiffAlgorithm(DiffAlgorithmFactory.create(diffAlgorithm));

      List<DiffEntry> diffEntries = df.scan(oldTree, newTree);

      return diffEntries.stream()
          .filter(d -> filePaths.contains(pathExtractor.apply(d)))
          .collect(Collectors.toMap(d -> pathExtractor.apply(d), Function.identity()));
    }

    private Map<Project.NameKey, List<Key>> groupByProject(Iterable<? extends Key> keys) {
      return Streams.stream(keys).distinct().collect(Collectors.groupingBy(Key::project));
    }

    private Map<Pair<ObjectId, ObjectId>, List<Key>> groupByTreeIds(List<Key> keys) {
      return keys.stream()
          .collect(Collectors.groupingBy(k -> ImmutablePair.of(k.oldTree(), k.newTree())));
    }

    private Map<Pair<Whitespace, DiffAlgorithm>, List<Key>> groupByWhitespaceAndDiffAlgorithm(
        List<Key> keys) {
      return keys.stream()
          .collect(
              Collectors.groupingBy(
                  key -> ImmutablePair.of(key.whitespace(), key.diffAlgorithm())));
    }

    private GitFileDiff toGitFileDiff(DiffEntry diffEntry, DiffFormatter df) throws IOException {
      FileHeader fileHeader = df.toFileHeader(diffEntry);
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
      if (jgitFileMode.equals(FileMode.TREE)) {
        return Patch.FileMode.TREE;
      } else if (jgitFileMode.equals(FileMode.SYMLINK)) {
        return Patch.FileMode.SYMLINK;
      } else if (jgitFileMode.equals(FileMode.REGULAR_FILE)) {
        return Patch.FileMode.REGULAR_FILE;
      } else if (jgitFileMode.equals(FileMode.EXECUTABLE_FILE)) {
        return Patch.FileMode.EXECUTABLE_FILE;
      } else if (jgitFileMode.equals(FileMode.GITLINK)) {
        return Patch.FileMode.GITLINK;
      } else if (jgitFileMode.equals(FileMode.MISSING)) {
        return Patch.FileMode.MISSING;
      } else {
        throw new IllegalArgumentException("Unsupported type " + jgitFileMode);
      }
    }
  }

  // TODO(ghareeb): Implement a key protobuf serializer
  @AutoValue
  public abstract static class Key implements Serializable {

    public abstract Project.NameKey project();

    public abstract ObjectId oldTree();

    public abstract ObjectId newTree();

    public abstract String newFilePath();

    public abstract Integer renameScore();

    public abstract DiffAlgorithm diffAlgorithm();

    public abstract DiffPreferencesInfo.Whitespace whitespace();

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
