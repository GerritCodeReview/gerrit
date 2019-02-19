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

package com.google.gerrit.plugins.checkers.api;

import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Objects;

/** REST API representation of a {@link com.google.gerrit.plugins.checkers.Check}. */
public class CheckInfo {
  /** Project name that this check applies to. */
  public String project;
  /** Change number that this check applies to. */
  public int changeNumber;
  /** Patch set ID that this check applies to. */
  public int patchSetId;
  /** UUID of the checker that posted this check. */
  public String checkerUUID;

  /** State that this check exited. */
  public CheckState state;
  /** Fully qualified URL to detailed result on the Checker's service. */
  @Nullable public String url;
  /** Timestamp of when this check was created. */
  @Nullable public Timestamp started;
  /** Timestamp of when this check was last updated. */
  @Nullable public Timestamp finished;

  /** Timestamp of when this check was created. */
  public Timestamp created;
  /** Timestamp of when this check was last updated. */
  public Timestamp updated;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckInfo)) {
      return false;
    }
    CheckInfo other = (CheckInfo) o;
    return Objects.equals(other.project, project)
        && Objects.equals(other.changeNumber, changeNumber)
        && Objects.equals(other.patchSetId, patchSetId)
        && Objects.equals(other.checkerUUID, checkerUUID)
        && Objects.equals(other.state, state)
        && Objects.equals(other.url, url)
        && Objects.equals(other.started, started)
        && Objects.equals(other.finished, finished)
        && Objects.equals(other.created, created)
        && Objects.equals(other.updated, updated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        project,
        changeNumber,
        patchSetId,
        checkerUUID,
        state,
        url,
        started,
        finished,
        created,
        updated);
  }
}
