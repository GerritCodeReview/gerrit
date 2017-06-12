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
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.NotesMigrationState;

public enum NoteDbMode {
  /** NoteDb is disabled. */
  OFF(NotesMigrationState.REVIEW_DB),

  /** Writing data to NoteDb is enabled. */
  WRITE(NotesMigrationState.WRITE),

  /** Reading and writing all data to NoteDb is enabled. */
  READ_WRITE(NotesMigrationState.READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY),

  /** Changes are created with their primary storage as NoteDb. */
  PRIMARY(NotesMigrationState.READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY),

  /** All change tables are entirely disabled. */
  DISABLE_CHANGE_REVIEW_DB(NotesMigrationState.NOTE_DB_UNFUSED),

  /** All change tables are entirely disabled, and code/meta ref updates are fused. */
  FUSED(NotesMigrationState.NOTE_DB),

  /**
   * Run tests with NoteDb disabled, then convert ReviewDb to NoteDb and check that the results
   * match.
   */
  CHECK(NotesMigrationState.REVIEW_DB);

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
    NotesMigration migration = get().migration;
    return migration.rawWriteChangesSetting() && migration.readChanges();
  }

  final NotesMigration migration;

  private NoteDbMode(NotesMigrationState state) {
    migration = state.migration();
  }
}
