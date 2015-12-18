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
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gwtorm.server.OrmException;

class HasDraftByPredicate extends IndexPredicate<ChangeData> {
  private final Account.Id accountId;

  HasDraftByPredicate(Account.Id accountId) {
    super(ChangeField.DRAFTBY, accountId.toString());
    this.accountId = accountId;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return cd.editsByUser().contains(accountId);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
