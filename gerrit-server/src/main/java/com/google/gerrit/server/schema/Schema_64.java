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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class Schema_64 extends SchemaVersion {
  private final AllProjectsName allProjects;
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_64(Provider<Schema_63> prior,
      AllProjectsName allProjects,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    List<GroupReference> groups = Lists.newArrayList();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT group_uuid, name FROM account_groups WHERE email_only_authors = 'Y'");
      try {
        while (rs.next()) {
          AccountGroup.UUID uuid = new AccountGroup.UUID(rs.getString(1));
          GroupReference group = new GroupReference(uuid, rs.getString(2));
          groups.add(group);
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }

    if (groups.isEmpty()) {
      return;
    }
    ui.message("Moved account_groups.email_only_authors to 'Email Reviewers' capability");

    Repository git;
    try {
      git = mgr.openRepository(allProjects);
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    }
    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      AccessSection section =
          config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
      Permission capability =
          section.getPermission(GlobalCapability.EMAIL_REVIEWERS, true);
      for (GroupReference group : groups) {
        capability.getRule(config.resolve(group), true).setDeny();
      }

      md.setMessage("Upgrade to Gerrit Code Review schema 64\n");
      if (!config.commit(md)) {
        throw new OrmException("Cannot update " + allProjects);
      }
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    } finally {
      git.close();
    }
  }
}
