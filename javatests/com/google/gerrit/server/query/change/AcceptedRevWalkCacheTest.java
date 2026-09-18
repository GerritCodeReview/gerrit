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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevFlag;
import org.junit.Test;
import org.mockito.MockedStatic;

public class AcceptedRevWalkCacheTest {
  @Test
  public void perThreadCacheRegistersCacheForClose() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    Project.NameKey project = Project.nameKey("accepted-walk");
    AcceptedRevWalkCache cache;
    try (Repository ignored = repoManager.createRepository(project);
        PerThreadCache unused = PerThreadCache.create()) {
      cache = AcceptedRevWalkCache.PerThread.get(repoManager);
      Void unusedResult = cache.run(project, null, (repo, rw, accepted) -> null);
      assertThat(cache.entryForTesting(project)).isNotNull();
    }

    assertThat(cache.entryForTesting(project)).isNull();
  }

  @Test
  public void parsesAcceptedSetOnceAndReusesFlagsAcrossCandidates() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    Project.NameKey project = Project.nameKey("accepted-walk");
    try (Repository ignored = repoManager.createRepository(project);
        AcceptedRevWalkCache cache = new AcceptedRevWalkCache(repoManager)) {
      try (MockedStatic<SubmitDryRun> submitDryRun =
          mockStatic(SubmitDryRun.class, CALLS_REAL_METHODS)) {
        // Trigger creation of the cache entry, then substitute a spy for its RevWalk so we can
        // verify reset() calls for the remaining candidates below.
        Void unused = cache.run(project, null, (repo, rw, accepted) -> null);
        AcceptedRevWalkCache.Entry entry = cache.entryForTesting(project);
        CodeReviewCommit.CodeReviewRevWalk spyRw = spy(entry.rw);
        entry.rw = spyRw;

        int additionalCandidates = 39;
        for (int i = 0; i < additionalCandidates; i++) {
          Void unusedResult =
              cache.run(
                  project,
                  null,
                  (repo, rw, alreadyAccepted) -> {
                    RevFlag flag = rw.newFlag("test");
                    rw.disposeFlag(flag);
                    return null;
                  });
        }

        // The accepted set is parsed once for the project, even though 40 candidates were
        // evaluated against it.
        submitDryRun.verify(
            () -> {
              var unusedAccepted = SubmitDryRun.getAlreadyAccepted(any(Repository.class));
            },
            times(1));
        submitDryRun.verify(() -> SubmitDryRun.addCommits(any(), any(), any()), times(1));
        // Each run() resets the walk before and after evaluating a candidate.
        verify(spyRw, times(additionalCandidates * 2)).reset();
      }
    }
  }
}
