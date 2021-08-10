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
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** A utility class for computing the base commit / parent for a specific patchset commit. */
@Singleton
class BaseCommitUtil {
  private final AutoMerger autoMerger;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final GitRepositoryManager repoManager;

  /** If true, auto-merge results are stored in the repository. */
  private final boolean saveAutomerge;

  @Inject
  BaseCommitUtil(AutoMerger am, @GerritServerConfig Config cfg, GitRepositoryManager repoManager) {
    this.autoMerger = am;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    this.saveAutomerge = AutoMerger.cacheAutomerge(cfg);
    this.repoManager = repoManager;
  }

  RevObject getBaseCommit(Project.NameKey project, ObjectId newCommit, @Nullable Integer parentNum)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = newInserter(repo);
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
      ObjectInserter ins,
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
          if (!saveAutomerge) {
            throw new IOException(
                "diff against auto-merge commits is only supported if 'change.cacheAutomerge' config is set to true.");
          }
          // TODO(ghareeb): Avoid persisting auto-merge commits.
          RevCommit autoMerge = createAutoMergeInGitIfNecessary(repo, ins, rw, current);
          return autoMerge == null ? getAutoMergeFromGit(repo, current) : autoMerge;
        }
        return null;
    }
  }

  /**
   * Creates the auto-merge commit in git. If the auto-merge already exists, this does nothing.
   * Otherwise, the auto-merge is created, persisted in git and the cache-automerge ref is updated
   * for the merge commit.
   *
   * @return null if the auto-merge already exists in git, or the auto-merge {@link RevCommit}
   *     object otherwise.
   */
  private RevCommit createAutoMergeInGitIfNecessary(
      Repository repo, ObjectInserter ins, RevWalk rw, RevCommit mergeCommit) throws IOException {
    Optional<ReceiveCommand> receive =
        autoMerger.createAutoMergeCommitIfNecessary(
            new RepoView(repo, rw, ins), rw, ins, mergeCommit);
    if (receive.isPresent()) {
      ins.flush();
      return updateRef(repo, rw, receive.get().getRefName(), receive.get().getNewId(), mergeCommit);
    }
    return null;
  }

  private RevCommit getAutoMergeFromGit(Repository repo, RevCommit mergeCommit) throws IOException {
    try (InMemoryInserter inMemoryIns = new InMemoryInserter(repo);
        RevWalk inMemoryRw = new RevWalk(inMemoryIns.newReader())) {
      return autoMerger.lookupFromGitOrMergeInMemory(
          repo, inMemoryRw, inMemoryIns, mergeCommit, mergeStrategy);
    }
  }

  private static RevCommit updateRef(
      Repository repo, RevWalk rw, String refName, ObjectId autoMergeId, RevCommit merge)
      throws IOException {
    RefUpdate ru = repo.updateRef(refName);
    ru.setNewObjectId(autoMergeId);
    ru.disableRefLog();
    switch (ru.forceUpdate()) {
      case FAST_FORWARD:
      case FORCED:
      case NEW:
      case NO_CHANGE:
        return rw.parseCommit(autoMergeId);
      case LOCK_FAILURE:
        throw new LockFailureException(
            String.format("Failed to create auto-merge of %s", merge.name()), ru);
      case IO_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      case RENAMED:
      default:
        throw new IOException(
            String.format(
                "Failed to create auto-merge of %s: Cannot write %s (%s)",
                merge.name(), refName, ru.getResult()));
    }
  }

  private ObjectInserter newInserter(Repository repo) {
    return saveAutomerge ? repo.newObjectInserter() : new InMemoryInserter(repo);
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    ObjectId id = ins.insert(Constants.OBJ_TREE, new byte[] {});
    ins.flush();
    return id;
  }
}
