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

package com.google.gerrit.server.query.account;

import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

public class CanSeeChangePredicate extends PostFilterPredicate<AccountState> {
  private final Provider<ReviewDb> db;
  private final PermissionBackend permissionBackend;
  private final ChangeNotes changeNotes;

  CanSeeChangePredicate(
      Provider<ReviewDb> db, PermissionBackend permissionBackend, ChangeNotes changeNotes) {
    super(AccountQueryBuilder.FIELD_CAN_SEE, changeNotes.getChangeId().toString());
    this.db = db;
    this.permissionBackend = permissionBackend;
    this.changeNotes = changeNotes;
  }

  @Override
  public boolean match(AccountState accountState) throws OrmException {
    try {
      return permissionBackend
          .absentUser(accountState.getAccount().getId())
          .database(db)
          .change(changeNotes)
          .test(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      throw new OrmException("Failed to check if account can see change", e);
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
