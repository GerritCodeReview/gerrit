// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.util;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

public class GitUtil {

  /**
   * @param git
   * @param commitId
   * @param parentNo
   * @return the {@code paretNo} parent of given commit or {@code null}
   *             when {@code parentNo} exceed number of {@code commitId} parents.
   * @throws IncorrectObjectTypeException
   *             the supplied id is not a commit or an annotated tag.
   * @throws IOException
   *             a pack file or loose object could not be read.
   */
  public static RevCommit getParent(Repository git,
      ObjectId commitId, int parentNo) throws IOException {
    try (RevWalk walk = new RevWalk(git)) {
      RevCommit commit = walk.parseCommit(commitId);
      if (commit.getParentCount() > parentNo) {
        return commit.getParent(parentNo);
      }
    }
    return null;
  }

  private GitUtil() {
  }
}
