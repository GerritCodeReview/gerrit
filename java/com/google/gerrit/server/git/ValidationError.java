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

import com.google.auto.value.AutoValue;

/** Indicates a problem with Git based data. */
@AutoValue
public abstract class ValidationError {
  public abstract String getMessage();

  public static ValidationError create(String file, String message) {
    return create(file + ": " + message);
  }

  public static ValidationError create(String file, int line, String message) {
    return create(file + ":" + line + ": " + message);
  }

  public static ValidationError create(String message) {
    return new AutoValue_ValidationError(message);
  }

  public interface Sink {
    void error(ValidationError error);
  }
}
