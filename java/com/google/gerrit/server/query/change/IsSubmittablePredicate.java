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

  /** TODO: document why this is necessary. */
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
