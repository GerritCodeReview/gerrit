// Copyright (C) 2024 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Result of invoking a {@link CommitValidationListener} if the commit passed the validator.
 *
 * <p>Note, if a commit is rejected by a {@link CommitValidationListener} it throws a {@link
 * CommitValidationException} and no {@code CommitValidationInfo} is returned. Hence {@code
 * CommitValidationInfo} doesn't cover rejections.
 */
@AutoValue
public abstract class CommitValidationInfo {
  /** Empty metadata map. */
  public static final ImmutableMap<String, String> NO_METADATA = ImmutableMap.of();

  public enum Status {
    /** The validation has been performed and the commit passed the validation. */
    PASSED,

    /**
     * The validation was not done because it was not applicable, for example the validator
     * configuration didn't match the commit that was uploaded/created.
     */
    NOT_APPLICABLE,

    /** The validation has been skipped by the user. */
    SKIPPED_BY_USER,
  }

  /** Status of the commit validation run. */
  public abstract Status status();

  /**
   * Metadata about the commit validation that has been performed, for example the version ID of the
   * configuration that was used for the commit validation or the SHA1 from which the configuration
   * that was used for the commit validation was read.
   */
  public abstract ImmutableMap<String, String> metadata();

  /** Validation messages collected during the commit validation run. */
  public abstract ImmutableList<CommitValidationMessage> validationMessages();

  public static CommitValidationInfo passed(
      ImmutableMap<String, String> metadata,
      ImmutableList<CommitValidationMessage> validationMessages) {
    return new AutoValue_CommitValidationInfo(Status.PASSED, metadata, validationMessages);
  }

  public static CommitValidationInfo notApplicable(ImmutableMap<String, String> metadata) {
    return new AutoValue_CommitValidationInfo(Status.NOT_APPLICABLE, metadata, ImmutableList.of());
  }

  public static CommitValidationInfo skippedByUser(ImmutableMap<String, String> metadata) {
    return new AutoValue_CommitValidationInfo(Status.SKIPPED_BY_USER, metadata, ImmutableList.of());
  }
}
