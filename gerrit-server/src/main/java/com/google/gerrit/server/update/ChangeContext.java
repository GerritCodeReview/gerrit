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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;

/**
 * Context for performing the {@link BatchUpdateOp#updateChange} phase.
 *
 * <p>A single {@code ChangeContext} corresponds to updating a single change; if a {@link
 * BatchUpdate} spans multiple changes, then multiple {@code ChangeContext} instances will be
 * created.
 */
public interface ChangeContext extends Context {
  /**
   * Get an update for this change at a given patch set.
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
   * Get the control for this change, encapsulating the user and up-to-date change data.
   *
   * <p>The user will be the same as {@link #getUser()}, and the change data is read within the same
   * transaction that {@link BatchUpdateOp#updateChange(ChangeContext)} is executing.
   *
   * @return control for this change.
   */
  ChangeControl getControl();

  /**
   * Get an instance of {@link PermissionBackend.ForChange} to perform permission checks by calling
   * on {@code check} and {@code test}.
   *
   * @return permissionBackend for this change.
   */
  PermissionBackend.ForChange permissions();

  /**
   * Don't bump the value of {@link Change#getLastUpdatedOn()}.
   *
   * <p>If called, don't bump the timestamp before storing to ReviewDb. Only has an effect in
   * ReviewDb, and the only usage should be to match the behavior of NoteDb. Specifically, in NoteDb
   * the timestamp is updated if and only if the change meta graph is updated, and is not updated
   * when only drafts are modified.
   */
  void dontBumpLastUpdatedOn();

  /**
   * Instruct {@link BatchUpdate} to delete this change.
   *
   * <p>If called, all other updates are ignored.
   */
  void deleteChange();

  /**
   * Get notes corresponding to {@link #getControl()}.
   *
   * @return loaded notes instance.
   */
  default ChangeNotes getNotes() {
    return checkNotNull(getControl().getNotes());
  }

  /** @return change corresponding to {@link #getControl()}. */
  default Change getChange() {
    return checkNotNull(getControl().getChange());
  }
}
