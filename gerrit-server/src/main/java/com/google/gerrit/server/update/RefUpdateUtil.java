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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.git.LockFailureException;
import java.io.IOException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Static utilities for working with JGit's ref update APIs. */
public class RefUpdateUtil {
  /**
   * Execute a batch ref update, throwing a checked exception if not all updates succeeded.
   *
   * @param bru batch update; should already have been executed.
   * @throws LockFailureException if the transaction was aborted due to lock failure; see {@link
   *     #checkResults(BatchRefUpdate)} for details.
   * @throws IOException if any result was not {@code OK}.
   */
  public static void executeChecked(BatchRefUpdate bru, RevWalk rw)
      throws LockFailureException, IOException {
    bru.execute(rw, NullProgressMonitor.INSTANCE);
    checkResults(bru);
  }

  /**
   * Check results of all commands in the update batch, reducing to a single exception if there was
   * a failure.
   *
   * <p>Throws {@link LockFailureException} if at least one command failed with {@code
   * LOCK_FAILURE}, and the entire transaction was aborted, i.e. any non-{@code LOCK_FAILURE}
   * results, if there were any, failed with "transaction aborted".
   *
   * <p>In particular, if the underlying ref database does not {@link
   * org.eclipse.jgit.lib.RefDatabase#performsAtomicTransactions() perform atomic transactions},
   * then a combination of {@code LOCK_FAILURE} on one ref and {@code OK} or another result on other
   * refs will <em>not</em> throw {@code LockFailureException}.
   *
   * @param bru batch update; should already have been executed.
   * @throws LockFailureException if the transaction was aborted due to lock failure.
   * @throws IOException if any result was not {@code OK}.
   */
  @VisibleForTesting
  static void checkResults(BatchRefUpdate bru) throws IOException {
    int lockFailure = 0;
    int aborted = 0;
    int failure = 0;

    for (ReceiveCommand cmd : bru.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        failure++;
      }
      if (cmd.getResult() == ReceiveCommand.Result.LOCK_FAILURE) {
        lockFailure++;
      } else if (cmd.getResult() == ReceiveCommand.Result.REJECTED_OTHER_REASON
          && JGitText.get().transactionAborted.equals(cmd.getMessage())) {
        aborted++;
      }
    }

    if (lockFailure + aborted == bru.getCommands().size()) {
      throw new LockFailureException("Update aborted with one or more lock failures: " + bru);
    } else if (failure > 0) {
      throw new IOException("Update failed: " + bru);
    }
  }

  private RefUpdateUtil() {}
}
