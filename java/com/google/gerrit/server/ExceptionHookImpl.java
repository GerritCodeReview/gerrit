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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.exceptions.MergeUpdateException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.Optional;
import org.eclipse.jgit.errors.MissingObjectException;
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
  private static final String INVALID_PROJECT_CONFIG_USER_MESSAGE =
      "Invalid " + ProjectConfig.PROJECT_CONFIG + " file.";
  private static final String CONTACT_PROJECT_OWNER_USER_MESSAGE =
      "Please contact the project owner.";

  @Override
  public boolean shouldRetry(String actionType, String actionName, Throwable throwable) {
    return isLockFailure(throwable);
  }

  @Override
  public boolean skipRetryWithTrace(String actionType, String actionName, Throwable throwable) {
    return isInvalidProjectConfig(throwable);
  }

  @Override
  public Optional<String> formatCause(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(RefUpdate.Result.LOCK_FAILURE.name());
    }
    if (isMissingObjectException(throwable)) {
      return Optional.of("missing_object");
    }
    if (isInvalidProjectConfig(throwable)) {
      return Optional.of("invalid_project_config");
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<String> getUserMessages(Throwable throwable, ImmutableSet<String> traceIds) {
    if (isLockFailure(throwable)) {
      return ImmutableList.of(LOCK_FAILURE_USER_MESSAGE);
    }
    if (isInvalidProjectConfig(throwable)) {
      return ImmutableList.of(
          getInvalidConfigMessage(throwable).orElse(INVALID_PROJECT_CONFIG_USER_MESSAGE)
              + "\n"
              + CONTACT_PROJECT_OWNER_USER_MESSAGE);
    }
    if (throwable instanceof MergeUpdateException) {
      return ImmutableList.of(throwable.getMessage());
    }
    return ImmutableList.of();
  }

  @Override
  public Optional<Status> getStatus(Throwable throwable) {
    if (isLockFailure(throwable)) {
      return Optional.of(Status.create(503, "Lock failure"));
    }
    if (isInvalidProjectConfig(throwable)) {
      return Optional.of(Status.create(409, "Conflict"));
    }
    return Optional.empty();
  }

  private static boolean isLockFailure(Throwable throwable) {
    return isMatching(throwable, t -> t instanceof LockFailureException);
  }

  private static boolean isMissingObjectException(Throwable throwable) {
    return isMatching(throwable, t -> t instanceof MissingObjectException);
  }

  private static boolean isInvalidProjectConfig(Throwable throwable) {
    return isMatching(
        throwable,
        t ->
            t instanceof InvalidConfigFileException
                && ProjectConfig.PROJECT_CONFIG.equals(
                    ((InvalidConfigFileException) t).getFileName()));
  }

  private Optional<String> getInvalidConfigMessage(Throwable throwable) {
    return Throwables.getCausalChain(throwable).stream()
        .filter(InvalidConfigFileException.class::isInstance)
        .map(ex -> ex.getMessage())
        .findFirst();
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
