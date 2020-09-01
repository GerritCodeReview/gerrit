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
//

package com.google.gerrit.server.patch;

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
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
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class DiffUtil {
  private final AutoMerger autoMerger;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final GitRepositoryManager repoManager;
  private final boolean save;

  @Inject
  DiffUtil(AutoMerger am, GitRepositoryManager repoManager, @GerritServerConfig Config cfg) {
    this.repoManager = repoManager;
    this.autoMerger = am;
    this.save = AutoMerger.cacheAutomerge(cfg);
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
  }

  /**
   * Returns the Git tree object ID pointed to by the commitId parameter.
   *
   * @param rw a {@link RevWalk} object of the repository
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return Git tree object ID pointed to by the commitId
   * @throws IOException
   */
  public static ObjectId getTreeId(RevWalk rw, ObjectId commitId) throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    return current.getTree().getId();
  }

  /**
   * Returns the number of parent commits of the commit represented by the commitId parameter.
   *
   * @param rw a {@link RevWalk} object of the repository
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return
   * @throws IOException
   */
  public static int getNumParents(RevWalk rw, ObjectId commitId) throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    return current.getParentCount();
  }

  /**
   * Returns the parent commit Object of the commit represented by the commitId parameter.
   *
   * @param repo a git repository
   * @param ins a git object inserter in the database
   * @param rw a {@link RevWalk} object of the repository
   * @param parentNum used to identify the parent number for merge commits
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return Returns the parent commit Object of the commit represented by the commitId parameter.
   * @throws IOException
   */
  public RevObject getParentCommit(
      Repository repo, ObjectInserter ins, RevWalk rw, Integer parentNum, ObjectId commitId)
      throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    switch (current.getParentCount()) {
      case 0:
        return rw.parseAny(emptyTree(ins));
      case 1:
        {
          RevCommit r = current.getParent(0);
          return r;
        }
      case 2:
        if (parentNum != null) {
          RevCommit r = current.getParent(parentNum - 1);
          return r;
        }
        return autoMerger.merge(repo, rw, ins, current, mergeStrategy);
      default:
        // octopus merge
        return null;
    }
  }

  /**
   * Returns the RevCommit object given the 20 bytes commitId SHA-1 hash.
   *
   * @param rw A RevWalk used to iterate over commits
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return The RevCommit representing the commit in Git
   * @throws IOException
   */
  public static RevCommit getRevCommit(RevWalk rw, ObjectId commitId) throws IOException {
    return rw.parseCommit(commitId);
  }

  /**
   * Returns true if the aId and bId commit parameters are parent/child, if they have a common
   * parent, or if any of them is a root or merge commit.
   *
   * @param rw a {@link RevWalk} for the repository
   * @param aId 20 bytes commitId SHA-1 hash of the first commit
   * @param bId 20 bytes commitId SHA-1 hash of the second commit
   * @return true if both commits are related
   * @throws IOException
   */
  public static boolean areRelated(RevWalk rw, ObjectId aId, ObjectId bId) throws IOException {
    if (aId == null) {
      return false;
    }
    RevCommit commitA = getRevCommit(rw, aId);
    RevCommit commitB = getRevCommit(rw, bId);
    if (commitA == null
        || isRootOrMergeCommit(commitA)
        || isRootOrMergeCommit(commitB)
        || areParentChild(commitA, commitB)
        || haveCommonParent(commitA, commitB)) {
      return false;
    }
    return true;
  }

  public static List<DiffEntry> getGitTreeDiff(
      Repository repo, ObjectReader reader, GitModifiedFilesCache.Key key) throws IOException {
    DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
    RawTextComparator cmp = comparatorFor(key.whitespace());
    df.setReader(reader, repo.getConfig());
    df.setDiffComparator(cmp);
    if (key.renameDetectionFlag()) {
      df.setDetectRenames(true);
      df.getRenameDetector().setRenameScore(key.renameScore());
    }
    return df.scan(key.aTree(), key.bTree());
  }

  public static RawTextComparator comparatorFor(Whitespace ws) {
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

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    ObjectId id = ins.insert(Constants.OBJ_TREE, new byte[] {});
    ins.flush();
    return id;
  }

  private static boolean isRootOrMergeCommit(RevCommit commit) {
    return commit.getParentCount() != 1;
  }

  private static boolean areParentChild(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB)
        || ObjectId.isEqual(commitB.getParent(0), commitA);
  }

  private static boolean haveCommonParent(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB.getParent(0));
  }
}
