// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.git.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Sets up a test repository with some simple commits, tags and branches.
 */
public abstract class SimpleDataRepositoryTestCase extends RepositoryTestCase {

  // Branch names
  protected static final String BRANCH_MASTER = "master";
  protected static final String BRANCH_1_0 = "rel-1.0";
  protected static final String BRANCH_1_3 = "rel-1.3";
  protected static final String BRANCH_2_0 = "rel-2.0";
  protected static final String BRANCH_2_5 = "rel-2.5";

  // Tag names
  protected static final String TAG_1_0 = "1.0";
  protected static final String TAG_1_0_1 = "1.0.1";
  protected static final String TAG_1_3 = "1.3";
  protected static final String TAG_2_0_1 = "2.0.1";
  protected static final String TAG_2_0 = "2.0";
  protected static final String TAG_2_5 = "2.5";
  protected static final String TAG_2_5_ANNOTATED = "2.5-annotated";
  protected static final String TAG_2_5_ANNOTATED_TWICE = "2.5-annotated_twice";

  // Commits
  protected RevCommit commit_v1_0;
  protected RevCommit commit_v1_0_1;
  protected RevCommit commit_v1_3;
  protected RevCommit commit_v2_0;
  protected RevCommit commit_v2_0_1;
  protected RevCommit commit_v2_5;
  protected RevCommit commit_initial;

  public void setUp() throws Exception {
    super.setUp();
    createSimpleTestData();
  }

  private void createSimpleTestData() throws Exception {

    /*-
      The following graph will be created.

      o   tag 2.5, 2.5_annotated, 2.5_annotated_twice
      |\
      | o tag 2.0.1
      | o tag 2.0
      o | tag 1.3
      |/
      o   c3

      | o tag 1.0.1
      |/
      o   tag 1.0
      o   c2
      o   c1
     */

    Git git = new Git(db);
    // Version 1.0
    commit_initial = git.commit().setMessage("c1").call();
    git.commit().setMessage("c2").call();
    commit_v1_0 = git.commit().setMessage("version 1.0").call();
    git.tag().setName(TAG_1_0).setObjectId(commit_v1_0).call();
    RevCommit c3 = git.commit().setMessage("c3").call();
    // Version 1.01
    createBranch(commit_v1_0, BRANCH_1_0);
    checkoutBranch(BRANCH_1_0);
    commit_v1_0_1 =
        git.commit().setMessage("verREFS_HEADS_RELsion 1.0.1").call();
    git.tag().setName(TAG_1_0_1).setObjectId(commit_v1_0_1).call();
    // Version 1.3
    createBranch(c3, BRANCH_1_3);
    checkoutBranch(BRANCH_1_3);
    commit_v1_3 = git.commit().setMessage("version 1.3").call();
    git.tag().setName(TAG_1_3).setObjectId(commit_v1_3).call();
    // Version 2.0
    createBranch(c3, BRANCH_2_0);
    checkoutBranch(BRANCH_2_0);
    commit_v2_0 = git.commit().setMessage("version 2.0").call();
    git.tag().setName(TAG_2_0).setObjectId(commit_v2_0).call();
    commit_v2_0_1 = git.commit().setMessage("version 2.0.1").call();
    git.tag().setName(TAG_2_0_1).setObjectId(commit_v2_0_1).call();

    // Version 2.5
    createBranch(commit_v1_3, BRANCH_2_5);
    checkoutBranch(BRANCH_2_5);
    git.merge().include(commit_v2_0_1).setCommit(false)
        .setFastForward(FastForwardMode.NO_FF).call();
    commit_v2_5 = git.commit().setMessage("version 2.5").call();
    git.tag().setName(TAG_2_5).setObjectId(commit_v2_5).setAnnotated(false)
        .call();
    Ref ref_tag_2_5_annotated =
        git.tag().setName(TAG_2_5_ANNOTATED).setObjectId(commit_v2_5)
            .setAnnotated(true).call();
    RevWalk revWalk = new RevWalk(db);
    RevTag tag_2_5_annotated =
        revWalk.parseTag(ref_tag_2_5_annotated.getObjectId());
    git.tag().setName(TAG_2_5_ANNOTATED_TWICE).setObjectId(tag_2_5_annotated)
        .setAnnotated(true).call();
  }

  @Override
  protected void createBranch(ObjectId objectId, String branchName)
      throws IOException {
    super.createBranch(objectId, "refs/heads/" + branchName);
  }

  @Override
  protected void checkoutBranch(String branchName)
      throws IllegalStateException, IOException {
    super.checkoutBranch("refs/heads/" + branchName);
  }
}