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

package com.google.gerrit.server.update;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import java.sql.Timestamp;

/**
 * Context for performing the {@link BatchUpdateOp#updateChange} phase.
 *
 * <p>A single {@code ChangeContext} corresponds to updating a single change; if a {@link
 * BatchUpdate} spans multiple changes, then multiple {@code ChangeContext} instances will be
 * created.
 */
public interface ChangeContext extends Context {
  /**
   * Get the first update for this change at a given patch set.
   *
   * <p>A single operation can modify changes at different patch sets. Commits in the NoteDb graph
   * within this update are created in patch set order.
   *
   * <p>To get the current patch set ID, use {@link com.google.gerrit.server.PatchSetUtil#current}.
   *
   * @param psId patch set ID.
   * @return handle for change updates.
   */
  ChangeUpdate getUpdate(PatchSet.Id psId);

  /**
   * Gets a new ChangeUpdate for this change at a given patch set.
   *
   * <p>To get the current patch set ID, use {@link com.google.gerrit.server.PatchSetUtil#current}.
   *
   * @param psId patch set ID.
   * @return handle for change updates.
   */
  ChangeUpdate getDistinctUpdate(PatchSet.Id psId);

  /**
   * Get the up-to-date notes for this change.
   *
   * <p>The change data is read within the same transaction that {@link
   * BatchUpdateOp#updateChange(ChangeContext)} is executing.
   *
   * @return notes for this change.
   */
  ChangeNotes getNotes();

  /**
   * Instruct {@link BatchUpdate} to delete this change.
   *
   * <p>If called, all other updates are ignored.
   */
  void deleteChange();

  /** @return change corresponding to {@link #getNotes()}. */
  default Change getChange() {
    return requireNonNull(getNotes().getChange());
  }
}
