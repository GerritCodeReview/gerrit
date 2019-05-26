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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.RefNames.REFS_TAGS;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Before;
import org.junit.Test;

public class IncludedInResolverTest {
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

  private TestRepository<?> tr;

  @Before
  public void setUp() throws Exception {
    tr = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("repo")));

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

    // Version 1.0
    commit_initial = tr.branch(BRANCH_MASTER).commit().message("c1").create();
    tr.branch(BRANCH_MASTER).commit().message("c2").create();
    RevCommit commit_v1_0 = tr.branch(BRANCH_MASTER).commit().message("version 1.0").create();
    tag(TAG_1_0, commit_v1_0);
    RevCommit c3 = tr.branch(BRANCH_MASTER).commit().message("c3").create();

    // Version 1.01
    tr.branch(BRANCH_1_0).update(commit_v1_0);
    RevCommit commit_v1_0_1 = tr.branch(BRANCH_1_0).commit().message("version 1.0.1").create();
    tag(TAG_1_0_1, commit_v1_0_1);

    // Version 1.3
    tr.branch(BRANCH_1_3).update(c3);
    commit_v1_3 = tr.branch(BRANCH_1_3).commit().message("version 1.3").create();
    tag(TAG_1_3, commit_v1_3);

    // Version 2.0
    tr.branch(BRANCH_2_0).update(c3);
    RevCommit commit_v2_0 = tr.branch(BRANCH_2_0).commit().message("version 2.0").create();
    tag(TAG_2_0, commit_v2_0);
    RevCommit commit_v2_0_1 = tr.branch(BRANCH_2_0).commit().message("version 2.0.1").create();
    tag(TAG_2_0_1, commit_v2_0_1);

    // Version 2.5
    tr.branch(BRANCH_2_5).update(commit_v1_3);
    tr.branch(BRANCH_2_5).commit().parent(commit_v2_0_1).create(); // Merge v2.0.1
    commit_v2_5 = tr.branch(BRANCH_2_5).commit().message("version 2.5").create();
    tr.update(REFS_TAGS + TAG_2_5, commit_v2_5);
    RevTag tag_2_5_annotated = tag(TAG_2_5_ANNOTATED, commit_v2_5);
    tag(TAG_2_5_ANNOTATED_TWICE, tag_2_5_annotated);
  }

  @Test
  public void resolveLatestCommit() throws Exception {
    // Check tip commit
    IncludedInResolver.Result detail = resolve(commit_v2_5);

    // Check that only tags and branches which refer the tip are returned
    assertThat(detail.tags()).containsExactly(TAG_2_5, TAG_2_5_ANNOTATED, TAG_2_5_ANNOTATED_TWICE);
    assertThat(detail.branches()).containsExactly(BRANCH_2_5);
  }

  @Test
  public void resolveFirstCommit() throws Exception {
    // Check first commit
    IncludedInResolver.Result detail = resolve(commit_initial);

    // Check whether all tags and branches are returned
    assertThat(detail.tags())
        .containsExactly(
            TAG_1_0,
            TAG_1_0_1,
            TAG_1_3,
            TAG_2_0,
            TAG_2_0_1,
            TAG_2_5,
            TAG_2_5_ANNOTATED,
            TAG_2_5_ANNOTATED_TWICE);
    assertThat(detail.branches())
        .containsExactly(BRANCH_MASTER, BRANCH_1_0, BRANCH_1_3, BRANCH_2_0, BRANCH_2_5);
  }

  @Test
  public void resolveBetwixtCommit() throws Exception {
    // Check a commit somewhere in the middle
    IncludedInResolver.Result detail = resolve(commit_v1_3);

    // Check whether all succeeding tags and branches are returned
    assertThat(detail.tags())
        .containsExactly(TAG_1_3, TAG_2_5, TAG_2_5_ANNOTATED, TAG_2_5_ANNOTATED_TWICE);
    assertThat(detail.branches()).containsExactly(BRANCH_1_3, BRANCH_2_5);
  }

  private IncludedInResolver.Result resolve(RevCommit commit) throws Exception {
    return IncludedInResolver.resolve(tr.getRepository(), tr.getRevWalk(), commit);
  }

  private RevTag tag(String name, RevObject dest) throws Exception {
    return tr.update(REFS_TAGS + name, tr.tag(name, dest));
  }
}
