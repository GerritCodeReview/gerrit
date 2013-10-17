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

public class Schema_86 extends SchemaVersion {

  private final PersonIdent serverUser;
  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjects;
  private GroupReference changeOwner;

  @Inject
  Schema_86(Provider<Schema_84> prior, AllProjectsName allProjects,
      GitRepositoryManager mgr, @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
    this.changeOwner = new GroupReference(
        AccountGroup.CHANGE_OWNER,
        "Change Owner");
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {

    Repository git;
    try {
      git = mgr.openRepository(allProjects);
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    } catch (IOException e) {
        throw new OrmException(e);
    }

    try {
      assignChangeOwnerPermissions(git);
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

  // Assign Change Owner group to gerrit permissions
  private void assignChangeOwnerPermissions(Repository git)
      throws IOException, ConfigInvalidException{

      MetaDataUpdate md = new MetaDataUpdate(
          GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Configure default Change Owner permissions");

      ProjectConfig config = ProjectConfig.read(md);
      AccessSection all = config.getAccessSection(AccessSection.ALL, true);
      grant(config, all, Permission.ABANDON, changeOwner);
      grant(config, all, Permission.EDIT_TOPIC_NAME, true, changeOwner);
      grant(config, all, Permission.DELETE_DRAFTS, changeOwner);
      grant(config, all, Permission.PUBLISH_DRAFTS, changeOwner);
      grant(config, all, Permission.REBASE, changeOwner);
      grant(config, all, Permission.REMOVE_REVIEWER, changeOwner);
      grant(config, all, Permission.VIEW_DRAFTS, changeOwner);

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

}
