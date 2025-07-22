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

/** Information about conflicts in a revision. */
public class ConflictsInfo {
  /**
   * The SHA1 of the commit that was used as the base commit for the Git merge that created the
   * revision.
   *
   * <p>A base is not set if:
   *
   * <ul>
   *   <li>the merged commits do not have a common ancestor (in this case {@link #noBaseReason} is
   *       {@link NoMergeBaseReason#NO_COMMON_ANCESTOR}).
   *   <li>the merged commits have multiple merge bases (happens for criss-cross-merges) and the
   *       base was computed (in this case {@link #noBaseReason} is {@link
   *       NoMergeBaseReason#COMPUTED_BASE}).
   *   <li>a one sided merge strategy (e.g. {@code ours} or {@code theirs}) has been used and
   *       computing a base was not required for the merge (in this case {@link #noBaseReason} is
   *       {@link NoMergeBaseReason#ONE_SIDED_MERGE_STRATEGY}).
   *   <li>the revision was not created by performing a Git merge operation (in this case {@link
   *       #noBaseReason} is {@link NoMergeBaseReason#NO_MERGE_PERFORMED}).
   *   <li>the revision has been created before Gerrit started to store the base for conflicts (in
   *       this case {@link #noBaseReason} is {@link NoMergeBaseReason#HISTORIC_DATA_WITHOUT_BASE}).
   * </ul>
   */
  public String base;

  /**
   * The SHA1 of the commit that was used as {@code ours} for the Git merge that created the
   * revision.
   *
   * <p>Guaranteed to be set if {@link #containsConflicts} is {@code true}. If {@link
   * #containsConflicts} is {@code false}, only set if the revision was created by Gerrit as a
   * result of performing a Git merge.
   */
  public String ours;

  /**
   * The SHA1 of the commit that was used as {@code theirs} for the Git merge that created the
   * revision.
   *
   * <p>Guaranteed to be set if {@link #containsConflicts} is {@code true}. If {@link
   * #containsConflicts} is {@code false}, only set if the revision was created by Gerrit as a
   * result of performing a Git merge.
   */
  public String theirs;

  /**
   * The merge strategy was used for the Git merge that created the revision.
   *
   * <p>Possible values: {@code resolve}, {@code recursive}, {@code simple-two-way-in-core}, {@code
   * ours} and {@code theirs}.
   */
  public String mergeStrategy;

  /**
   * Reason why {@link #base} is not set.
   *
   * <p>Only set if {@link #base} is not set.
   *
   * <p>Possible values are:
   *
   * <ul>
   *   <li>{@code NO_COMMON_ANCESTOR}: The merged commits do not have a common ancestor.
   *   <li>{@code COMPUTED_BASE}: The merged commits have multiple merge bases (happens for
   *       criss-cross-merges) and the base was computed.
   *   <li>{@code ONE_SIDED_MERGE_STRATEGY}: A one sided merge strategy (e.g. {@code ours} or {@code
   *       theirs}) has been used and computing a base was not required for the merge.
   *   <li>{@code NO_MERGE_PERFORMED}: The revision was not created by performing a Git merge
   *       operation.
   *   <li>{@code HISTORIC_DATA_WITHOUT_BASE}: The revision has been created before Gerrit started
   *       to store the base for conflicts.
   * </ul>
   */
  public NoMergeBaseReason noBaseReason;

  /**
   * Whether any of the files in the revision has a conflict due to merging {@link #ours} and {@link
   * #theirs}.
   *
   * <p>If {@code true} at least one of the files in the revision has a conflict and contains Git
   * conflict markers. The conflicts occurred while performing a merge between {@link #ours} and
   * {@link #theirs}.
   *
   * <p>If {@code false}, and {@link #ours} and {@link #theirs} are present, merging {@link #ours}
   * and {@link #theirs} didn't have any conflict. In this case the files in the revision may only
   * contain Git conflict markers if they were already present in {@link #ours} or {@link #theirs}.
   *
   * <p>If {@code false}, and {@link #ours} and {@link #theirs} are not present, the revision was
   * not created as a result of performing a Git merge and hence doesn't contain conflicts.
   */
  public Boolean containsConflicts;
}
