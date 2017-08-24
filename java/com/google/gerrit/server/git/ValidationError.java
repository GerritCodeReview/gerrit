// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.slf4j.Logger;

/** Indicates a problem with Git based data. */
public class ValidationError {
  private final String message;

  public ValidationError(String file, String message) {
    this(file + ": " + message);
  }

  public ValidationError(String file, int line, String message) {
    this(file + ":" + line + ": " + message);
  }

  public ValidationError(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ValidationError[" + message + "]";
  }

  public interface Sink {
    void error(ValidationError error);
  }

  public static Sink createLoggerSink(String message, Logger log) {
    return new ValidationError.Sink() {
      @Override
      public void error(ValidationError error) {
        log.error(message + error.getMessage());
      }
    };
  }
}
