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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;

public class ReviewerinPredicate extends ChangeOperatorPredicate {
  protected final IdentifiedUser.GenericFactory userFactory;
  protected final AccountGroup.UUID uuid;

  public ReviewerinPredicate(IdentifiedUser.GenericFactory userFactory, AccountGroup.UUID uuid) {
    super(ChangeQueryBuilder.FIELD_REVIEWERIN, uuid.toString());
    this.userFactory = userFactory;
    this.uuid = uuid;
  }

  protected AccountGroup.UUID getAccountGroupUUID() {
    return uuid;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    for (Account.Id accountId : object.reviewers().all()) {
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
