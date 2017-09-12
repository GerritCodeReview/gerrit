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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import java.util.stream.Stream;

public class ReviewerPredicate extends ChangeIndexPredicate {
  protected static Predicate<ChangeData> forState(Account.Id id, ReviewerStateInternal state) {
    checkArgument(state != ReviewerStateInternal.REMOVED, "can't query by removed reviewer");
    return new ReviewerPredicate(state, id);
  }

  protected static Predicate<ChangeData> reviewer(Arguments args, Account.Id id) {
    if (args.notesMigration.readChanges()) {
      // With NoteDb, Reviewer/CC are clearly distinct states, so only choose reviewer.
      return new ReviewerPredicate(ReviewerStateInternal.REVIEWER, id);
    }
    // Without NoteDb, Reviewer/CC are a bit unpredictable; maintain the old behavior of matching
    // any reviewer state.
    return anyReviewerState(id);
  }

  protected static Predicate<ChangeData> cc(Account.Id id) {
    // As noted above, CC is nebulous without NoteDb, but it certainly doesn't make sense to return
    // Reviewers for cc:foo. Most likely this will just not match anything, but let the index sort
    // it out.
    return new ReviewerPredicate(ReviewerStateInternal.CC, id);
  }

  protected static Predicate<ChangeData> anyReviewerState(Account.Id id) {
    return Predicate.or(
        Stream.of(ReviewerStateInternal.values())
            .filter(s -> s != ReviewerStateInternal.REMOVED)
            .map(s -> new ReviewerPredicate(s, id))
            .collect(toList()));
  }

  protected final ReviewerStateInternal state;
  protected final Account.Id id;

  private ReviewerPredicate(ReviewerStateInternal state, Account.Id id) {
    super(ChangeField.REVIEWER, ChangeField.getReviewerFieldValue(state, id));
    this.state = state;
    this.id = id;
  }

  protected Account.Id getAccountId() {
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
