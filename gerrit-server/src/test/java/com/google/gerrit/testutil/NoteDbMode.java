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
import com.google.common.collect.ImmutableList;

public enum NoteDbMode {
  /** NoteDb is disabled. */
  OFF,

  /** Writing data to NoteDb is enabled. */
  WRITE,

  /** Reading and writing all data to NoteDb is enabled. */
  READ_WRITE,

  /**
   * Run tests with NoteDb disabled, then convert ReviewDb to NoteDb and check that the results
   * match.
   */
  CHECK;

  private static final String VAR = "GERRIT_NOTEDB";

  public static NoteDbMode get() {
    if (isEnvVarTrue("GERRIT_ENABLE_NOTEDB")) {
      // TODO(dborowitz): Remove once GerritForge CI is migrated.
      return READ_WRITE;
    }
    String value = System.getenv(VAR);
    if (Strings.isNullOrEmpty(value)) {
      return OFF;
    }
    value = value.toUpperCase().replace("-", "_");
    NoteDbMode mode = Enums.getIfPresent(NoteDbMode.class, value).orNull();
    checkArgument(mode != null, "Invalid value for %s: %s", VAR, System.getenv(VAR));
    return mode;
  }

  public static boolean readWrite() {
    return get() == READ_WRITE;
  }

  private static boolean isEnvVarTrue(String name) {
    String value = Strings.nullToEmpty(System.getenv(name)).toLowerCase();
    return ImmutableList.of("yes", "y", "true", "1").contains(value);
  }
}
