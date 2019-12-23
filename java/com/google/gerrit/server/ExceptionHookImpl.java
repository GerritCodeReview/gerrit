// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.gerrit.git.LockFailureException;
import java.util.Optional;
import org.eclipse.jgit.lib.RefUpdate;

/**
 * Class to detect and handle exceptions that are caused by temporary errors, and hence should cause
 * a retry of the failed operation.
 */
public class ExceptionHookImpl implements ExceptionHook {
  private static final String LOCK_FAILURE_USER_MESSAGE =
      "Updating a ref failed with LOCK_FAILURE.\n"
          + "This may be a temporary issue due to concurrent updates.\n"
          + "Please retry later.";

  @Override
  public boolean shouldRetry(String actionType, String actionName, Throwable throwable) {
    return isLockFailure(throwable);
  }

  @Override
  public Optional<String> formatCause(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(RefUpdate.Result.LOCK_FAILURE.name());
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> getUserMessage(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(LOCK_FAILURE_USER_MESSAGE);
    }
    return Optional.empty();
  }

  @Override
  public Optional<Status> getStatus(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(Status.create(503, "Lock failure"));
    }
    return Optional.empty();
  }

  private static boolean isLockFailure(Throwable throwable) {
    return isMatching(throwable, t -> t instanceof LockFailureException);
  }

  /**
   * Check whether the given exception or any of its causes matches the given predicate.
   *
   * @param throwable Exception that should be tested
   * @param predicate predicate to check if a throwable matches
   * @return {@code true} if the given exception or any of its causes matches the given predicate
   */
  private static boolean isMatching(Throwable throwable, Predicate<Throwable> predicate) {
    return Throwables.getCausalChain(throwable).stream().anyMatch(predicate);
  }
}
