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

package com.google.gerrit.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Static utilities for working with JGit's ref update APIs. */
public class RefUpdateUtil {
  /**
   * Execute a batch ref update, throwing a checked exception if not all updates succeeded.
   *
   * <p>Creates a new {@link RevWalk} used only for this operation.
   *
   * @param bru batch update; should already have been executed.
   * @param repo repository that created {@code bru}.
   * @throws LockFailureException if the transaction was aborted due to lock failure; see {@link
   *     #checkResults(BatchRefUpdate)} for details.
   * @throws IOException if any result was not {@code OK}.
   */
  public static void executeChecked(BatchRefUpdate bru, Repository repo) throws IOException {
    try (RevWalk rw = new RevWalk(repo)) {
      executeChecked(bru, rw);
    }
  }

  public static void execute(BatchRefUpdate bru, Repository repo) throws LockFailureException {
    // TODO(dborowitz): Consider getting rid of executeChecked, or else flipping the implementation
    // dependency.
    try {
      executeChecked(bru, repo);
    } catch (IOException e) {
      Throwables.propagateIfInstanceOf(e, LockFailureException.class);
      throw new StorageException(e);
    }
  }

  /**
   * Execute a batch ref update, throwing a checked exception if not all updates succeeded.
   *
   * @param bru batch update; should already have been executed.
   * @param rw walk for executing the update.
   * @throws LockFailureException if the transaction was aborted due to lock failure; see {@link
   *     #checkResults(BatchRefUpdate)} for details.
   * @throws IOException if any result was not {@code OK}.
   */
  public static void executeChecked(BatchRefUpdate bru, RevWalk rw) throws IOException {
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
    if (bru.getCommands().isEmpty()) {
      return;
    }

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
      throw new LockFailureException("Update aborted with one or more lock failures: " + bru, bru);
    } else if (failure > 0) {
      throw new IOException("Update failed: " + bru);
    }
  }

  /**
   * Check results of a single ref update, throwing an exception if there was a failure.
   *
   * @param ru ref update; must already have been executed.
   * @throws IllegalArgumentException if the result was {@code NOT_ATTEMPTED}.
   * @throws LockFailureException if the result was {@code LOCK_FAILURE}.
   * @throws IOException if the result failed for another reason.
   */
  public static void checkResult(RefUpdate ru) throws IOException {
    RefUpdate.Result result = ru.getResult();
    switch (result) {
      case NOT_ATTEMPTED:
        throw new IllegalArgumentException("Not attempted: " + ru.getName());
      case NEW:
      case FORCED:
      case NO_CHANGE:
      case FAST_FORWARD:
      case RENAMED:
        return;
      case LOCK_FAILURE:
        throw new LockFailureException("Failed to update " + ru.getName() + ": " + result, ru);
      default:
      case IO_FAILURE:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
        throw new IOException("Failed to update " + ru.getName() + ": " + ru.getResult());
    }
  }

  /**
   * Delete a single ref, throwing a checked exception on failure.
   *
   * <p>Does not require that the ref have any particular old value. Succeeds as a no-op if the ref
   * did not exist.
   *
   * @param repo repository.
   * @param refName ref name to delete.
   * @throws LockFailureException if a low-level lock failure (e.g. compare-and-swap failure)
   *     occurs.
   * @throws IOException if an error occurred.
   */
  public static void deleteChecked(Repository repo, String refName) throws IOException {
    RefUpdate ru = repo.updateRef(refName);
    ru.setForceUpdate(true);
    switch (ru.delete()) {
      case FORCED:
        // Ref was deleted.
        return;

      case NEW:
        // Ref didn't exist (yes, really).
        return;

      case LOCK_FAILURE:
        throw new LockFailureException("Failed to delete " + refName + ": " + ru.getResult(), ru);

        // Not really failures, but should not be the result of a deletion, so the best option is to
        // throw.
      case NO_CHANGE:
      case FAST_FORWARD:
      case RENAMED:
      case NOT_ATTEMPTED:

      case IO_FAILURE:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        throw new IOException("Failed to delete " + refName + ": " + ru.getResult());
    }
  }

  private RefUpdateUtil() {}
}
