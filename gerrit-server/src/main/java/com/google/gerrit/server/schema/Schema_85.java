// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;

public class Schema_85 extends SchemaVersion {

  @Inject
  Schema_85(Provider<Schema_84> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    List<AccountGroup> all = db.accountGroups().all().toList();
    if (all.contains(AccountGroup.CHANGE_OWNER)) {
      return;
    }

    AccountGroup changeOwners =
        newGroup(db, "Change Owner", AccountGroup.CHANGE_OWNER);
    changeOwners.setDescription("The owner of a change");
    changeOwners.setOwnerGroupUUID(adminGroup(all));
    changeOwners.setType(AccountGroup.Type.SYSTEM);
    db.accountGroups().insert(
        Collections.<AccountGroup> singletonList(changeOwners));
  }

  private static AccountGroup.UUID adminGroup(List<AccountGroup> all) {
    for (AccountGroup g : all) {
      if (g.getName().equals("Administrators")) {
        return g.getGroupUUID();
      }
    }
    return null;
  }

  private static AccountGroup newGroup(ReviewDb c, String n,
      AccountGroup.UUID uuid) throws OrmException {
    return new AccountGroup(
        new AccountGroup.NameKey(n),
        new AccountGroup.Id(c.nextAccountGroupId()),
        uuid);
  }
}
