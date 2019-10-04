// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ReviewerStateInternal;

public class ReviewerinPredicate extends PostFilterPredicate<ChangeData> {
  protected final IdentifiedUser.GenericFactory userFactory;
  protected final AccountGroup.UUID uuid;

  public ReviewerinPredicate(IdentifiedUser.GenericFactory userFactory, AccountGroup.UUID uuid) {
    super(ChangeQueryBuilder.FIELD_REVIEWERIN, uuid.get());
    this.userFactory = userFactory;
    this.uuid = uuid;
  }

  protected AccountGroup.UUID getAccountGroupUUID() {
    return uuid;
  }

  @Override
  public boolean match(ChangeData object) {
    for (Account.Id accountId : object.reviewers().byState(ReviewerStateInternal.REVIEWER)) {
      IdentifiedUser reviewer = userFactory.create(accountId);
      if (reviewer.getEffectiveGroups().contains(uuid)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 3;
  }
}
