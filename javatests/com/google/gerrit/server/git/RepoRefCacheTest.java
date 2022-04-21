// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.testing.GerritBaseTests;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class RepoRefCacheTest extends GerritBaseTests {

  private RepoRefCache objectUnderTest;
  private InMemoryRepository repo;
  private TestRepository<Repository> testRepo;

  @Before
  public void setUp() throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("foo");
    FS fs = FS.detect();
    fs.setUserHome(null);
    repo = new InMemoryRepository.Builder().setRepositoryDescription(desc).setFS(fs).build();

    testRepo = new TestRepository<>(repo);
    objectUnderTest = new RepoRefCache(repo);
  }

  @Test
  public void shouldCheckForStaleness() throws Exception {
    String refName = "refs/heads/foo";
    Optional<ObjectId> cachedObjId = objectUnderTest.get(refName);

    assertThat(cachedObjId).isEqualTo(Optional.empty());

    RefUpdate refUpdate = repo.getRefDatabase().newUpdate(refName, true);
    refUpdate.setNewObjectId(testRepo.commit().create().getId());

    assertThat(refUpdate.forceUpdate()).isEqualTo(Result.NEW);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> objectUnderTest.checkStaleness());
    assertThat(thrown).hasMessageThat().contains(refName);
  }
}
