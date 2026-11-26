// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DiffUtil}. */
public class DiffUtilTest {
  private Repository repo;
  private RevWalk rw;
  private ObjectId emptyTreeId;
  private int commitCounter;

  @Before
  public void setUp() throws Exception {
    repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
    try (ObjectInserter ins = repo.newObjectInserter()) {
      emptyTreeId = ins.insert(new TreeFormatter());
      ins.flush();
    }
    rw = new RevWalk(repo);
  }

  @After
  public void tearDown() {
    rw.close();
    repo.close();
  }

  @Test
  public void areRelated_nullCommit_returnsTrue() throws Exception {
    RevCommit commit = createCommit();
    assertThat(DiffUtil.areRelated(null, commit)).isTrue();
    assertThat(DiffUtil.areRelated(commit, null)).isTrue();
    assertThat(DiffUtil.areRelated(null, null)).isTrue();
  }

  @Test
  public void areRelated_rootCommits_returnsTrue() throws Exception {
    RevCommit root1 = createCommit();
    RevCommit root2 = createCommit();
    RevCommit child = createCommit(root1);
    RevCommit merge = createCommit(root1, root2);

    assertThat(DiffUtil.areRelated(root1, root2)).isTrue();
    assertThat(DiffUtil.areRelated(root2, child)).isTrue();
    assertThat(DiffUtil.areRelated(child, root2)).isTrue();
    assertThat(DiffUtil.areRelated(root1, merge)).isTrue();
    assertThat(DiffUtil.areRelated(merge, root2)).isTrue();
  }

  @Test
  public void areRelated_singleParentCommits() throws Exception {
    RevCommit root = createCommit();
    RevCommit parentA = createCommit(root);
    RevCommit parentB = createCommit(root);
    RevCommit childOfA1 = createCommit(parentA);
    RevCommit childOfA2 = createCommit(parentA);
    RevCommit childOfB = createCommit(parentB);

    assertThat(DiffUtil.areRelated(parentA, childOfA1)).isTrue();
    assertThat(DiffUtil.areRelated(childOfA1, parentA)).isTrue();
    assertThat(DiffUtil.areRelated(childOfA1, childOfA2)).isTrue();
    assertThat(DiffUtil.areRelated(childOfA1, childOfB)).isFalse();
  }

  @Test
  public void areRelated_mergeCommitAndAnyParent_returnsTrue() throws Exception {
    RevCommit root = createCommit();
    RevCommit parent1 = createCommit(root);
    RevCommit parent2 = createCommit(root);
    RevCommit parent3 = createCommit(root);
    RevCommit merge = createCommit(parent1, parent2, parent3);

    assertThat(DiffUtil.areRelated(parent1, merge)).isTrue();
    assertThat(DiffUtil.areRelated(merge, parent1)).isTrue();
    assertThat(DiffUtil.areRelated(parent2, merge)).isTrue();
    assertThat(DiffUtil.areRelated(merge, parent2)).isTrue();
    assertThat(DiffUtil.areRelated(parent3, merge)).isTrue();
    assertThat(DiffUtil.areRelated(merge, parent3)).isTrue();
  }

  @Test
  public void areRelated_mergeCommitsSharingFirstParent_returnsTrue() throws Exception {
    RevCommit root = createCommit();
    RevCommit parent1 = createCommit(root);
    RevCommit parent2a = createCommit(root);
    RevCommit parent2b = createCommit(parent2a);
    RevCommit merge1 = createCommit(parent1, parent2a);
    RevCommit merge2 = createCommit(parent1, parent2b);
    RevCommit singleOnParent1 = createCommit(parent1);

    assertThat(DiffUtil.areRelated(merge1, merge2)).isTrue();
    assertThat(DiffUtil.areRelated(singleOnParent1, merge1)).isTrue();
    assertThat(DiffUtil.areRelated(merge1, singleOnParent1)).isTrue();
  }

  @Test
  public void areRelated_mergeCommitsWithDifferentFirstParents_returnsFalse() throws Exception {
    RevCommit root = createCommit();
    RevCommit parent1a = createCommit(root);
    RevCommit parent1b = createCommit(parent1a);
    RevCommit parent2 = createCommit(root);
    RevCommit merge1 = createCommit(parent1a, parent2);
    RevCommit merge2 = createCommit(parent1b, parent2);
    RevCommit singleOnParent1b = createCommit(parent1b);

    assertThat(DiffUtil.areRelated(merge1, merge2)).isFalse();
    assertThat(DiffUtil.areRelated(merge2, merge1)).isFalse();
    assertThat(DiffUtil.areRelated(merge1, singleOnParent1b)).isFalse();
    assertThat(DiffUtil.areRelated(singleOnParent1b, merge1)).isFalse();
  }

  private RevCommit createCommit(ObjectId... parents) throws IOException {
    try (ObjectInserter ins = repo.newObjectInserter()) {
      PersonIdent ident = new PersonIdent("Test User", "test@example.com");
      commitCounter++;
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(emptyTreeId);
      cb.setAuthor(ident);
      cb.setCommitter(ident);
      cb.setMessage("Test commit " + commitCounter);
      if (parents.length > 0) {
        cb.setParentIds(parents);
      }
      ObjectId commitId = ins.insert(cb);
      ins.flush();
      return rw.parseCommit(commitId);
    }
  }
}
