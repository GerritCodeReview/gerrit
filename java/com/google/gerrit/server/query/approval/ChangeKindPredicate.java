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
    return ctx.changeKind().matches(changeKind, ctx.isMerge());
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
