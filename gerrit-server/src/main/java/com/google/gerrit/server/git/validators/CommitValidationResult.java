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

import java.util.ArrayList;
import java.util.List;

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

  private boolean validated;
  private String why;
  private final List<CommitValidationMessage> messages = new ArrayList<CommitValidationMessage>();

  /**
   * Successful commit validation.
   */
  public static final CommitValidationResult SUCCESS =
      new CommitValidationResult(true, "");

  /**
   * Commit validation failed.
   */
  public static final CommitValidationResult FAILURE =
      new CommitValidationResult(false, "Prohibited by server, contact admin");

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

  protected CommitValidationResult(boolean validated, String why) {
    this.validated = validated;
    this.why = why;
  }

  public CommitValidationResult() {
    this.validated = false;
    this.why = "";
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
   * @return textual validation reason.
   */
  public String getValidationReason() {
    return why;
  }

  /**
   * Sets additional textual description for the validation.
   * @param why the textual validation reason.
   */
  public void setValidationReason(String why) {
    this.why = why;
  }

  /**
   * Add a new message.
   * @param message the message.
   */
  public void addMessage(String message) {
    messages.add(new CommitValidationMessage(message, false));
  }

  /**
   * Add a new error message.
   * @param error the error message.
   */
  public void addError(String error) {
    messages.add(new CommitValidationMessage(error, true));
  }

  /**
   * Get the list of messages.
   * @return the list of messages.
   */
  public List<CommitValidationMessage> getMessages() {
    return messages;
  }
}
