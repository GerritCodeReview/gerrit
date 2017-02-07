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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

public class StarPredicate extends ChangeIndexPredicate {
  private final Account.Id accountId;
  private final String label;

  StarPredicate(Account.Id accountId, String label) {
    super(ChangeField.STAR, StarredChangesUtil.StarField.create(accountId, label).toString());
    this.accountId = accountId;
    this.label = label;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return cd.stars().get(accountId).contains(label);
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_STAR + ":" + label;
  }
}
