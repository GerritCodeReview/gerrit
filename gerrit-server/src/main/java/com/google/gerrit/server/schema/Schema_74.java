// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUUID;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUUIDAudit;
import com.google.gerrit.reviewdb.server.AccountGroupIncludeByUUIDAccess;
import com.google.gerrit.reviewdb.server.AccountGroupIncludeByUUIDAuditAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;

public class Schema_74 extends SchemaVersion {
  @Inject
  Schema_74(Provider<Schema_73> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(final ReviewDb db, final UpdateUI ui)
      throws SQLException, OrmException {
    // Grab all the groups since we don't have the cache available
    HashMap<AccountGroup.Id, AccountGroup.UUID> allGroups =
        new HashMap<AccountGroup.Id, AccountGroup.UUID>();
    for( AccountGroup ag : db.accountGroups().all() ) {
      allGroups.put(ag.getId(), ag.getGroupUUID());
    }

    // Copy AccountGroupInclude(Audit) to AccountGroupIncludeByUUID(Audit)
    AccountGroupIncludeByUUIDAccess destIncludeTable = db.accountGroupIncludesByUUID();
    AccountGroupIncludeByUUIDAuditAccess destAuditTable = db.accountGroupIncludesByUUIDAudit();
    ResultSet<AccountGroupIncludeAudit> rs;
    AccountGroupIncludeByUUID destIncludeEntry;
    for( AccountGroupInclude agi : db.accountGroupIncludes().iterateAllEntities() ) {
      rs = db.accountGroupIncludesAudit().byGroupInclude(agi.getGroupId(), agi.getIncludeId());
      destIncludeEntry = new AccountGroupIncludeByUUID(
          new AccountGroupIncludeByUUID.Key(agi.getGroupId(), allGroups.get(agi.getIncludeId())));
      for( AccountGroupIncludeAudit agia : rs ) {
        destAuditTable.insert(Collections.singleton(
            new AccountGroupIncludeByUUIDAudit(
                destIncludeEntry, agia.getAddedBy(), agia.getKey().getAddedOn())));
      }
      destIncludeTable.insert(Collections.singleton(destIncludeEntry));
    }
  }
}
