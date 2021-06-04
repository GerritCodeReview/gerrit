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
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Objects;

/**
 * Predicate that matches patch set approvals we want to copy if the diff between the old and new
 * patch set is of a certain kind.
 */
public class ChangeKindPredicate extends ApprovalPredicate {
  public interface Factory {
    ChangeKindPredicate create(ChangeKind changeKind);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeKind changeKind;

  @Inject
  ChangeKindPredicate(
      ChangeData.Factory changeDataFactory,
      ChangeKindCache changeKindCache,
      @Assisted ChangeKind changeKind) {
    this.changeKind = changeKind;
    this.changeKindCache = changeKindCache;
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    ChangeData cd = changeDataFactory.create(ctx.project(), ctx.target().changeId());
    ChangeKind actualChangeKind =
        changeKindCache.getChangeKind(null, null, cd, cd.patchSet(ctx.target()));
    return actualChangeKind.equals(changeKind);
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new ChangeKindPredicate(changeDataFactory, changeKindCache, changeKind);
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
