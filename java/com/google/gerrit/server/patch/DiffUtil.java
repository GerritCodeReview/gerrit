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
import com.google.gerrit.server.patch.diff.ModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import java.io.IOException;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A utility class used by the diff cache interfaces {@link GitModifiedFilesCache} and {@link
 * ModifiedFilesCache}.
 */
public class DiffUtil {

  /**
   * Returns the Git tree object ID pointed to by the commitId parameter.
   *
   * @param rw a {@link RevWalk} of an opened repository that is used to walk the commit graph.
   * @param commitId 20 bytes commitId SHA-1 hash.
   * @return Git tree object ID pointed to by the commitId.
   */
  public static ObjectId getTreeId(RevWalk rw, ObjectId commitId) throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    return current.getTree().getId();
  }

  /**
   * Returns the RevCommit object given the 20 bytes commitId SHA-1 hash.
   *
   * @param rw a {@link RevWalk} of an opened repository that is used to walk the commit graph.
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return The RevCommit representing the commit in Git
   * @throws IOException a pack file or loose object could not be read while parsing the commits.
   */
  public static RevCommit getRevCommit(RevWalk rw, ObjectId commitId) throws IOException {
    return rw.parseCommit(commitId);
  }

  /**
   * Returns true if the commitA and commitB parameters are parent/child, if they have a common
   * parent, or if any of them is a root or merge commit.
   */
  public static boolean areRelated(RevCommit commitA, RevCommit commitB) {
    if (commitA == null
        || isRootOrMergeCommit(commitA)
        || isRootOrMergeCommit(commitB)
        || areParentAndChild(commitA, commitB)
        || haveCommonParent(commitA, commitB)) {
      return true;
    }
    return false;
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

  private static boolean isRootOrMergeCommit(RevCommit commit) {
    return commit.getParentCount() != 1;
  }

  private static boolean areParentAndChild(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB)
        || ObjectId.isEqual(commitB.getParent(0), commitA);
  }

  private static boolean haveCommonParent(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB.getParent(0));
  }
}
