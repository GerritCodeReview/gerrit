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
import com.google.gerrit.server.git.LockFailureException;
import java.io.IOException;
import java.util.function.Consumer;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefUpdateUtilTest {
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

  @Test
  public void checkBatchRefUpdateResults() throws Exception {
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
