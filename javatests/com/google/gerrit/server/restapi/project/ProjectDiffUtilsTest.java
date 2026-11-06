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

package com.google.gerrit.server.restapi.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class ProjectDiffUtilsTest {
  @Test
  public void forwardDiffReusesAncestryChecksAndRejectsHiddenIntermediateCommit() throws Exception {
    checkRelatedCommits(false);
  }

  @Test
  public void reverseDiffReusesAncestryChecksAndRejectsHiddenIntermediateCommit() throws Exception {
    checkRelatedCommits(true);
  }

  private void checkRelatedCommits(boolean reverseDiff) throws Exception {
    try (InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
        CountingRevWalk rw = new CountingRevWalk(repo)) {
      TestRepository<InMemoryRepository> tr = new TestRepository<>(repo);
      RevCommit root = tr.commit().message("root").create();
      RevCommit middle = tr.commit().parent(root).message("middle").create();
      RevCommit tip = tr.commit().parent(middle).message("tip").create();
      // Commit graph: root -> middle -> tip. Reverse diffs swap only the endpoints.
      RevCommit oldCommit = rw.parseCommit(reverseDiff ? tip : root);
      RevCommit newCommit = rw.parseCommit(reverseDiff ? root : tip);
      CommitsCollection commits = mock(CommitsCollection.class);
      when(commits.canRead(any(), any(), any())).thenReturn(true);
      ProjectDiffUtils utils = new ProjectDiffUtils(commits);
      ProjectDiffUtils.AncestorRelationship relationship =
          utils.validateAncestorRelationship(rw, oldCommit, newCommit);
      utils.verifyPathVisibility(null, repo, rw, oldCommit, newCommit, relationship);
      // One check per direction, with no additional ancestry checks during visibility checking.
      assertThat(rw.ancestryChecks).isEqualTo(2);

      // Visible endpoints must not hide an unreadable commit between them.
      when(commits.canRead(any(), any(), any()))
          .thenAnswer(invocation -> !middle.equals(invocation.getArgument(2)));
      assertThrows(
          ResourceNotFoundException.class,
          () -> utils.verifyPathVisibility(null, repo, rw, oldCommit, newCommit, relationship));
    }
  }

  @Test
  public void rejectsUnrelatedCommits() throws Exception {
    try (InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
        CountingRevWalk rw = new CountingRevWalk(repo)) {
      TestRepository<InMemoryRepository> tr = new TestRepository<>(repo);
      RevCommit first = rw.parseCommit(tr.commit().message("first").create());
      RevCommit second = rw.parseCommit(tr.commit().message("second").create());
      ProjectDiffUtils utils = new ProjectDiffUtils(mock(CommitsCollection.class));
      assertThrows(
          BadRequestException.class, () -> utils.validateAncestorRelationship(rw, first, second));
      assertThat(rw.ancestryChecks).isEqualTo(2);
    }
  }

  // Count expensive ancestry checks while retaining real JGit traversal.
  private static class CountingRevWalk extends RevWalk {
    int ancestryChecks;

    CountingRevWalk(Repository repo) {
      super(repo);
    }

    @Override
    public boolean isMergedInto(RevCommit base, RevCommit tip) throws IOException {
      ancestryChecks++;
      return super.isMergedInto(base, tip);
    }
  }
}
