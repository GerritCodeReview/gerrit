// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ReviewerStateInternal;

class ReviewerByEmailPredicate extends ChangeIndexPredicate {

  static Predicate<ChangeData> forState(Address adr, ReviewerStateInternal state) {
    checkArgument(state != ReviewerStateInternal.REMOVED, "can't query by removed reviewer");
    return new ReviewerByEmailPredicate(state, adr);
  }

  private final ReviewerStateInternal state;
  private final Address adr;

  private ReviewerByEmailPredicate(ReviewerStateInternal state, Address adr) {
    super(ChangeField.REVIEWER_BY_EMAIL, ChangeField.getReviewerByEmailFieldValue(state, adr));
    this.state = state;
    this.adr = adr;
  }

  Address getAddress() {
    return adr;
  }

  @Override
  public boolean match(ChangeData cd) {
    return cd.reviewersByEmail().asTable().get(state, adr) != null;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
