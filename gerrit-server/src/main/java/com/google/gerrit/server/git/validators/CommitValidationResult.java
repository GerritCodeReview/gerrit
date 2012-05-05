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

/**
 * Result of a commit validation from a CommitValidatorListener.
 *
 * Commit validators should return CommitValidationResult.SUCCESS
 * in case of valid commit and CommitValidationResult.FAILURE in
 * case of rejected commits.
 *
 * When reason of the failure needs to be displayed on the remote
 * client, {@link #newFailure(String)} can be used to return additional
 * textual description.
 */
public class CommitValidationResult {

  /**
   * Successful commit validation.
   */
  public static final CommitValidationResult SUCCESS =
      new CommitValidationResult(true, "");

  /**
   * Commit validation failed.
   */
  public static final CommitValidationResult FAILURE =
      new CommitValidationResult(false, "");

  /**
   * Commit validation failed with a reason.
   *
   * @param why reason of the commit validation failure.
   *
   * @return validation failure with reason.
   */
  public static CommitValidationResult newFailure(String why) {
    return new CommitValidationResult(false, why);
  }

  /**
   * Validation status.
   */
  public final boolean validated;

  /**
   * Additional textual description for remote clients.
   */
  public final String why;

  CommitValidationResult(boolean validated, String why) {
    this.validated = validated;
    this.why = why;
  }
}
