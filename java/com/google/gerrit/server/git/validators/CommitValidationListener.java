// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import static com.google.gerrit.server.git.validators.CommitValidationInfo.NO_METADATA;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.events.CommitReceivedEvent;
import java.util.List;

/**
 * Listener to provide validation on received commits.
 *
 * <p>Invoked by Gerrit when a new commit is received, has passed basic Gerrit validation and can be
 * then subject to extra validation checks.
 *
 * <p>Do not use {@link com.google.gerrit.server.patch.DiffOperations} from {@code
 * CommitValidationListener} implementations to get the modified files for the received commit,
 * instead use {@link com.google.gerrit.server.patch.DiffOperationsForCommitValidation} that is
 * provided in {@link CommitReceivedEvent#diffOperations}.
 */
@ExtensionPoint
public interface CommitValidationListener {
  /**
   * Name of the validator.
   *
   * <p>Must return a unique name (i.e. a name that is not used by any other validator).
   */
  default String getValidatorName() {
    return getClass().getName();
  }

  /**
   * Runs a commit validation.
   *
   * <p>This method only exist for backwards-compatibility and doesn't need to be implemented when
   * {@link #validateCommit(CommitReceivedEvent)} is implemented.
   *
   * @param receiveEvent commit event details
   * @return list of validation messages if the commit passes the validation
   * @throws CommitValidationException if validation fails and the commit is rejected
   * @deprecated use {@link #validateCommit(CommitReceivedEvent)} instead
   */
  @Deprecated
  default List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    throw new IllegalStateException("not implemented");
  }

  /**
   * Runs a commit validation.
   *
   * <p>Implement this method instead of {@link #onCommitReceived(CommitReceivedEvent)}.
   *
   * @param receiveEvent commit event details
   * @return result of the commit validation if the commit passes the validation
   * @throws CommitValidationException if validation fails and the commit is rejected
   */
  default CommitValidationInfo validateCommit(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    return CommitValidationInfo.passed(
        NO_METADATA, ImmutableList.copyOf(onCommitReceived(receiveEvent)));
  }

  /**
   * Whether this validator should validate all commits.
   *
   * @return {@code true} if this validator should validate all commits, even when the {@code
   *     skip-validation} push option was specified.
   */
  default boolean shouldValidateAllCommits() {
    return false;
  }
}
