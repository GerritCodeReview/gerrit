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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevFlag;
import org.junit.Test;

public class AcceptedRevWalkCacheTest {
  @Test
  public void parsesAcceptedSetOnceAndReusesFlagsAcrossCandidates() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    Project.NameKey project = Project.nameKey("accepted-walk");
    try (Repository ignored = repoManager.createRepository(project)) {
      AcceptedRevWalkCache cache = new AcceptedRevWalkCache(repoManager);
      for (int i = 0; i < 40; i++) {
        Void unused =
            cache.run(
                project,
                ignoredRepo -> ImmutableSet.of(),
                null,
                (repo, rw, alreadyAccepted) -> {
                  RevFlag flag = rw.newFlag("test");
                  rw.disposeFlag(flag);
                  return null;
                });
      }

      assertThat(cache.acceptedSetParseCount(project)).isEqualTo(1);
      assertThat(cache.resetCount()).isEqualTo(80);
    }
  }
}
