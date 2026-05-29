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

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Objects;

/** Predicate that matches patch set approvals we want to copy based on the value. */
public class MagicValuePredicate extends ApprovalPredicate {
  enum MagicValue {
    MIN,
    MAX,
    ANY,
    POSITIVE,
    NEGATIVE
  }

  public interface Factory {
    MagicValuePredicate create(MagicValue value);
  }

  private final MagicValue value;
  private final ProjectCache projectCache;

  @Inject
  MagicValuePredicate(ProjectCache projectCache, @Assisted MagicValue value) {
    this.projectCache = projectCache;
    this.value = value;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    return switch (value) {
      case ANY -> true;
      case MIN -> ctx.approvalValue() == ctx.labelType().getMaxNegative();
      case MAX -> ctx.approvalValue() == ctx.labelType().getMaxPositive();
      case POSITIVE -> ctx.approvalValue() > 0;
      case NEGATIVE -> ctx.approvalValue() < 0;
    };
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new MagicValuePredicate(projectCache, value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MagicValuePredicate)) {
      return false;
    }
    return ((MagicValuePredicate) other).value.equals(value);
  }
}
