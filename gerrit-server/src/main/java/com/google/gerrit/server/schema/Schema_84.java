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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.SystemConfig;
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
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Schema_84 extends SchemaVersion {

  private final PersonIdent serverUser;
  private SystemConfig systemConfig;
  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjects;
  private GroupReference changeOwners;

  @Inject
  Schema_84(Provider<Schema_83> prior, AllProjectsName allProjects,
      GitRepositoryManager mgr, @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
    this.changeOwners = new GroupReference(
        AccountGroup.CHANGE_OWNERS,
        "Change Owners");
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    systemConfig = db.systemConfig().get(new SystemConfig.Key());
    assignGroupUUIDs(db);

    Repository git = null;
    try {
      git = mgr.openRepository(allProjects);
      asignChangeOwnerPermissions(git);
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    } finally {
      if (git != null) {
        git.close();
      }
    }
  }

  // Add new Change Owner group
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

  // Assign Change Owner group to gerrit permissions
  private void asignChangeOwnerPermissions(Repository git)
      throws IOException, ConfigInvalidException{

      MetaDataUpdate md = new MetaDataUpdate(
          GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Assign Change Owner to All-Projects permissions");

      ProjectConfig config = ProjectConfig.read(md);
      AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);
      grant(config, heads, Permission.ABANDON, changeOwners);
      grant(config, heads, Permission.EDIT_TOPIC_NAME, true, changeOwners);
      grant(config, heads, Permission.DELETE_DRAFTS, changeOwners);
      grant(config, heads, Permission.PUBLISH_DRAFTS, changeOwners);
      grant(config, heads, Permission.REBASE, changeOwners);
      grant(config, heads, Permission.REMOVE_REVIEWER, changeOwners);
      grant(config, heads, Permission.VIEW_DRAFTS, changeOwners);

      config.commit(md);
  }

  private void grant(ProjectConfig config, AccessSection section,
      String permission, GroupReference... groupList) {
    grant(config, section, permission, false, groupList);
  }

  private void grant(ProjectConfig config, AccessSection section,
      String permission, boolean force, GroupReference... groupList) {
    Permission p = section.getPermission(permission, true);
    for (GroupReference group : groupList) {
      if (group != null) {
        PermissionRule r = rule(config, group);
        r.setForce(force);
        p.add(r);
      }
    }
  }

  private PermissionRule rule(ProjectConfig config, GroupReference group) {
    return new PermissionRule(config.resolve(group));
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
