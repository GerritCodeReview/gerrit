// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.index.change.ChangeField.REVIEWEDBY;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

class IsReviewedPredicate extends ChangeIndexPredicate {
  private static final Account.Id NOT_REVIEWED = new Account.Id(ChangeField.NOT_REVIEWED);

  static Predicate<ChangeData> create() {
    return Predicate.not(new IsReviewedPredicate(NOT_REVIEWED));
  }

  static Predicate<ChangeData> create(Collection<Account.Id> ids) {
    List<Predicate<ChangeData>> predicates = new ArrayList<>(ids.size());
    for (Account.Id id : ids) {
      predicates.add(new IsReviewedPredicate(id));
    }
    return Predicate.or(predicates);
  }

  private final Account.Id id;

  private IsReviewedPredicate(Account.Id id) {
    super(REVIEWEDBY, Integer.toString(id.get()));
    this.id = id;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    Set<Account.Id> reviewedBy = cd.reviewedBy();
    return !reviewedBy.isEmpty() ? reviewedBy.contains(id) : id == NOT_REVIEWED;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
