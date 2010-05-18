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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

public class Schema_33 extends SchemaVersion {
  @Inject
  Schema_33(Provider<Schema_32> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    SystemConfig config = db.systemConfig().all().toList().get(0);
    final AccountGroup batchUsers =
      new AccountGroup(new AccountGroup.NameKey("Non-Interactive Users"),
          new AccountGroup.Id(db.nextAccountGroupId()));
    batchUsers.setDescription("Users who perform batch actions on Gerrit");
    batchUsers.setOwnerGroupId(config.adminGroupId);
    batchUsers.setType(AccountGroup.Type.INTERNAL);
    db.accountGroups().insert(Collections.singleton(batchUsers));
    db.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(batchUsers)));

    config.batchUsersGroupId = batchUsers.getId();
    db.systemConfig().update(Collections.singleton(config));
  }
}
