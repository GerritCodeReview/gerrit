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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Schema_67 extends SchemaVersion {

  @Inject
  Schema_67(Provider<Schema_66> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    ui.message("Update ownerGroupId to ownerGroupUUID");

    // Scan all AccountGroup, and find the ones that need the owner_group_id
    // migrated to owner_group_uuid.
    Map<AccountGroup.Id, AccountGroup.Id> idMap = Maps.newHashMap();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT group_id, owner_group_id FROM account_groups"
          + " WHERE owner_group_uuid is NULL or owner_group_uuid =''");
      try {
        Map<Integer, ContributorAgreement> agreements = Maps.newHashMap();
        while (rs.next()) {
          AccountGroup.Id groupId = new AccountGroup.Id(rs.getInt(1));
          AccountGroup.Id ownerId = new AccountGroup.Id(rs.getInt(2));
          idMap.put(groupId, ownerId);
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }

    // Lookup up all groups by ID.
    Set<AccountGroup.Id> all =
        Sets.newHashSet(Iterables.concat(idMap.keySet(), idMap.values()));
    Map<AccountGroup.Id, AccountGroup> groups = Maps.newHashMap();
    com.google.gwtorm.server.ResultSet<AccountGroup> rs =
        db.accountGroups().get(all);
    try {
      for (AccountGroup group : rs) {
        groups.put(group.getId(), group);
      }
    } finally {
      rs.close();
    }

    // Update the ownerGroupUUID.
    List<AccountGroup> toUpdate = Lists.newArrayListWithCapacity(idMap.size());
    for (Entry<AccountGroup.Id, AccountGroup.Id> entry : idMap.entrySet()) {
      AccountGroup group = groups.get(entry.getKey());
      AccountGroup owner = groups.get(entry.getValue());
      group.setOwnerGroupUUID(owner.getGroupUUID());
      toUpdate.add(group);
    }

    db.accountGroups().update(toUpdate);
  }
}
