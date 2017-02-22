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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import java.util.stream.Stream;

class ReviewerPredicate extends ChangeIndexPredicate {
  static Predicate<ChangeData> reviewer(Arguments args, Account.Id id) {
    Predicate<ChangeData> p;
    if (args.notesMigration.readChanges()) {
      // With NoteDb, Reviewer/CC are clearly distinct states, so only choose reviewer.
      p = new ReviewerPredicate(ReviewerStateInternal.REVIEWER, id);
    } else {
      // Without NoteDb, Reviewer/CC are a bit unpredictable; maintain the old behavior of matching
      // any reviewer state.
      p = anyReviewerState(id);
    }
    return create(args, p);
  }

  static Predicate<ChangeData> cc(Arguments args, Account.Id id) {
    // As noted above, CC is nebulous without NoteDb, but it certainly doesn't make sense to return
    // Reviewers for cc:foo. Most likely this will just not match anything, but let the index sort
    // it out.
    return create(args, new ReviewerPredicate(ReviewerStateInternal.CC, id));
  }

  private static Predicate<ChangeData> anyReviewerState(Account.Id id) {
    return Predicate.or(
        Stream.of(ReviewerStateInternal.values())
            .filter(s -> s != ReviewerStateInternal.REMOVED)
            .map(s -> new ReviewerPredicate(s, id))
            .collect(toList()));
  }

  private static Predicate<ChangeData> create(Arguments args, Predicate<ChangeData> p) {
    if (!args.allowsDrafts) {
      // TODO(dborowitz): This really belongs much higher up e.g. QueryProcessor. Also, why are we
      // even doing this?
      return Predicate.and(p, Predicate.not(new ChangeStatusPredicate(Change.Status.DRAFT)));
    }
    return p;
  }

  private final ReviewerStateInternal state;
  private final Account.Id id;

  private ReviewerPredicate(ReviewerStateInternal state, Account.Id id) {
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
