// Copyright (C) 2022 The Android Open Source Project
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
import java.util.Collection;

/** Predicate that matches patch set approvals that have a given voting value. */
public class ExactValuePredicate extends ApprovalPredicate {
  private final short votingValue;

  public ExactValuePredicate(short votingValue) {
    this.votingValue = votingValue;
  }

  @Override
  public boolean match(ApprovalContext approvalContext) {
    return votingValue == approvalContext.patchSetApprovalValue();
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new ExactValuePredicate(votingValue);
  }

  @Override
  public int hashCode() {
    return Short.valueOf(votingValue).hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof ExactValuePredicate)
        && votingValue == ((ExactValuePredicate) other).votingValue;
  }
}
