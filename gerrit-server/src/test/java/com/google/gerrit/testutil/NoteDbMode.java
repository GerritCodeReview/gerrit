// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.testutil;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Enums;
import com.google.common.base.Strings;

public enum NoteDbMode {
  /** NoteDb is disabled. */
  OFF(false),

  /** Writing data to NoteDb is enabled. */
  WRITE(false),

  /** Reading and writing all data to NoteDb is enabled. */
  READ_WRITE(true),

  /** Changes are created with their primary storage as NoteDb. */
  PRIMARY(true),

  /** All change tables are entirely disabled. */
  DISABLE_CHANGE_REVIEW_DB(true),

  /**
   * Run tests with NoteDb disabled, then convert ReviewDb to NoteDb and check that the results
   * match.
   */
  CHECK(false);

  private static final String ENV_VAR = "GERRIT_NOTEDB";
  private static final String SYS_PROP = "gerrit.notedb";

  public static NoteDbMode get() {
    String value = System.getenv(ENV_VAR);
    if (Strings.isNullOrEmpty(value)) {
      value = System.getProperty(SYS_PROP);
    }
    if (Strings.isNullOrEmpty(value)) {
      return OFF;
    }
    value = value.toUpperCase().replace("-", "_");
    NoteDbMode mode = Enums.getIfPresent(NoteDbMode.class, value).orNull();
    if (!Strings.isNullOrEmpty(System.getenv(ENV_VAR))) {
      checkArgument(
          mode != null, "Invalid value for env variable %s: %s", ENV_VAR, System.getenv(ENV_VAR));
    } else {
      checkArgument(
          mode != null,
          "Invalid value for system property %s: %s",
          SYS_PROP,
          System.getProperty(SYS_PROP));
    }
    return mode;
  }

  public static boolean readWrite() {
    return get().readWrite;
  }

  private final boolean readWrite;

  private NoteDbMode(boolean readWrite) {
    this.readWrite = readWrite;
  }
}
