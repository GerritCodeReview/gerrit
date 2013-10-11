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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Schema_85 extends SchemaVersion {

  private final AllProjectsName allProjects;
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_85(Provider<Schema_84> prior,
      AllProjectsName allProjects,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(
    ReviewDb db,
    UpdateUI ui) throws OrmException {
    Repository git;
    try {
      git = mgr.openRepository(allProjects);
    } catch (IOException e) {
      throw new OrmException(e);
    }

    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
      ProjectConfig config = ProjectConfig.read(md);

      // Create the CHANGE OWNER group.
      List<AccountGroup.UUID> adminGroupUUIDs =
          getAdministrateServerGroups(db, config);
      if (!adminGroupUUIDs.isEmpty()) {
        createGroup(db, "Change Owner", adminGroupUUIDs.get(0),
            "The owner of a change");
      }
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    } finally {
      git.close();
    }
  }

  private AccountGroup createGroup(ReviewDb db, String groupName,
      AccountGroup.UUID adminGroupUUID, String description)
          throws OrmException {
    final AccountGroup.Id groupId =
        new AccountGroup.Id(db.nextAccountGroupId());
    final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    final AccountGroup.UUID uuid = GroupUUID.make(groupName, serverUser);
    final AccountGroup group = new AccountGroup(nameKey, groupId, uuid);
    group.setOwnerGroupUUID(adminGroupUUID);
    group.setDescription(description);
    final AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    db.accountGroupNames().insert(Collections.singleton(gn));
    db.accountGroups().insert(Collections.singleton(group));
    return group;
  }

  private List<AccountGroup.UUID> getAdministrateServerGroups(
      ReviewDb db, ProjectConfig cfg) {
    List<PermissionRule> rules = cfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
       .getPermission(GlobalCapability.ADMINISTRATE_SERVER)
       .getRules();

    List<AccountGroup.UUID> groups =
        Lists.newArrayListWithExpectedSize(rules.size());
    for (PermissionRule rule : rules) {
      if (rule.getAction() == Action.ALLOW) {
        groups.add(rule.getGroup().getUUID());
      }
    }
    if (groups.isEmpty()) {
      throw new IllegalStateException("no administrator group found");
    }

    return groups;
  }
}
