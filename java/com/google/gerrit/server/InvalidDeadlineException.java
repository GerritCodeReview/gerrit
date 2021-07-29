// Copyright (C) 2021 The Android Open Source Project
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

/** Exception that is thrown is a deadline cannot be parsed. */
public class InvalidDeadlineException extends Exception {
  private static final long serialVersionUID = 1L;

  private static final String MESSAGE_PREFIX = "Invalid deadline. ";

  public InvalidDeadlineException(String message) {
    super(MESSAGE_PREFIX + message);
  }

  public InvalidDeadlineException(String message, Throwable cause) {
    super(MESSAGE_PREFIX + message, cause);
  }
}
