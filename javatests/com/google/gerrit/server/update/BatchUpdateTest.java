// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BatchUpdateTest extends GerritBaseTests {
  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Inject private GitRepositoryManager repoManager;
  @Inject private BatchUpdate.Factory batchUpdateFactory;
  @Inject private Provider<CurrentUser> user;

  private Project.NameKey project;
  private TestRepository<Repository> repo;

  @Before
  public void setUp() throws Exception {
    project = new Project.NameKey("test");

    Repository inMemoryRepo = repoManager.createRepository(project);
    repo = new TestRepository<>(inMemoryRepo);
  }

  @Test
  public void addRefUpdateFromFastForwardCommit() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.addRepoOnlyOp(
          new RepoOnlyOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws Exception {
              ctx.addRefUpdate(masterCommit.getId(), branchCommit.getId(), "refs/heads/master");
            }
          });
      bu.execute();
    }

    assertThat(repo.getRepository().exactRef("refs/heads/master").getObjectId())
        .isEqualTo(branchCommit.getId());
  }
}
