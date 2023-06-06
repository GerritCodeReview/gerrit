// Copyright (C) 2023 The Android Open Source Project
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

public class DiffMetaInfo {
  public RelationType relationType;

  public enum RelationType {
    /** Both revisions are the same. */
    IDENTICAL,

    /** Both revisions have the same parent. */
    SAME_PARENT,

    /** LHS revision is the direct parent of the RHS revision. */
    LHS_PARENT_OF_RHS,

    /** The parent of LHS is an ancestor of the parent of the RHS. */
    LHS_PARENT_ANCESTOR_OF_RHS_PARENT,

    /** The parent of RHS is an ancestor of the parent of the LHS. */
    RHS_PARENT_ANCESTOR_OF_LHS_PARENT,

    /** Both LHS and RHS revisions have a common base that's reachable from both revisions. */
    COMMON_BASE,

    /** Any of the LHS or RHS is a merge commit. */
    MERGE_COMMIT,

    /** Comparison is not in any of the above types. */
    OTHER
  }
}
