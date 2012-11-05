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
 * Result of a commit validation from a CommitValidationListener.
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

  private boolean validated;
  private String message;

  /**
   * Successful commit validation.
   */
  public static final CommitValidationResult SUCCESS =
      new CommitValidationResult(true, "");

  /**
   * Commit validation failed.
   */
  public static final CommitValidationResult FAILURE =
      new CommitValidationResult(false, "Prohibited by Gerrit, invalid commit");

  /**
   * Commit validation failed with a reason.
   *
   * @param message reason of the commit validation failure.
   *
   * @return validation failure with reason.
   */
  public static CommitValidationResult newFailure(String message) {
    return new CommitValidationResult(false, message);
  }

  /**
   * Commit validation result and reason.
   *
   * @param validated true if commit is valid or false if has to be rejected.
   * @param message reason of the commit validation failure or warning message when
   *            commit has been validated.
   */
  protected CommitValidationResult(boolean validated, String message) {
    this.validated = validated;
    this.message = message;
  }

  /**
   * Gets validation status.
   *
   * @return validation status.
   */
  public boolean isValidated() {
    return validated;
  }

  /**
   * Sets validation status.
   * @param validated the validation status
   */
  public void setIsValidated(boolean validated) {
    this.validated = validated;
  }

  /**
   * Gets additional textual description for the validation.
   *
   * @return textual validation description.
   */
  public String getValidationReason() {
    return message;
  }

  /**
   * Sets additional textual description for the validation.
   * @param message the textual validation description.
   */
  public void setValidationReason(String message) {
    this.message = message;
  }
}
