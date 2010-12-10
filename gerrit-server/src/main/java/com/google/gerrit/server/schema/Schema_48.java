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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.UserConfig;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class Schema_48 extends SchemaVersion {
  private final GitRepositoryManager mgr;

  @Inject
  Schema_48(Provider<Schema_47> prior, GitRepositoryManager mgr) {
    super(prior);
    this.mgr = mgr;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    final UserConfig config = new Config().get(UserConfig.KEY);
    final PersonIdent adminUser =
        new PersonIdent(config.getCommitterName(), config.getCommitterEmail());

    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM projects ORDER BY name");
    while (rs.next()) {
      final String name = rs.getString("name");

      Repository git;
      try {
        git = mgr.openRepository(name);
      } catch (RepositoryNotFoundException notFound) {
        // A repository may be missing if this project existed only to store
        // inheritable permissions. For example '-- All Projects --'.
        try {
          git = mgr.createRepository(name);
        } catch (RepositoryNotFoundException err) {
          throw new OrmException("Cannot create repository " + name, err);
        }
      }
      try {
        ProjectConfig p = ProjectConfig.read(git);

        loadProject(rs, p.getProject());

        CommitBuilder cb = new CommitBuilder();
        cb.setAuthor(adminUser);
        cb.setCommitter(adminUser);
        cb.setMessage("Imported project configuration.\n");
        if (!p.commit(cb, git)) {
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

  private void loadProject(ResultSet rs, Project project) throws SQLException {
    project.setName(rs.getString("name"));
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
    }

    project.setUseSignedOffBy("Y".equals(rs.getString("use_signed_off_by")));
    project.setRequireChangeID("Y".equals(rs.getString("require_change_id")));
    project.setUseContentMerge("Y".equals(rs.getString("use_content_merge")));
    project.setParentName(rs.getString("parent_name"));
  }
}
