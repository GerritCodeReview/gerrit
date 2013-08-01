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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.PersonIdent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Schema_83 extends SchemaVersion {

  private final PersonIdent serverUser;
  private SystemConfig systemConfig;

  @Inject
  Schema_83(Provider<Schema_82> prior, @GerritPersonIdent PersonIdent serverUser) {
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
    if (!allGroups.contains(AccountGroup.CHANGE_OWNERS)) {

      for (AccountGroup g : allGroups) {
        if (g.getName().equals("Administrators")){
          systemConfig.adminGroupUUID = g.getGroupUUID();
        }
      }

      AccountGroup changeOwners = newGroup(db, "Change Owners", AccountGroup.CHANGE_OWNERS);
      changeOwners.setDescription("The owners of a change");
      changeOwners.setOwnerGroupUUID(systemConfig.adminGroupUUID);
      changeOwners.setType(AccountGroup.Type.SYSTEM);

      List<AccountGroup> groups = new ArrayList<AccountGroup>();
      groups.add(changeOwners);
      db.accountGroups().insert(groups);
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
}
