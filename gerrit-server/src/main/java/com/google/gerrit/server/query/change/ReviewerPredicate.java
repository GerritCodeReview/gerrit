// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import java.util.ArrayList;
import java.util.List;

class ReviewerPredicate extends ChangeIndexPredicate {
  static Predicate<ChangeData> create(Arguments args, Account.Id id) {
    List<Predicate<ChangeData>> and = new ArrayList<>(2);
    ReviewerStateInternal[] states = ReviewerStateInternal.values();
    List<Predicate<ChangeData>> or = new ArrayList<>(states.length - 1);
    for (ReviewerStateInternal state : states) {
      if (state != ReviewerStateInternal.REMOVED) {
        or.add(new ReviewerPredicate(state, id));
      }
    }
    and.add(Predicate.or(or));

    // TODO(dborowitz): This really belongs much higher up e.g. QueryProcessor.
    if (!args.allowsDrafts) {
      and.add(Predicate.not(new ChangeStatusPredicate(Change.Status.DRAFT)));
    }
    return Predicate.and(and);
  }

  private final ReviewerStateInternal state;
  private final Account.Id id;

  ReviewerPredicate(ReviewerStateInternal state, Account.Id id) {
    super(ChangeField.REVIEWER, ChangeField.getReviewerFieldValue(state, id));
    this.state = state;
    this.id = id;
  }

  Account.Id getAccountId() {
    return id;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return cd.reviewers().asTable().get(state, id) != null;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
