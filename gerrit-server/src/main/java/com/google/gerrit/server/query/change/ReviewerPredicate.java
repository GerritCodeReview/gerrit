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
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gwtorm.server.OrmException;

class ReviewerPredicate extends IndexPredicate<ChangeData> {

  private final Account.Id id;
  private boolean allowDrafts;

  ReviewerPredicate(Account.Id id, boolean allowDrafts) {
    super(ChangeField.REVIEWER, id.toString());
    this.id = id;
    this.allowDrafts = allowDrafts;
  }

  Account.Id getAccountId() {
    return id;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    if (!allowDrafts &&
        object.change().getStatus() == Change.Status.DRAFT) {
      return false;
    }
    for (Account.Id accountId : object.reviewers().values()) {
      if (id.equals(accountId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 2;
  }
}
