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

import java.io.IOException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class DiffUtil {

  /**
   * Returns the Git tree object ID pointed to by the commitId parameter.
   *
   * @param rw a {@link RevWalk} object of some repository
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return Git tree object ID pointed to by the commitId
   */
  public static ObjectId getTreeId(RevWalk rw, ObjectId commitId) throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    return current.getTree().getId();
  }

  /**
   * Returns the RevCommit object given the 20 bytes commitId SHA-1 hash.
   *
   * @param rw A RevWalk used to iterate over commits
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return The RevCommit representing the commit in Git
   * @throws IOException a pack file or loose object could not be read while parsing the commits.
   */
  public static RevCommit getRevCommit(RevWalk rw, ObjectId commitId) throws IOException {
    return rw.parseCommit(commitId);
  }

  /**
   * Returns {@link ImmutablePair#nullPair()} if the aId and bId commit parameters are parent/child,
   * if they have a common parent, or if any of them is a root or merge commit. Otherwise, we return
   * a pair of RevCommit objects for aId and bId.
   *
   * @param rw a {@link RevWalk} for the repository.
   * @param aId 20 bytes commitId SHA-1 hash of the first commit.
   * @param bId 20 bytes commitId SHA-1 hash of the second commit.
   * @throws IOException a pack file or loose object could not be read while parsing the commits.
   */
  public static Pair<RevCommit, RevCommit> areRelated(RevWalk rw, ObjectId aId, ObjectId bId)
      throws IOException {
    RevCommit commitA = getRevCommit(rw, aId);
    RevCommit commitB = getRevCommit(rw, bId);
    if (commitA == null
        || isRootOrMergeCommit(commitA)
        || isRootOrMergeCommit(commitB)
        || areParentChild(commitA, commitB)
        || haveCommonParent(commitA, commitB)) {
      return ImmutablePair.nullPair();
    }
    return Pair.of(commitA, commitB);
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
