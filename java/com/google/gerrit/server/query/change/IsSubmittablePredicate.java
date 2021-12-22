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

import com.google.gerrit.index.query.NotPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeField;

public class IsSubmittablePredicate extends BooleanPredicate {
  public IsSubmittablePredicate() {
    super(ChangeField.IS_SUBMITTABLE);
  }

  /**
   * Rewrite the is:submittable predicate.
   *
   * <p>If we run a query with "is:submittable OR -is:submittable" the result should match all
   * changes. In Lucene, we keep separate sub-indexes for open and closed changes. The Lucene
   * backend inspects the input predicate and depending on all its child predicates decides if the
   * query should run against the open sub-index, closed sub-index or both.
   *
   * <p>The "is:submittable" operator is implemented as:
   *
   * <p>issubmittable:1
   *
   * <p>But we want to exclude closed changes from being matched by this query. For the normal case,
   * we rewrite the query as:
   *
   * <p>issubmittable:1 AND status:new
   *
   * <p>Hence Lucene will match the query against the open sub-index. For the negated case (i.e.
   * "-is:submittable"), we cannot just negate the previous query because it would result in:
   *
   * <p>-(issubmittable:1 AND status:new)
   *
   * <p>Lucene will conclude that it should look for changes that are <b>not</b> new and hence will
   * run the query against the closed sub-index, not matching with changes that are open but not
   * submittable. For this case, we need to rewrite the query to match with closed changes <b>or</b>
   * changes that are not submittable.
   */
  public static Predicate<ChangeData> rewrite(Predicate<ChangeData> in) {
    if (in instanceof IsSubmittablePredicate) {
      return Predicate.and(
          new BooleanPredicate(ChangeField.IS_SUBMITTABLE), ChangeStatusPredicate.open());
    }
    if (in instanceof NotPredicate && in.getChild(0) instanceof IsSubmittablePredicate) {
      return Predicate.or(
          Predicate.not(new BooleanPredicate(ChangeField.IS_SUBMITTABLE)),
          ChangeStatusPredicate.closed());
    }
    return in;
  }
}
