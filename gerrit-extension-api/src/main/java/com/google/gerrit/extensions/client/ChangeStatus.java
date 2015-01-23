// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

/* Current state within the basic workflow of the change **/
public enum ChangeStatus {

  /**
   * Change is open and pending review, or review is in progress.
   *
   * <p>
   * This is the default state assigned to a change when it is first created
   * in the database. A change stays in the NEW state throughout its review
   * cycle, until the change is submitted or abandoned.
   *
   * <p>
   * Changes in the NEW state can be moved to:
   * <ul>
   * <li>{@link #SUBMITTED} - when the Submit Patch Set action is used;
   * <li>{@link #ABANDONED} - when the Abandon action is used.
   * </ul>
   */
  NEW,

  /**
   * Change is open, but has been submitted to the merge queue.
   *
   * <p>
   * A change enters the SUBMITTED state when an authorized user presses the
   * "submit" action through the web UI, requesting that Gerrit merge the
   * change's current patch set into the destination branch.
   *
   * <p>
   * Typically a change resides in the SUBMITTED for only a brief sub-second
   * period while the merge queue fires and the destination branch is updated.
   * However, if a dependency commit (directly or transitively) is not yet
   * merged into the branch, the change will hang in the SUBMITTED state
   * indefinitely.
   *
   * <p>
   * Changes in the SUBMITTED state can be moved to:
   * <ul>
   * <li>{@link #NEW} - when a replacement patch set is supplied, OR when a
   * merge conflict is detected;
   * <li>{@link #MERGED} - when the change has been successfully merged into
   * the destination branch;
   * <li>{@link #ABANDONED} - when the Abandon action is used.
   * </ul>
   */
  SUBMITTED,

  /**
   * Change is a draft change that only consists of draft patchsets.
   *
   * <p>
   * This is a change that is not meant to be submitted or reviewed yet. If
   * the uploader publishes the change, it becomes a NEW change.
   * Publishing is a one-way action, a change cannot return to DRAFT status.
   * Draft changes are only visible to the uploader and those explicitly
   * added as reviewers.
   *
   * <p>
   * Changes in the DRAFT state can be moved to:
   * <ul>
   * <li>{@link #NEW} - when the change is published, it becomes a new change;
   * </ul>
   */
  DRAFT,

  /**
   * Change is closed, and submitted to its destination branch.
   *
   * <p>
   * Once a change has been merged, it cannot be further modified by adding a
   * replacement patch set. Draft comments however may be published,
   * supporting a post-submit review.
   */
  MERGED,

  /**
   * Change is closed, but was not submitted to its destination branch.
   *
   * <p>
   * Once a change has been abandoned, it cannot be further modified by adding
   * a replacement patch set, and it cannot be merged. Draft comments however
   * may be published, permitting reviewers to send constructive feedback.
   */
  ABANDONED
}