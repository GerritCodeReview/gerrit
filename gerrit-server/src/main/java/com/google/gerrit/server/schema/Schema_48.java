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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Schema_48 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  private SystemConfig systemConfig;
  private Map<AccountGroup.Id, AccountGroup> groupMap;

  @Inject
  Schema_48(Provider<Schema_47> prior, GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    systemConfig = db.systemConfig().get(new SystemConfig.Key());
    assignGroupUUIDs(db);
    exportProjectConfig(db);
  }

  private void assignGroupUUIDs(ReviewDb db) throws OrmException {
    groupMap = new HashMap<AccountGroup.Id, AccountGroup>();
    List<AccountGroup> groups = db.accountGroups().all().toList();
    for (AccountGroup g : groups) {
      if (g.getId().equals(systemConfig.ownerGroupId)) {
        g.setGroupUUID(AccountGroup.PROJECT_OWNERS);

      } else if (g.getId().equals(systemConfig.anonymousGroupId)) {
        g.setGroupUUID(AccountGroup.ANONYMOUS_USERS);

      } else if (g.getId().equals(systemConfig.registeredGroupId)) {
        g.setGroupUUID(AccountGroup.REGISTERED_USERS);

      } else {
        g.setGroupUUID(GroupUUID.make(g.getName(), serverUser));
      }
      groupMap.put(g.getId(), g);
    }
    db.accountGroups().update(groups);

    systemConfig.adminGroupUUID = toUUID(systemConfig.adminGroupId);
    systemConfig.batchUsersGroupUUID = toUUID(systemConfig.batchUsersGroupId);
    db.systemConfig().update(Collections.singleton(systemConfig));
  }

  private AccountGroup.UUID toUUID(AccountGroup.Id id) {
    return groupMap.get(id).getGroupUUID();
  }

  private void exportProjectConfig(ReviewDb db) throws OrmException,
      SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM projects ORDER BY name");
    while (rs.next()) {
      final String name = rs.getString("name");
      final Project.NameKey nameKey = new Project.NameKey(name);

      Repository git;
      try {
        git = mgr.openRepository(nameKey);
      } catch (RepositoryNotFoundException notFound) {
        // A repository may be missing if this project existed only to store
        // inheritable permissions. For example '-- All Projects --'.
        try {
          git = mgr.createRepository(nameKey);
        } catch (RepositoryNotFoundException err) {
          throw new OrmException("Cannot create repository " + name, err);
        }
      }
      try {
        MetaDataUpdate md =
            new MetaDataUpdate(new NoReplication(), nameKey, git);
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);

        ProjectConfig config = ProjectConfig.read(md);
        loadProject(rs, config.getProject());

        md.setMessage("Import project configuration from SQL\n");
        if (!config.commit(md)) {
          throw new OrmException("Cannot export project " + name);
        }
      } catch (ConfigInvalidException err) {
        throw new OrmException("Cannot read project " + name, err);
      } catch (IOException err) {
        throw new OrmException("Cannot export project " + name, err);
      } finally {
        git.close();
      }
    }
    rs.close();
    stmt.close();
  }

  private void loadProject(ResultSet rs, Project project) throws SQLException,
      OrmException {
    project.setDescription(rs.getString("description"));
    project.setUseContributorAgreements("Y".equals(rs
        .getString("use_contributor_agreements")));

    switch (rs.getString("submit_type").charAt(0)) {
      case 'F':
        project.setSubmitType(Project.SubmitType.FAST_FORWARD_ONLY);
        break;
      case 'M':
        project.setSubmitType(Project.SubmitType.MERGE_IF_NECESSARY);
        break;
      case 'A':
        project.setSubmitType(Project.SubmitType.MERGE_ALWAYS);
        break;
      case 'C':
        project.setSubmitType(Project.SubmitType.CHERRY_PICK);
        break;
      default:
        throw new OrmException("Unsupported submit_type="
            + rs.getString("submit_type") + " on project " + project.getName());
    }

    project.setUseSignedOffBy("Y".equals(rs.getString("use_signed_off_by")));
    project.setRequireChangeID("Y".equals(rs.getString("require_change_id")));
    project.setUseContentMerge("Y".equals(rs.getString("use_content_merge")));
    project.setParentName(rs.getString("parent_name"));
  }
}
