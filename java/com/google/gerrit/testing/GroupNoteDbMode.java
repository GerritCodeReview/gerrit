// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.gerrit.server.notedb.GroupsMigration;

public enum GroupNoteDbMode {
  /** NoteDb is disabled, groups are only in ReviewDb */
  OFF(new GroupsMigration(false, false, false)),

  /** Writing new groups to NoteDb is enabled. */
  WRITE(new GroupsMigration(true, false, false)),

  /**
   * Reading/writing groups from/to NoteDb is enabled. Trying to read groups from ReviewDb throws an
   * exception.
   */
  READ_WRITE(new GroupsMigration(true, true, false)),

  /**
   * All group tables in ReviewDb are entirely disabled. Trying to read groups from ReviewDb throws
   * an exception. Reading groups through an unwrapped ReviewDb instance writing groups to ReviewDb
   * is a No-Op.
   */
  ON(new GroupsMigration(true, true, true));

  private static final String ENV_VAR = "GERRIT_NOTEDB_GROUPS";
  private static final String SYS_PROP = "gerrit.notedb.groups";

  public static GroupNoteDbMode get() {
    String value = System.getenv(ENV_VAR);
    if (Strings.isNullOrEmpty(value)) {
      value = System.getProperty(SYS_PROP);
    }
    if (Strings.isNullOrEmpty(value)) {
      return OFF;
    }
    value = value.toUpperCase().replace("-", "_");
    GroupNoteDbMode mode = Enums.getIfPresent(GroupNoteDbMode.class, value).orNull();
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

  private final GroupsMigration groupsMigration;

  private GroupNoteDbMode(GroupsMigration groupsMigration) {
    this.groupsMigration = groupsMigration;
  }

  public GroupsMigration getGroupsMigration() {
    return groupsMigration;
  }
}
