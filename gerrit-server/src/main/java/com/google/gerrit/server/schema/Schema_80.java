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


import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.PersonIdent;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class Schema_80 extends SchemaVersion {
  private final PersonIdent serverUser;

  private SystemConfig systemConfig;
  private Map<AccountGroup.Id, GroupReference> groupMap;

  @Inject
  Schema_80(Provider<Schema_79> prior, GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    systemConfig = db.systemConfig().get(new SystemConfig.Key());

    assignGroupUUIDs(db);
  }

  private void assignGroupUUIDs(ReviewDb db) throws OrmException {

    List<AccountGroup> allGroups = db.accountGroups().all().toList();
    System.out.println("attempting to migrate change owner group");
    if (!allGroups.contains(AccountGroup.CHANGE_OWNERS)) {
      systemConfig.adminGroupUUID = toUUID(systemConfig.adminGroupId);
      systemConfig.batchUsersGroupUUID = toUUID(systemConfig.batchUsersGroupId);

      AccountGroup changeOwners = newGroup(db, "Change Owners", AccountGroup.CHANGE_OWNERS);
      changeOwners.setDescription("The owners of a change");
      changeOwners.setOwnerGroupUUID(systemConfig.adminGroupUUID);
      changeOwners.setType(AccountGroup.Type.SYSTEM);
      db.accountGroups().insert(Collections.singleton(changeOwners));
      db.accountGroupNames().insert(
          Collections.singleton(new AccountGroupName(changeOwners)));

      List<AccountGroup> groups = new ArrayList<AccountGroup>();
      groups.add(changeOwners);
      db.accountGroups().update(groups);
      db.systemConfig().update(Collections.singleton(systemConfig));
    }
  }

  private AccountGroup newGroup(ReviewDb c, String name, AccountGroup.UUID uuid)
      throws OrmException {
    if (uuid == null) {
      uuid = GroupUUID.make(name, serverUser);
    }
    return new AccountGroup( //
        new AccountGroup.NameKey(name), //
        new AccountGroup.Id(c.nextAccountGroupId()), //
        uuid);
  }

  private AccountGroup.UUID toUUID(AccountGroup.Id id) {
    return groupMap.get(id).getUUID();
  }
}