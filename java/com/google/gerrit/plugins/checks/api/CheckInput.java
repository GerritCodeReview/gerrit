// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Objects;

/** Input to create or update a {@link com.google.gerrit.plugins.checks.Check}. */
public class CheckInput {
  /** UUID of the checker. */
  public String checkerUUID;
  /** State of the check. */
  public CheckState state;
  /** Fully qualified URL to detailed result on the Checker's service. */
  @Nullable public String url;
  /** Date/Time at which the checker started processing this check. */
  @Nullable public Timestamp started;
  /** Date/Time at which the checker finished processing this check. */
  @Nullable public Timestamp finished;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckInfo)) {
      return false;
    }
    CheckInfo other = (CheckInfo) o;
    return Objects.equals(other.checkerUUID, checkerUUID)
        && Objects.equals(other.state, state)
        && Objects.equals(other.url, url)
        && Objects.equals(other.started, started)
        && Objects.equals(other.finished, finished);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkerUUID, state, url, started, finished);
  }
}
