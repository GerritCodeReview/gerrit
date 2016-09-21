// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

class AssigneePredicate extends ChangeIndexPredicate {
  private final Account.Id id;

  AssigneePredicate(Account.Id id) {
    super(ChangeField.ASSIGNEE, id.toString());
    this.id = id;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    if (id.get() == ChangeField.NO_ASSIGNEE) {
      Optional<Account.Id> assignee = object.notes().load().getAssignee();
      return assignee == null || !assignee.isPresent();
    }
    return id.equals(object.notes().load().getAssignee().get());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
