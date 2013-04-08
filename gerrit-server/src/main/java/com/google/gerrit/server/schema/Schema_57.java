// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;

public class Schema_57 extends SchemaVersion {
  private final SitePaths site;
  private final LocalDiskRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_57(Provider<Schema_56> prior, SitePaths site,
      LocalDiskRepositoryManager mgr, @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.site = site;
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    SystemConfig sc = db.systemConfig().get(new SystemConfig.Key());
    Project.NameKey allProjects = sc.wildProjectName;

    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config, FS.DETECTED);
    boolean cfgDirty = false;
    try {
      cfg.load();
    } catch (ConfigInvalidException err) {
      throw new OrmException("Cannot read " + site.gerrit_config, err);
    } catch (IOException err) {
      throw new OrmException("Cannot read " + site.gerrit_config, err);
    }

    if (!allProjects.get().equals(AllProjectsNameProvider.DEFAULT)) {
      ui.message("Setting gerrit.allProjects = " + allProjects.get());
      cfg.setString("gerrit", null, "allProjects", allProjects.get());
      cfgDirty = true;
    }

    try {
      Repository git = mgr.openRepository(allProjects);
      try {
        MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);

        ProjectConfig config = ProjectConfig.read(md);
        AccessSection cap = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);

        // Move the Administrators group reference to All-Projects.
        cap.getPermission(GlobalCapability.ADMINISTRATE_SERVER, true)
            .add(new PermissionRule(config.resolve(db.accountGroups().get(sc.adminGroupId))));

        // Move the repository.*.createGroup to Create Project.
        String[] createGroupList = cfg.getStringList("repository", "*", "createGroup");

        // Prepare the account_group_includes query
        PreparedStatement stmt = ((JdbcSchema) db).getConnection().
            prepareStatement("SELECT * FROM account_group_includes WHERE group_id = ?");

        for (String name : createGroupList) {
          AccountGroup.NameKey key = new AccountGroup.NameKey(name);
          AccountGroupName groupName = db.accountGroupNames().get(key);
          if (groupName == null) {
            continue;
          }

          AccountGroup group = db.accountGroups().get(groupName.getId());
          if (group == null) {
            continue;
          }

          cap.getPermission(GlobalCapability.CREATE_PROJECT, true)
              .add(new PermissionRule(config.resolve(group)));
        }
        if (createGroupList.length != 0) {
          ui.message("Moved repository.*.createGroup to 'Create Project' capability");
          cfg.unset("repository", "*", "createGroup");
          cfgDirty = true;
        }

        AccountGroup batch = db.accountGroups().get(sc.batchUsersGroupId);
        stmt.setInt(1, sc.batchUsersGroupId.get());
        if (batch != null
            && db.accountGroupMembers().byGroup(sc.batchUsersGroupId).toList().isEmpty()
            &&  stmt.executeQuery().first() != false) {
          // If the batch user group is not used, delete it.
          //
          db.accountGroups().delete(Collections.singleton(batch));

          AccountGroupName name = db.accountGroupNames().get(batch.getNameKey());
          if (name != null) {
            db.accountGroupNames().delete(Collections.singleton(name));
          }
        } else if (batch != null) {
          cap.getPermission(GlobalCapability.PRIORITY, true)
              .getRule(config.resolve(batch), true)
              .setAction(Action.BATCH);
        }

        md.setMessage("Upgrade to Gerrit Code Review schema 57\n");
        config.commit(md);
      } catch (SQLException err) {
        throw new OrmException( "Cannot read account_group_includes", err);
      } finally {
        git.close();
      }
    } catch (ConfigInvalidException err) {
      throw new OrmException("Cannot read " + allProjects, err);
    } catch (IOException err) {
      throw new OrmException("Cannot update " + allProjects, err);
    }

    if (cfgDirty) {
      try {
        cfg.save();
      } catch (IOException err) {
        throw new OrmException("Cannot update " + site.gerrit_config, err);
      }
    }

    // We cannot set the columns to NULL, so use 0 and a DELETED tag.
    sc.adminGroupId = new AccountGroup.Id(0);
    sc.adminGroupUUID = new AccountGroup.UUID("DELETED");
    sc.anonymousGroupId = new AccountGroup.Id(0);
    sc.registeredGroupId = new AccountGroup.Id(0);
    sc.wildProjectName = new Project.NameKey("DELETED");
    sc.ownerGroupId = new AccountGroup.Id(0);
    sc.batchUsersGroupId = new AccountGroup.Id(0);
    sc.batchUsersGroupUUID = new AccountGroup.UUID("DELETED");
    sc.registerEmailPrivateKey = "DELETED";

    db.systemConfig().update(Collections.singleton(sc));
  }
}
