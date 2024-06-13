// Copyright (C) 2013 The Android Open Source Project
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

/** Operation performed by a change relative to its parent. */
public enum ChangeKind {
  /** Nontrivial content changes. */
  REWORK,

  /** Conflict-free merge between the new parent and the prior patch set. */
  TRIVIAL_REBASE,

  /**
   * Conflict-free merge between the new parent and the prior patch set, accompanied with a change
   * to commit message.
   */
  TRIVIAL_REBASE_WITH_MESSAGE_UPDATE,

  /** Conflict-free change of first (left) parent of a merge commit. */
  MERGE_FIRST_PARENT_UPDATE,

  /** Same tree and same parent tree. */
  NO_CODE_CHANGE,

  /** Same tree, parent tree, same commit message. */
  NO_CHANGE;

  public boolean matches(ChangeKind changeKind, boolean isMerge) {
    switch (changeKind) {
      case REWORK:
        // REWORK inlcudes all other change kinds, since those are just more trivial cases of a
        // rework
        return true;
      case TRIVIAL_REBASE:
        return isTrivialRebase();
      case TRIVIAL_REBASE_WITH_MESSAGE_UPDATE:
        return isTrivialRebaseWithMessageUpdate();
      case MERGE_FIRST_PARENT_UPDATE:
        return isMergeFirstParentUpdate(isMerge);
      case NO_CHANGE:
        return this == NO_CHANGE;
      case NO_CODE_CHANGE:
        return isNoCodeChange();
    }
    throw new IllegalStateException("unexpected change kind: " + changeKind);
  }

  public boolean isNoCodeChange() {
    // NO_CHANGE is a more trivial case of NO_CODE_CHANGE and hence matched as well
    return this == NO_CHANGE || this == NO_CODE_CHANGE;
  }

  public boolean isTrivialRebase() {
    // NO_CHANGE is a more trivial case of TRIVIAL_REBASE and hence matched as well
    return this == NO_CHANGE || this == TRIVIAL_REBASE;
  }

  public boolean isTrivialRebaseWithMessageUpdate() {
    // TRIVIAL_REBASE is more strict condition and hence matched as well
    return this == NO_CHANGE
        || this == NO_CODE_CHANGE
        || this == TRIVIAL_REBASE
        || this == TRIVIAL_REBASE_WITH_MESSAGE_UPDATE;
  }

  public boolean isMergeFirstParentUpdate(boolean isMerge) {
    if (!isMerge) {
      return false;
    }

    // NO_CHANGE is a more trivial case of MERGE_FIRST_PARENT_UPDATE and hence matched as well
    return this == NO_CHANGE || this == MERGE_FIRST_PARENT_UPDATE;
  }
}
