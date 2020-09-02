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

package com.google.gerrit.server.patch;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.GitFileDiffCache.Loader.FileHeaderFactory;
import com.google.gerrit.server.patch.GitFileDiffCache.Loader.FileSizeFactory;
import com.google.gerrit.server.patch.entities.FileHeader;
import com.google.gerrit.server.patch.entities.GitFileDiff;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Caches the pure Git diff for a single file path between two commits. The diff includes the
 * line-based edits within the file, the total number of inserted and deleted lines, as well as a
 * formatted file header.
 */
public class GitFileDiffCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String GIT_DIFF = "git_diff";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(GIT_DIFF, Key.class, GitFileDiff.class)
            .maximumWeight(10 << 20)
            .loader(GitFileDiffCache.Loader.class);

        factory(FileHeaderFactory.Factory.class);
        factory(FileSizeFactory.Factory.class);
      }
    };
  }

  private final LoadingCache<Key, GitFileDiff> cache;

  @Inject
  public GitFileDiffCache(@Named(GIT_DIFF) LoadingCache<Key, GitFileDiff> cache) {
    this.cache = cache;
  }

  public GitFileDiff get(Key key) throws ExecutionException {
    return cache.get(key);
  }

  public ImmutableMap<Key, GitFileDiff> getAll(Collection<Key> keys) throws ExecutionException {
    return cache.getAll(keys);
  }

  static class Loader extends CacheLoader<Key, GitFileDiff> {
    private final GitRepositoryManager repoManager;
    private final DiffUtil diffUtil;
    private final FileHeaderFactory.Factory fileHeaderFactory;
    private final FileSizeFactory.Factory fileSizeFactory;

    @Inject
    public Loader(
        GitRepositoryManager repoManager,
        DiffUtil diffUtil,
        FileHeaderFactory.Factory fileHeaderFactory,
        FileSizeFactory.Factory fileSizeFactory) {
      this.repoManager = repoManager;
      this.diffUtil = diffUtil;
      this.fileHeaderFactory = fileHeaderFactory;
      this.fileSizeFactory = fileSizeFactory;
    }

    @Override
    public GitFileDiff load(Key key) throws IOException {
      Project.NameKey project = key.project();
      try (Repository repo = repoManager.openRepository(project);
          ObjectReader reader = repo.newObjectReader();
          RevWalk rw = new RevWalk(reader);
          DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE); ) {

        return loadForKey(key, repo, reader, rw, df, project);
      }
    }

    @Override
    public Map<Key, GitFileDiff> loadAll(Iterable<? extends Key> keys) throws IOException {
      Map<Key, GitFileDiff> result = new HashMap<>();
      List<Key> keyList = Lists.newArrayList(keys);
      Map<Project.NameKey, List<Key>> keysByProject =
          keyList.stream().collect(Collectors.groupingBy(Key::project));

      for (Project.NameKey project : keysByProject.keySet()) {
        try (Repository repo = repoManager.openRepository(project);
            ObjectReader reader = repo.newObjectReader();
            RevWalk rw = new RevWalk(reader);
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE); ) {
          for (Key key : keysByProject.get(project)) {
            result.put(key, loadForKey(key, repo, reader, rw, df, project));
          }
        }
      }
      return result;
    }

    private GitFileDiff loadForKey(
        Key key,
        Repository repo,
        ObjectReader reader,
        RevWalk rw,
        DiffFormatter df,
        Project.NameKey project)
        throws IOException {
      Optional<DiffEntry> diffEntry = diffUtil.getOneGitTreeDiff(repo, reader, df, key);

      if (!diffEntry.isPresent()) {
        return GitFileDiff.empty();
      }

      DiffEntry entry = diffEntry.get();

      return loadFromDiffEntry(project, entry, df, rw, reader, key);
    }

    private GitFileDiff loadFromDiffEntry(
        Project.NameKey project,
        DiffEntry entry,
        DiffFormatter df,
        RevWalk rw,
        ObjectReader reader,
        Key key)
        throws IOException {
      org.eclipse.jgit.patch.FileHeader fileHeader =
          fileHeaderFactory.create(project, df).fromDiffEntry(entry);
      List<Edit> edits = fileHeader.toEditList();

      String oldName = entry.getOldPath();
      String newName = entry.getNewPath();

      RevTree aTree = rw.parseTree(key.oldTree());
      RevTree bTree = rw.parseTree(key.newTree());

      Long oldSize =
          fileSizeFactory.create(reader, aTree).get(entry.getOldId(), entry.getOldMode(), oldName);

      Long newSize =
          fileSizeFactory.create(reader, bTree).get(entry.getNewId(), entry.getNewMode(), newName);

      return GitFileDiff.create(
          edits, FileHeader.create(fileHeader), oldName, newName, oldSize, newSize);
    }

    static class FileHeaderFactory {
      private Project.NameKey project;
      private DiffFormatter df;
      private ExecutorService diffExecutor;
      private long timeoutMillis;

      interface Factory {
        FileHeaderFactory create(Project.NameKey project, DiffFormatter df);
      }

      @Inject
      FileHeaderFactory(
          @DiffExecutor ExecutorService de,
          @GerritServerConfig Config cfg,
          @Assisted Project.NameKey project,
          @Assisted DiffFormatter df) {
        this.project = project;
        this.df = df;
        this.diffExecutor = de;
        // TODO(ghareeb): configure the timeout here
        timeoutMillis =
            ConfigUtil.getTimeUnit(
                cfg,
                "cache",
                "sth", // PatchListCacheImpl.FILE_NAME,
                "timeout",
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
                TimeUnit.MILLISECONDS);
      }

      org.eclipse.jgit.patch.FileHeader fromDiffEntry(DiffEntry diffEntry) throws IOException {
        Future<org.eclipse.jgit.patch.FileHeader> result =
            diffExecutor.submit(
                () -> {
                  synchronized (diffEntry) {
                    return df.toFileHeader(diffEntry);
                  }
                });

        try {
          return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException e) {
          logger.atWarning().log(
              "%s ms timeout reached for Diff loader in project %s"
                  + " on path %s comparing %s..%s",
              timeoutMillis,
              project,
              diffEntry.getNewPath(),
              diffEntry.getOldId().name(),
              diffEntry.getNewId().name());
          result.cancel(true);
          synchronized (diffEntry) {
            return toFileHeaderWithoutMyersDiff(df, diffEntry);
          }
        } catch (ExecutionException e) {
          // If there was an error computing the result, carry it
          // up to the caller so the cache knows this key is invalid.
          Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
          throw new IOException(e.getMessage(), e.getCause());
        }
      }

      private org.eclipse.jgit.patch.FileHeader toFileHeaderWithoutMyersDiff(
          DiffFormatter diffFormatter, DiffEntry diffEntry) throws IOException {
        HistogramDiff histogramDiff = new HistogramDiff();
        histogramDiff.setFallbackAlgorithm(null);
        diffFormatter.setDiffAlgorithm(histogramDiff);
        return diffFormatter.toFileHeader(diffEntry);
      }
    }

    static class FileSizeFactory {
      private final ObjectReader reader;
      private final RevTree tree;

      interface Factory {
        FileSizeFactory create(ObjectReader reader, RevTree tree);
      }

      @Inject
      FileSizeFactory(@Assisted ObjectReader reader, @Assisted RevTree tree) {
        this.reader = reader;
        this.tree = tree;
      }

      private long get(AbbreviatedObjectId abbreviatedId, FileMode mode, String path)
          throws IOException {
        if (!isBlob(mode)) {
          return 0;
        }
        ObjectId fileId =
            toObjectId(reader, abbreviatedId).orElseGet(() -> lookupObjectId(reader, path, tree));
        if (ObjectId.zeroId().equals(fileId)) {
          return 0;
        }
        return reader.getObjectSize(fileId, OBJ_BLOB);
      }

      private static ObjectId lookupObjectId(ObjectReader reader, String path, RevTree tree) {
        // This variant is very expensive.
        try (TreeWalk treeWalk = TreeWalk.forPath(reader, path, tree)) {
          return treeWalk != null ? treeWalk.getObjectId(0) : ObjectId.zeroId();
        } catch (IOException e) {
          throw new StorageException(e);
        }
      }

      private static Optional<ObjectId> toObjectId(
          ObjectReader reader, AbbreviatedObjectId abbreviatedId) throws IOException {
        if (abbreviatedId == null) {
          // In theory, DiffEntry#getOldId or DiffEntry#getNewId can be null for pure renames or
          // pure
          // mode changes (e.g. DiffEntry#modify doesn't set the IDs). However, the method we call
          // for diffs (DiffFormatter#scan) seems to always produce DiffEntries with set IDs, even
          // for
          // pure renames.
          return Optional.empty();
        }
        if (abbreviatedId.isComplete()) {
          // With the current JGit version and the method we call for diffs (DiffFormatter#scan),
          // this
          // is the only code path taken right now.
          return Optional.ofNullable(abbreviatedId.toObjectId());
        }
        Collection<ObjectId> objectIds = reader.resolve(abbreviatedId);
        // It seems very unlikely that an ObjectId which was just abbreviated by the diff
        // computation
        // now can't be resolved to exactly one ObjectId. The API allows this possibility, though.
        return objectIds.size() == 1
            ? Optional.of(Iterables.getOnlyElement(objectIds))
            : Optional.empty();
      }

      private static boolean isBlob(FileMode mode) {
        int t = mode.getBits() & FileMode.TYPE_MASK;
        return t == FileMode.TYPE_FILE || t == FileMode.TYPE_SYMLINK;
      }
    }
  }

  @AutoValue
  public abstract static class Key implements Serializable {
    public enum DiffAlgorithm {
      MYERS;
    }

    public static Key create(
        Project.NameKey project,
        ObjectId oldTree,
        ObjectId newTree,
        String newFilePath,
        @Nullable Integer similarityLevel,
        @Nullable DiffAlgorithm diffAlgorithm,
        DiffPreferencesInfo.Whitespace ws) {
      return new AutoValue_GitFileDiffCache_Key(
          project, oldTree, newTree, newFilePath, similarityLevel, diffAlgorithm, ws);
    }

    public abstract Project.NameKey project();

    public abstract ObjectId oldTree();

    public abstract ObjectId newTree();

    public abstract String newFilePath();

    @Nullable
    public abstract Integer similarityLevel();

    @Nullable
    public abstract DiffAlgorithm diffAlgorithm();

    public abstract DiffPreferencesInfo.Whitespace ws();
  }
}
