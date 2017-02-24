// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;
import java.util.List;

public class Schema_142 extends SchemaVersion {
  @Inject
  Schema_142(Provider<Schema_141> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    List<AccountExternalId> newIds = db.accountExternalIds().all().toList();
    for (AccountExternalId id : newIds) {
      if (!id.isScheme(AccountExternalId.SCHEME_USERNAME)) {
        continue;
      }

      String password = id.getPassword();
      if (password != null) {
        HashedPassword hashed = HashedPassword.fromPassword(password);
        id.setPassword(hashed.encode());
      }
    }

    db.accountExternalIds().upsert(newIds);
  }
}
