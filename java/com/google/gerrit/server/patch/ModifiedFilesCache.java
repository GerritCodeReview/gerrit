package com.google.gerrit.server.patch;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.entities.GitModifiedFile;
import com.google.gerrit.server.patch.entities.GitModifiedFilesList;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A cache for the list of Git modified files between 2 commits (patchsets) with extra Gerrit logic.
 *
 * <p>The loader uses the underlying {@link GitModifiedFilesCache} to retrieve the git modified
 * files.
 */
public class ModifiedFilesCache {
  static final String MODIFIED_FILES = "modified_files";

  private final LoadingCache<Key, GitModifiedFilesList> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ModifiedFilesCache.class);

        persist(ModifiedFilesCache.MODIFIED_FILES, Key.class, GitModifiedFilesList.class)
            .maximumWeight(10 << 20)
            .loader(ModifiedFilesLoader.class);
      }
    };
  }

  @Inject
  public ModifiedFilesCache(
      @Named(ModifiedFilesCache.MODIFIED_FILES) LoadingCache<Key, GitModifiedFilesList> cache) {
    this.cache = cache;
  }

  public GitModifiedFilesList get(Key key) throws ExecutionException {
    return cache.get(key);
  }

  static class ModifiedFilesLoader extends CacheLoader<Key, GitModifiedFilesList> {
    private final DiffUtil diffUtil;
    private final GitModifiedFilesCache gitCache;
    private final GitRepositoryManager repoManager;

    @Inject
    ModifiedFilesLoader(
        DiffUtil diffUtil, GitModifiedFilesCache gitCache, GitRepositoryManager repoManager) {
      this.diffUtil = diffUtil;
      this.gitCache = gitCache;
      this.repoManager = repoManager;
    }

    @Override
    public GitModifiedFilesList load(Key key) throws IOException, ExecutionException {
      try (Repository repo = repoManager.openRepository(key.project());
          ObjectReader reader = repo.newObjectReader();
          ObjectInserter ins = diffUtil.newInserter(repo);
          RevWalk rw = new RevWalk(reader)) {
        ImmutableList.Builder<GitModifiedFile> result = ImmutableList.builder();
        result.add(getCommit());
        if (isMergeCommit(rw, key.bCommit())) {
          result.add(getMergeList());
        }
        if (key.includeFiles()) {
          result.addAll(getFiles(key, repo, ins, rw));
        }
        return GitModifiedFilesList.create(result.build());
      }
    }

    private GitModifiedFile getCommit() {
      GitModifiedFile commit =
          GitModifiedFile.create(null, Patch.COMMIT_MSG, Patch.COMMIT_MSG, null, null);
      return commit;
    }

    private GitModifiedFile getMergeList() {
      return GitModifiedFile.create(null, Patch.MERGE_LIST, Patch.MERGE_LIST, null, null);
    }

    private ImmutableList<GitModifiedFile> getFiles(
        Key key, Repository repo, ObjectInserter ins, RevWalk rw)
        throws IOException, ExecutionException {
      ObjectId aTree = diffUtil.getTreeId(rw, key.aCommit());
      ObjectId bTree = diffUtil.getTreeId(rw, key.bCommit());
      GitModifiedFilesCache.Key gitKey =
          GitModifiedFilesCache.Key.create(
              key.project(),
              aTree,
              bTree,
              key.whitespace(),
              key.renameDetectionFlag(),
              key.renameScore());
      List<GitModifiedFile> files = gitCache.get(gitKey).gitModifiedFiles();
      if (!diffUtil.areRelated(rw, key.aCommit(), key.bCommit())) {
        return ImmutableList.copyOf(files);
      }
      Set<String> touchedFiles = getTouchedFilesWithParents(key, repo, ins, rw);
      return files.stream().filter(f -> isTouched(touchedFiles, f)).collect(toImmutableList());
    }

    private boolean isMergeCommit(RevWalk rw, ObjectId commitId) throws IOException {
      return diffUtil.getNumParents(rw, commitId) > 1;
    }

    /**
     * Returns the paths of files that were modified between the old and new commits versus their
     * parents (i.e. old commit vs. its parent, and new commit vs. its parent).
     *
     * @param key the {@link Key} representing the commits we are diffing
     * @param rw a {@link RevWalk} for the repository
     * @return The list of modified files between the old/new commits and their parents
     * @throws IOException
     * @throws ExecutionException
     */
    private Set<String> getTouchedFilesWithParents(
        Key key, Repository repo, ObjectInserter ins, RevWalk rw)
        throws IOException, ExecutionException {
      GitModifiedFilesCache.Key oldVsBaseKey =
          againstDefaultBase(key, repo, ins, rw, key.aCommit());
      List<GitModifiedFile> oldVsBase = gitCache.get(oldVsBaseKey).gitModifiedFiles();

      GitModifiedFilesCache.Key newVsBaseKey =
          againstDefaultBase(key, repo, ins, rw, key.bCommit());
      List<GitModifiedFile> newVsBase = gitCache.get(newVsBaseKey).gitModifiedFiles();

      Set<String> touchedFilePaths = new HashSet<>();
      touchedFilePaths.addAll(getOldAndNewPaths(oldVsBase));
      touchedFilePaths.addAll(getOldAndNewPaths(newVsBase));

      return touchedFilePaths;
    }

    private ImmutableList<String> getOldAndNewPaths(List<GitModifiedFile> files) {
      ImmutableList.Builder<String> result = ImmutableList.builder();
      files.stream()
          .forEach(
              f -> {
                if (f.oldPath() != null) {
                  result.add(f.oldPath());
                }
                if (f.newPath() != null) {
                  result.add(f.newPath());
                }
              });
      return result.build();
    }

    private static boolean isTouched(Set<String> touchedFilePaths, GitModifiedFile diffEntry) {
      String oldFilePath = diffEntry.oldPath();
      String newFilePath = diffEntry.newPath();
      // One of the above file paths could be /dev/null but we need not explicitly check for this
      // value as the set of file paths shouldn't contain it.
      return touchedFilePaths.contains(oldFilePath) || touchedFilePaths.contains(newFilePath);
    }

    /**
     * Returns the {@link GitModifiedFilesCache.Key} for the <code>commitId</code> argument against
     * its default parent commit.
     *
     * @param key
     * @param commitId The 20 bytes hex SHA-1 of the commit
     * @return the {@link GitModifiedFilesCache.Key} of the the commitId against its parent commit
     * @throws IOException
     */
    private GitModifiedFilesCache.Key againstDefaultBase(
        Key key, Repository repo, ObjectInserter ins, RevWalk rw, ObjectId commitId)
        throws IOException {
      RevObject parentCommit = diffUtil.getParentCommit(repo, ins, rw, null, commitId);
      ObjectId bTree = diffUtil.getTreeId(rw, commitId);
      ObjectId aTree = diffUtil.getTreeId(rw, parentCommit.getId());
      return GitModifiedFilesCache.Key.create(
          key.project(),
          aTree,
          bTree,
          key.whitespace(),
          key.renameDetectionFlag(),
          key.renameScore());
    }
  }

  @AutoValue
  public abstract static class Key implements Serializable {
    public static Key create(
        Project.NameKey project,
        ObjectId aTree,
        ObjectId bTree,
        DiffPreferencesInfo.Whitespace whitespace,
        boolean renameDetectionFlag,
        int renameScore,
        boolean includeFiles) {
      return new AutoValue_ModifiedFilesCache_Key(
          project, aTree, bTree, whitespace, renameDetectionFlag, renameScore, includeFiles);
    }

    public abstract Project.NameKey project();

    public abstract ObjectId aCommit();

    public abstract ObjectId bCommit();

    public abstract DiffPreferencesInfo.Whitespace whitespace();

    public abstract boolean renameDetectionFlag();

    public abstract int renameScore();

    public abstract boolean includeFiles();
  }
}
