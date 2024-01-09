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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** A utility class for computing the base commit / parent for a specific patchset commit. */
@Singleton
class BaseCommitUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AutoMerger autoMerger;
  private final GitRepositoryManager repoManager;

  /** If true, auto-merge results are stored in the repository. */
  private final boolean saveAutomerge;

  @Inject
  BaseCommitUtil(AutoMerger am, @GerritServerConfig Config cfg, GitRepositoryManager repoManager) {
    this.autoMerger = am;
    this.saveAutomerge = AutoMerger.cacheAutomerge(cfg);
    this.repoManager = repoManager;
  }

  /**
   * Returns the number of parent commits of the commit represented by the commitId parameter.
   *
   * @param project a specific git repository.
   * @param commitId 20 bytes commitId SHA-1 hash.
   * @return an integer representing the number of parents of the designated commit.
   */
  int getNumParents(Project.NameKey project, ObjectId commitId) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit current = rw.parseCommit(commitId);
      return current.getParentCount();
    }
  }

  /**
   * Returns the base commit for the provided commit.
   *
   * @param repoView repo view
   * @param ins a git object inserter in the database.
   * @param commitId 20 bytes commitId SHA-1 hash.
   * @param parentNum used to identify the parent number for merge commits. If parentNum is null and
   *     {@code commitId} has two parents, the auto-merge commit will be returned. If {@code
   *     commitId} has a single parent, it will be returned.
   * @return Returns the parent commit of the commit represented by the commitId parameter. Note
   *     that auto-merge is not supported for commits having more than two parents. If the commit
   *     has no parents (initial commit) or more than 2 parents {@code null} is returned as the
   *     parent commit.
   */
  @Nullable
  RevCommit getBaseCommit(
      RepoView repoView, ObjectInserter ins, ObjectId commitId, @Nullable Integer parentNum)
      throws IOException {
    RevCommit current = repoView.getRevWalk().parseCommit(commitId);
    switch (current.getParentCount()) {
      case 0:
        return null;
      case 1:
        return current.getParent(0);
      default:
        if (parentNum != null) {
          RevCommit r = current.getParent(parentNum - 1);
          repoView.getRevWalk().parseBody(r);
          return r;
        }
        // Only support auto-merge for 2 parents, not octopus merges
        if (current.getParentCount() == 2) {
          if (!saveAutomerge) {
            throw new IOException(
                "diff against auto-merge commits is only supported if 'change.cacheAutomerge' config is set to true.");
          }
          // TODO(ghareeb): Avoid persisting auto-merge commits.
          return getAutoMergeFromGitOrCreate(repoView, ins, current);
        }
        return null;
    }
  }

  /**
   * Gets the auto-merge commit from git if it already exists. If not, the auto-merge is created,
   * persisted in git and the cache-automerge ref is updated for the merge commit.
   *
   * @return the auto-merge {@link RevCommit}
   */
  private RevCommit getAutoMergeFromGitOrCreate(
      RepoView repoView, ObjectInserter ins, RevCommit mergeCommit) throws IOException {
    String refName = RefNames.refsCacheAutomerge(mergeCommit.name());
    Optional<RevCommit> autoMergeCommit = autoMerger.lookupCommit(repoView, refName);
    if (autoMergeCommit.isPresent()) {
      return autoMergeCommit.get();
    }
    if (!saveAutomerge && !(ins instanceof InMemoryInserter)) {
      ins = new InMemoryInserter(repoView.getRevWalk().getObjectReader());
    }
    ObjectId autoMergeId = autoMerger.createAutoMergeCommit(repoView, ins, mergeCommit);
    logger.atFine().log("flushing inserter %s", ins);
    ins.flush();
    return repoView.getRevWalk().parseCommit(autoMergeId);
  }
}
