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

package com.google.gerrit.server.change;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IncludedInResolverTest extends RepositoryTestCase {

  // Branch names
  private static final String BRANCH_MASTER = "master";
  private static final String BRANCH_1_0 = "rel-1.0";
  private static final String BRANCH_1_3 = "rel-1.3";
  private static final String BRANCH_2_0 = "rel-2.0";
  private static final String BRANCH_2_5 = "rel-2.5";

  // Tag names
  private static final String TAG_1_0 = "1.0";
  private static final String TAG_1_0_1 = "1.0.1";
  private static final String TAG_1_3 = "1.3";
  private static final String TAG_2_0_1 = "2.0.1";
  private static final String TAG_2_0 = "2.0";
  private static final String TAG_2_5 = "2.5";
  private static final String TAG_2_5_ANNOTATED = "2.5-annotated";
  private static final String TAG_2_5_ANNOTATED_TWICE = "2.5-annotated_twice";

  // Commits
  private RevCommit commit_initial;
  private RevCommit commit_v1_3;
  private RevCommit commit_v2_5;

  private List<String> expTags = new ArrayList<>();
  private List<String> expBranches = new ArrayList<>();

  private RevWalk revWalk;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    /*- The following graph will be created.

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

    // TODO(dborowitz): Use try/finally when this doesn't double-close the repo.
    @SuppressWarnings("resource")
    Git git = new Git(db);
    revWalk = new RevWalk(db);
    // Version 1.0
    commit_initial = git.commit().setMessage("c1").call();
    git.commit().setMessage("c2").call();
    RevCommit commit_v1_0 = git.commit().setMessage("version 1.0").call();
    git.tag().setName(TAG_1_0).setObjectId(commit_v1_0).call();
    RevCommit c3 = git.commit().setMessage("c3").call();
    // Version 1.01
    createAndCheckoutBranch(commit_v1_0, BRANCH_1_0);
    RevCommit commit_v1_0_1 = git.commit().setMessage("verREFS_HEADS_RELsion 1.0.1").call();
    git.tag().setName(TAG_1_0_1).setObjectId(commit_v1_0_1).call();
    // Version 1.3
    createAndCheckoutBranch(c3, BRANCH_1_3);
    commit_v1_3 = git.commit().setMessage("version 1.3").call();
    git.tag().setName(TAG_1_3).setObjectId(commit_v1_3).call();
    // Version 2.0
    createAndCheckoutBranch(c3, BRANCH_2_0);
    RevCommit commit_v2_0 = git.commit().setMessage("version 2.0").call();
    git.tag().setName(TAG_2_0).setObjectId(commit_v2_0).call();
    RevCommit commit_v2_0_1 = git.commit().setMessage("version 2.0.1").call();
    git.tag().setName(TAG_2_0_1).setObjectId(commit_v2_0_1).call();

    // Version 2.5
    createAndCheckoutBranch(commit_v1_3, BRANCH_2_5);
    git.merge()
        .include(commit_v2_0_1)
        .setCommit(false)
        .setFastForward(FastForwardMode.NO_FF)
        .call();
    commit_v2_5 = git.commit().setMessage("version 2.5").call();
    git.tag().setName(TAG_2_5).setObjectId(commit_v2_5).setAnnotated(false).call();
    Ref ref_tag_2_5_annotated =
        git.tag().setName(TAG_2_5_ANNOTATED).setObjectId(commit_v2_5).setAnnotated(true).call();
    RevTag tag_2_5_annotated = revWalk.parseTag(ref_tag_2_5_annotated.getObjectId());
    git.tag()
        .setName(TAG_2_5_ANNOTATED_TWICE)
        .setObjectId(tag_2_5_annotated)
        .setAnnotated(true)
        .call();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    revWalk.close();
    super.tearDown();
  }

  @Test
  public void resolveLatestCommit() throws Exception {
    // Check tip commit
    IncludedInResolver.Result detail = resolve(commit_v2_5);

    // Check that only tags and branches which refer the tip are returned
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  @Test
  public void resolveFirstCommit() throws Exception {
    // Check first commit
    IncludedInResolver.Result detail = resolve(commit_initial);

    // Check whether all tags and branches are returned
    expTags.add(TAG_1_0);
    expTags.add(TAG_1_0_1);
    expTags.add(TAG_1_3);
    expTags.add(TAG_2_0);
    expTags.add(TAG_2_0_1);
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());

    expBranches.add(BRANCH_MASTER);
    expBranches.add(BRANCH_1_0);
    expBranches.add(BRANCH_1_3);
    expBranches.add(BRANCH_2_0);
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  @Test
  public void resolveBetwixtCommit() throws Exception {
    // Check a commit somewhere in the middle
    IncludedInResolver.Result detail = resolve(commit_v1_3);

    // Check whether all succeeding tags and branches are returned
    expTags.add(TAG_1_3);
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());

    expBranches.add(BRANCH_1_3);
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  private IncludedInResolver.Result resolve(RevCommit commit) throws Exception {
    return IncludedInResolver.resolve(db, revWalk, commit);
  }

  private void assertEquals(List<String> list1, List<String> list2) {
    Collections.sort(list1);
    Collections.sort(list2);
    Assert.assertEquals(list1, list2);
  }

  private void createAndCheckoutBranch(ObjectId objectId, String branchName) throws IOException {
    String fullBranchName = "refs/heads/" + branchName;
    super.createBranch(objectId, fullBranchName);
    super.checkoutBranch(fullBranchName);
  }
}
