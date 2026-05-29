// Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.base.CaseFormat;

/** Reasons why a merge base is not available. */
public enum NoMergeBaseReason {
  /**
   * The revision has been created before Gerrit started to compute and store the base for
   * conflicts.
   */
  HISTORIC_DATA_WITHOUT_BASE(0),

  /** The merged commits do not have a common ancestor. */
  NO_COMMON_ANCESTOR(1),

  /**
   * The merged commits have multiple merge bases (happens for criss-cross-merges) and the base was
   * computed.
   */
  COMPUTED_BASE(2),

  /**
   * A one sided merge strategy (e.g. {@code ours} or {@code theirs}) has been used and computing a
   * base was not required for the merge.
   */
  ONE_SIDED_MERGE_STRATEGY(3),

  /** The revision was not created by performing a Git merge operation. */
  NO_MERGE_PERFORMED(4);

  private final int value;

  NoMergeBaseReason(int v) {
    this.value = v;
  }

  public int getValue() {
    return value;
  }

  public String getDescription() {
    return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name()).replace('-', ' ');
  }
}
