// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.approval;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Predicate;
import java.util.Collection;
import java.util.Objects;

/**
 * Predicate that matches patch set approvals we want to copy if the diff between the old and new
 * patch set is of a certain kind.
 */
public class ChangeKindPredicate extends ApprovalPredicate {
  private final ChangeKind changeKind;

  ChangeKindPredicate(ChangeKind changeKind) {
    this.changeKind = changeKind;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    if (ctx.changeKind().equals(changeKind)) {
      // The configured change kind (changeKind) on which approvals should be copied matches the
      // actual change kind (ctx.changeKind()).
      return true;
    }

    // If the actual change kind (ctx.changeKind()) is NO_CHANGE it is also matched if the
    // configured change kind (changeKind) is:
    // * TRIVIAL_REBASE: since NO_CHANGE is a special kind of a trivial rebase
    // * NO_CODE_CHANGE: if there is no change, there is also no code change
    // * MERGE_FIRST_PARENT_UPDATE (only if the new patch set is a merge commit): if votes should be
    //   copied on first parent update, they should also be copied if there was no change
    //
    // Motivation:
    // * https://gerrit-review.googlesource.com/c/gerrit/+/74690
    // * There is no practical use case where you would want votes to be copied on
    //   TRIVIAL_REBASE|NO_CODE_CHANGE|MERGE_FIRST_PARENT_UPDATE but not on NO_CHANGE. Matching
    //   NO_CHANGE implicitly for these change kinds makes configuring copy conditions easier (as
    //   users can simply configure "changekind:<CHANGE-KIND>", rather than
    //   "changekind:<CHANGE-KIND> OR changekind:NO_CHANGE").
    // * This preserves backwards compatibility with the deprecated boolean flags for copying
    //   approvals based on the change kind ('copyAllScoresOnTrivialRebase',
    //   'copyAllScoresIfNoCodeChange' and 'copyAllScoresOnMergeFirstParentUpdate').
    return ctx.changeKind() == ChangeKind.NO_CHANGE
        && (changeKind == ChangeKind.TRIVIAL_REBASE
            || changeKind == ChangeKind.NO_CODE_CHANGE
            || (ctx.isMerge() && changeKind == ChangeKind.MERGE_FIRST_PARENT_UPDATE));
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new ChangeKindPredicate(changeKind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeKind);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ChangeKindPredicate)) {
      return false;
    }
    return ((ChangeKindPredicate) other).changeKind.equals(changeKind);
  }
}
