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

package com.google.gerrit.extensions.restapi;

import java.util.Optional;

/** Caller cannot perform the request operation (HTTP 403 Forbidden). */
public class AuthException extends RestApiException {
  private static final long serialVersionUID = 1L;

  private Optional<String> advice = Optional.empty();

  /** @param msg message to return to the client. */
  public AuthException(String msg) {
    super(msg);
  }

  /**
   * @param msg message to return to the client.
   * @param cause cause of this exception.
   */
  public AuthException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public void setAdvice(String advice) {
    this.advice = Optional.of(advice);
  }

  /**
   * Advice that the user can follow to acquire authorization to perform the
   * action.
   * <p>
   * This may be long-form text with newlines, and may be printed to a terminal,
   * for example in the message stream in response to a push.
   */
  public Optional<String> getAdvice() {
    return advice;
  }
}
