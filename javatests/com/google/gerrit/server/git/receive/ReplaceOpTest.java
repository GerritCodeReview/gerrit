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

package com.google.gerrit.server.git.receive;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoView;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class ReplaceOpTest {
  @Test
  public void checksEachDistinctTipOnceIncludingPreferredBranch() throws Exception {
    try (InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
        CountingRevWalk rw = new CountingRevWalk(repo)) {
      TestRepository<InMemoryRepository> tr = new TestRepository<>(repo);
      RevCommit commit = rw.parseCommit(tr.commit().message("change").create());
      RevCommit unrelated = tr.commit().message("unrelated").create();
      RevCommit other = tr.commit().message("other").create();
      ImmutableMap<String, ObjectId> heads =
          ImmutableMap.of("master", unrelated, "duplicate", unrelated, "other", other);
      assertThat(findMergedInto(rw, Optional.of(unrelated), heads, commit)).isNull();
      // Master and duplicate share one tip; other has the only remaining distinct tip.
      assertThat(rw.ancestryChecks).isEqualTo(2);
    }
  }

  @Test
  public void prefersDestinationBranchOverOtherMatchingBranches() throws Exception {
    try (InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
        CountingRevWalk rw = new CountingRevWalk(repo)) {
      TestRepository<InMemoryRepository> tr = new TestRepository<>(repo);
      RevCommit commit = rw.parseCommit(tr.commit().message("change").create());
      assertThat(findMergedInto(rw, Optional.of(commit), ImmutableMap.of("other", commit), commit))
          .isEqualTo("refs/heads/master");
      // A preferred-branch match returns immediately, without scanning other heads.
      assertThat(rw.ancestryChecks).isEqualTo(1);
    }
  }

  @Test
  public void skipsDuplicateTipsAndFindsMatchingBranchWhenPreferredBranchIsAbsent()
      throws Exception {
    try (InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
        CountingRevWalk rw = new CountingRevWalk(repo)) {
      TestRepository<InMemoryRepository> tr = new TestRepository<>(repo);
      RevCommit commit = rw.parseCommit(tr.commit().message("change").create());
      RevCommit unrelated = tr.commit().message("unrelated").create();
      RevCommit tip = tr.commit().parent(commit).message("tip").create();
      ImmutableMap<String, ObjectId> heads =
          ImmutableMap.of("first", unrelated, "duplicate", unrelated, "matching", tip);
      assertThat(findMergedInto(rw, Optional.empty(), heads, commit))
          .isEqualTo("refs/heads/matching");
      // Check the unrelated tip once, then the matching tip, despite three branch names.
      assertThat(rw.ancestryChecks).isEqualTo(2);
    }
  }

  private static String findMergedInto(
      RevWalk rw, Optional<ObjectId> preferred, Map<String, ObjectId> heads, RevCommit commit)
      throws Exception {
    Context ctx = mock(Context.class);
    RepoView view = mock(RepoView.class);
    when(ctx.getRevWalk()).thenReturn(rw);
    when(ctx.getRepoView()).thenReturn(view);
    when(view.getRef("refs/heads/master")).thenReturn(preferred);
    when(view.getRefs("refs/heads/")).thenReturn(heads);
    return ReplaceOp.findMergedInto(ctx, "refs/heads/master", commit);
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
