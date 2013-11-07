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
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

/* Handles copying all entries from AccountGroupIncludes(Audit) to the new tables */
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
    for (AccountGroup ag : db.accountGroups().all()) {
      allGroups.put(ag.getId(), ag.getGroupUUID());
    }

    // Initialize some variables
    Connection conn = ((JdbcSchema) db).getConnection();
    ArrayList<AccountGroupById> newIncludes =
        new ArrayList<AccountGroupById>();
    ArrayList<AccountGroupByIdAud> newIncludeAudits =
        new ArrayList<AccountGroupByIdAud>();

    // Iterate over all entries in account_group_includes
    Statement oldGroupIncludesStmt = conn.createStatement();
    try {
      ResultSet oldGroupIncludes = oldGroupIncludesStmt.
          executeQuery("SELECT * FROM account_group_includes");
      while (oldGroupIncludes.next()) {
        AccountGroup.Id oldGroupId =
            new AccountGroup.Id(oldGroupIncludes.getInt("group_id"));
        AccountGroup.Id oldIncludeId =
            new AccountGroup.Id(oldGroupIncludes.getInt("include_id"));
        AccountGroup.UUID uuidFromIncludeId = allGroups.get(oldIncludeId);

        // If we've got an include, but the group no longer exists, don't bother converting
        if (uuidFromIncludeId == null) {
          ui.message("Skipping group_id = \"" + oldIncludeId.get() +
              "\", not a current group");
          continue;
        }

        // Create the new include entry
        AccountGroupById destIncludeEntry = new AccountGroupById(
            new AccountGroupById.Key(oldGroupId, uuidFromIncludeId));

        // Iterate over all the audits (for this group)
        PreparedStatement oldAuditsQueryStmt = conn.prepareStatement(
            "SELECT * FROM account_group_includes_audit WHERE group_id=? AND include_id=?");
        try {
          oldAuditsQueryStmt.setInt(1, oldGroupId.get());
          oldAuditsQueryStmt.setInt(2, oldIncludeId.get());
          ResultSet oldGroupIncludeAudits = oldAuditsQueryStmt.executeQuery();
          while (oldGroupIncludeAudits.next()) {
            Account.Id addedBy = new Account.Id(oldGroupIncludeAudits.getInt("added_by"));
            int removedBy = oldGroupIncludeAudits.getInt("removed_by");

            // Create the new audit entry
            AccountGroupByIdAud destAuditEntry =
                new AccountGroupByIdAud(destIncludeEntry, addedBy,
                    oldGroupIncludeAudits.getTimestamp("added_on"));

            // If this was a "removed on" entry, note that
            if (removedBy > 0) {
              destAuditEntry.removed(new Account.Id(removedBy),
                  oldGroupIncludeAudits.getTimestamp("removed_on"));
            }
            newIncludeAudits.add(destAuditEntry);
          }
          newIncludes.add(destIncludeEntry);
        } finally {
          oldAuditsQueryStmt.close();
        }
      }
    } finally {
      oldGroupIncludesStmt.close();
    }

    // Now insert all of the new entries to the database
    db.accountGroupById().insert(newIncludes);
    db.accountGroupByIdAud().insert(newIncludeAudits);
  }
}
