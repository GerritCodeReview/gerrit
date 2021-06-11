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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.index.change.ChangeField;

/** Label vote predicate that matches with a specific numeric label vote. */
public class EqualsNumericLabelVotePredicate extends EqualsLabelPredicate {
  protected final short expVal;

  public EqualsNumericLabelVotePredicate(
      LabelPredicate.Args args, String label, short expVal, Account.Id account) {
    super(args, label, account, ChangeField.formatLabel(label, expVal, account));
    this.expVal = expVal;
  }

  @Override
  boolean matchValue(short value) {
    return value == expVal;
  }

  @Override
  boolean override(boolean hasVote) {
    return !hasVote && expVal == 0;
  }
}
