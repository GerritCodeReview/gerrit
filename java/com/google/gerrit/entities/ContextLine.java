// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;

/**
 * An entity class representing 1 line of context {line number, line text} of the source file where
 * a comment was written.
 */
@AutoValue
public abstract class ContextLine {
  public static ContextLine create(int lineNumber, String contextLine) {
    return new AutoValue_ContextLine(lineNumber, contextLine);
  }

  public abstract int lineNumber();

  public abstract String contextLine();
}
