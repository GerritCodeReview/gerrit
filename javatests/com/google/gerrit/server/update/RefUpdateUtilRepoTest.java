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

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RefUpdateUtilRepoTest {
  public enum RepoSetup {
    LOCAL_DISK {
      @Override
      Repository setUpRepo() throws Exception {
        Path p = Files.createTempDirectory("gerrit_repo_");
        try {
          Repository repo = new FileRepository(p.toFile());
          repo.create(true);
          return repo;
        } catch (Exception e) {
          delete(p);
          throw e;
        }
      }

      @Override
      void tearDownRepo(Repository repo) throws Exception {
        delete(repo.getDirectory().toPath());
      }

      private void delete(Path p) throws Exception {
        MoreFiles.deleteRecursively(p, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    },

    IN_MEMORY {
      @Override
      Repository setUpRepo() {
        return new InMemoryRepository(new DfsRepositoryDescription("repo"));
      }

      @Override
      void tearDownRepo(Repository repo) {}
    };

    abstract Repository setUpRepo() throws Exception;

    abstract void tearDownRepo(Repository repo) throws Exception;
  }

  @Parameters(name = "{0}")
  public static ImmutableList<RepoSetup[]> data() {
    return ImmutableList.copyOf(new RepoSetup[][] {{RepoSetup.LOCAL_DISK}, {RepoSetup.IN_MEMORY}});
  }

  @Parameter public RepoSetup repoSetup;

  private Repository repo;

  @Before
  public void setUp() throws Exception {
    repo = repoSetup.setUpRepo();
  }

  @After
  public void tearDown() throws Exception {
    if (repo != null) {
      repoSetup.tearDownRepo(repo);
      repo = null;
    }
  }

  @Test
  public void deleteRefNoOp() throws Exception {
    String ref = "refs/heads/foo";
    assertThat(repo.exactRef(ref)).isNull();
    RefUpdateUtil.deleteChecked(repo, "refs/heads/foo");
    assertThat(repo.exactRef(ref)).isNull();
  }

  @Test
  public void deleteRef() throws Exception {
    String ref = "refs/heads/foo";
    new TestRepository<>(repo).branch(ref).commit().create();
    assertThat(repo.exactRef(ref)).isNotNull();
    RefUpdateUtil.deleteChecked(repo, "refs/heads/foo");
    assertThat(repo.exactRef(ref)).isNull();
  }
}
