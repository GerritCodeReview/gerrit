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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/** A utility class for computing the base commit / parent for a specific patchset commit. */
@Singleton
class BaseCommitUtil {
  private final AutoMerger autoMerger;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final GitRepositoryManager repoManager;

  @Inject
  BaseCommitUtil(AutoMerger am, @GerritServerConfig Config cfg, GitRepositoryManager repoManager) {
    this.autoMerger = am;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    this.repoManager = repoManager;
  }

  RevObject getBaseCommit(Project.NameKey project, ObjectId newCommit, @Nullable Integer parentNum)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        InMemoryInserter ins = new InMemoryInserter(repo);
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      return getParentCommit(repo, ins, rw, parentNum, newCommit);
    }
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
   * Returns the parent commit Object of the commit represented by the commitId parameter.
   *
   * @param repo a git repository.
   * @param ins a git object inserter in the database.
   * @param rw a {@link RevWalk} object of the repository.
   * @param parentNum used to identify the parent number for merge commits. If parentNum is null and
   *     {@code commitId} has two parents, the auto-merge commit will be returned. If {@code
   *     commitId} has a single parent, it will be returned.
   * @param commitId 20 bytes commitId SHA-1 hash.
   * @return Returns the parent commit of the commit represented by the commitId parameter. Note
   *     that auto-merge is not supported for commits having more than two parents.
   */
  RevObject getParentCommit(
      Repository repo,
      InMemoryInserter ins,
      RevWalk rw,
      @Nullable Integer parentNum,
      ObjectId commitId)
      throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    switch (current.getParentCount()) {
      case 0:
        return rw.parseAny(emptyTree(ins));
      case 1:
        return current.getParent(0);
      default:
        if (parentNum != null) {
          RevCommit r = current.getParent(parentNum - 1);
          rw.parseBody(r);
          return r;
        }
        // Only support auto-merge for 2 parents, not octopus merges
        if (current.getParentCount() == 2) {
          return autoMerger.lookupFromGitOrMergeInMemory(repo, rw, ins, current, mergeStrategy);
        }
        return null;
    }
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    ObjectId id = ins.insert(Constants.OBJ_TREE, new byte[] {});
    ins.flush();
    return id;
  }
}
