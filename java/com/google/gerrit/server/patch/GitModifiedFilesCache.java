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
//

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/** A cache for the list of Git modified files between 2 different commits (patchsets). */
public class GitModifiedFilesCache {
  static final String GIT_MODIFIED_FILES = "git_modified_files";

  LoadingCache<Key, GitModifiedFilesList> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(GitModifiedFilesCache.class);

        persist(GIT_MODIFIED_FILES, Key.class, GitModifiedFilesList.class)
            .maximumWeight(10 << 20)
            .loader(GitModifiedFilesCache.Loader.class);
      }
    };
  }

  @Inject
  public GitModifiedFilesCache(
      @Named(GIT_MODIFIED_FILES) LoadingCache<Key, GitModifiedFilesList> cache) {
    this.cache = cache;
  }

  public GitModifiedFilesList get(Key key) throws ExecutionException {
    return cache.get(key);
  }

  static class Loader extends CacheLoader<Key, GitModifiedFilesList> {
    private final GitRepositoryManager repoManager;
    private final DiffUtil diffUtil;

    @Inject
    Loader(GitRepositoryManager repoManager, DiffUtil diffUtil) {
      this.repoManager = repoManager;
      this.diffUtil = diffUtil;
    }

    @Override
    public GitModifiedFilesList load(Key key) throws IOException {
      Project.NameKey project = key.project();
      try (Repository repo = repoManager.openRepository(project);
          ObjectReader reader = repo.newObjectReader()) {
        List<DiffEntry> entries = diffUtil.getGitTreeDiff(repo, reader, key);

        List<GitModifiedFile> gitModifiedFiles =
            entries.stream().map(g -> map(g)).collect(Collectors.toList());

        return GitModifiedFilesList.create(gitModifiedFiles);
      }
    }

    private GitModifiedFile map(DiffEntry entry) {
      return GitModifiedFile.create(
          entry.getChangeType(),
          entry.getOldPath(),
          entry.getNewPath(),
          entry.getOldMode(),
          entry.getNewMode());
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
        int renameScore) {
      return new AutoValue_GitModifiedFilesCache_Key(
          project, aTree, bTree, whitespace, renameDetectionFlag, renameScore);
    }

    public abstract Project.NameKey project();

    public abstract ObjectId aTree();

    public abstract ObjectId bTree();

    public abstract DiffPreferencesInfo.Whitespace whitespace();

    public abstract boolean renameDetectionFlag();

    public abstract int renameScore();
  }
}
