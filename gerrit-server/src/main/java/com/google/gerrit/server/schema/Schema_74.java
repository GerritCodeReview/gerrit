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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuidAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.ResultSet;
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
    AccountGroupIncludeByUuid destIncludeEntry;
    AccountGroupIncludeByUuidAudit destAuditEntry;
    Connection conn = ((JdbcSchema) db).getConnection();
    ResultSet agi = conn.createStatement().executeQuery("SELECT * FROM account_group_includes");
    ResultSet agia;
    while (agi.next()) {
      AccountGroup.Id oldId = AccountGroup.Id.parse(agi.getString("group_id"));
      AccountGroup.Id oldIncludeId = AccountGroup.Id.parse(agi.getString("include_id"));
      destIncludeEntry = new AccountGroupIncludeByUuid(
          new AccountGroupIncludeByUuid.Key(oldId, allGroups.get(oldIncludeId)));
      agia = conn.createStatement().executeQuery("SELECT * FROM account_group_includes_audit");
      while( agia.next() ) {
        Account.Id addedBy = Account.Id.parse(agia.getString("added_by"));
        String removedBy = agia.getString("removed_by");
        destAuditEntry = new AccountGroupIncludeByUuidAudit(destIncludeEntry,
            addedBy, agia.getTimestamp("added_on"));
        if (removedBy != null) {
          destAuditEntry.removed(Account.Id.parse(removedBy), agia.getTimestamp("removed_on"));
        }
        db.accountGroupIncludesByUuidAudit().insert(Collections.singleton(destAuditEntry));
      }
      db.accountGroupIncludesByUuid().insert(Collections.singleton(destIncludeEntry));
    }
  }
}
