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
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

public class OwnerPredicate extends ChangeIndexPredicate {
  protected final Account.Id id;

  public OwnerPredicate(Account.Id id) {
    super(ChangeField.OWNER, id.toString());
    this.id = id;
  }

  protected Account.Id getAccountId() {
    return id;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change change = object.change();
    return change != null && id.equals(change.getOwner());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
