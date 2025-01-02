// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

/**
 * Information about conflicts in a revision that was created by Gerrit as a result of performing a
 * Git merge.
 */
public class ConflictsInfo {
  /**
   * The SHA1 of the commit that was used as {@code ours} for the Git merge that created the
   * revision.
   */
  public String ours;

  /**
   * The SHA1 of the commit that was used as {@code theirs} for the Git merge that created the
   * revision.
   */
  public String theirs;

  /**
   * Whether any of the files in the revision has a conflict due to merging {@link #ours} and {@link
   * #theirs}.
   *
   * <p>If {@code true} at least one of the files in the revision has a conflict and contains Git
   * conflict markers.
   *
   * <p>If {@code false} merging {@link #ours} and {@link #theirs} didn't have any conflict. In this
   * case the files in the revision may only contain Git conflict marker if they were already
   * present in {@link #ours} or {@link #theirs}.
   */
  public Boolean containsConflicts;
}
