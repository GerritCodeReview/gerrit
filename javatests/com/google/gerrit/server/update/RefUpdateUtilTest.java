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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gerrit.server.git.LockFailureException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RefUpdateUtilTest {
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
      Repository setUpRepo() throws Exception {
        return new InMemoryRepository(new DfsRepositoryDescription("repo"));
      }

      @Override
      void tearDownRepo(Repository repo) throws Exception {}
    };

    abstract Repository setUpRepo() throws Exception;

    abstract void tearDownRepo(Repository repo) throws Exception;
  }

  @Parameters(name = "{0}")
  public static ImmutableList<RepoSetup[]> data() {
    return ImmutableList.copyOf(new RepoSetup[][] {{RepoSetup.LOCAL_DISK}, {RepoSetup.IN_MEMORY}});
  }

  @Parameter public RepoSetup repoSetup;

  private static final Consumer<ReceiveCommand> OK = c -> c.setResult(ReceiveCommand.Result.OK);
  private static final Consumer<ReceiveCommand> LOCK_FAILURE =
      c -> c.setResult(ReceiveCommand.Result.LOCK_FAILURE);
  private static final Consumer<ReceiveCommand> REJECTED =
      c -> c.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON);
  private static final Consumer<ReceiveCommand> ABORTED =
      c -> {
        c.setResult(ReceiveCommand.Result.NOT_ATTEMPTED);
        ReceiveCommand.abort(ImmutableList.of(c));
        checkState(
            c.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED
                && c.getResult() != ReceiveCommand.Result.LOCK_FAILURE
                && c.getResult() != ReceiveCommand.Result.OK,
            "unexpected state after abort: %s",
            c);
      };

  private Repository repo;

  @After
  public void tearDown() throws Exception {
    if (repo != null) {
      repoSetup.tearDownRepo(repo);
    }
  }

  @Test
  public void checkBatchRefUpdateResults() throws Exception {
    checkResults();
    checkResults(OK);
    checkResults(OK, OK);

    assertIoException(REJECTED);
    assertIoException(OK, REJECTED);
    assertIoException(LOCK_FAILURE, REJECTED);
    assertIoException(LOCK_FAILURE, OK);
    assertIoException(LOCK_FAILURE, REJECTED, OK);
    assertIoException(LOCK_FAILURE, LOCK_FAILURE, REJECTED);
    assertIoException(LOCK_FAILURE, ABORTED, REJECTED);
    assertIoException(LOCK_FAILURE, ABORTED, OK);

    assertLockFailureException(LOCK_FAILURE);
    assertLockFailureException(LOCK_FAILURE, LOCK_FAILURE);
    assertLockFailureException(LOCK_FAILURE, LOCK_FAILURE, ABORTED);
    assertLockFailureException(LOCK_FAILURE, LOCK_FAILURE, ABORTED, ABORTED);
    assertLockFailureException(ABORTED);
    assertLockFailureException(ABORTED, ABORTED);
  }

  @Test
  public void deleteRefNoOp() throws Exception {
    String ref = "refs/heads/foo";
    assertThat(repo().exactRef(ref)).isNull();
    RefUpdateUtil.deleteChecked(repo(), "refs/heads/foo");
    assertThat(repo().exactRef(ref)).isNull();
  }

  @Test
  public void deleteRef() throws Exception {
    String ref = "refs/heads/foo";
    new TestRepository<>(repo()).branch(ref).commit().create();
    assertThat(repo().exactRef(ref)).isNotNull();
    RefUpdateUtil.deleteChecked(repo(), "refs/heads/foo");
    assertThat(repo().exactRef(ref)).isNull();
  }

  private Repository repo() throws Exception {
    if (repo == null) {
      repo = repoSetup.setUpRepo();
    }
    return repo;
  }

  @SafeVarargs
  private static void checkResults(Consumer<ReceiveCommand>... resultSetters) throws Exception {
    RefUpdateUtil.checkResults(newBatchRefUpdate(resultSetters));
  }

  @SafeVarargs
  private static void assertIoException(Consumer<ReceiveCommand>... resultSetters) {
    try {
      RefUpdateUtil.checkResults(newBatchRefUpdate(resultSetters));
      assert_().fail("expected IOException");
    } catch (IOException e) {
      assertThat(e).isNotInstanceOf(LockFailureException.class);
    }
  }

  @SafeVarargs
  private static void assertLockFailureException(Consumer<ReceiveCommand>... resultSetters)
      throws Exception {
    try {
      RefUpdateUtil.checkResults(newBatchRefUpdate(resultSetters));
      assert_().fail("expected LockFailureException");
    } catch (LockFailureException e) {
      // Expected.
    }
  }

  @SafeVarargs
  private static BatchRefUpdate newBatchRefUpdate(Consumer<ReceiveCommand>... resultSetters) {
    try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription("repo"))) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      for (int i = 0; i < resultSetters.length; i++) {
        ReceiveCommand cmd =
            new ReceiveCommand(
                ObjectId.fromString(String.format("%039x1", i)),
                ObjectId.fromString(String.format("%039x2", i)),
                "refs/heads/branch" + i);
        bru.addCommand(cmd);
        resultSetters[i].accept(cmd);
      }
      return bru;
    }
  }
}
