// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;

public class Schema_120 extends SchemaVersion {

  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_120(
      Provider<Schema_119> prior,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  private void allowSubmoduleSubscription(Branch.NameKey subbranch, Branch.NameKey superBranch)
      throws OrmException {
    try (Repository git = mgr.openRepository(subbranch.getParentKey());
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      try (MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, subbranch.getParentKey(), git, bru)) {
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);
        md.setMessage("Added superproject subscription during upgrade");
        ProjectConfig pc = ProjectConfig.read(md);

        SubscribeSection s = null;
        for (SubscribeSection s1 : pc.getSubscribeSections(subbranch)) {
          if (s1.getProject().equals(superBranch.getParentKey())) {
            s = s1;
          }
        }
        if (s == null) {
          s = new SubscribeSection(superBranch.getParentKey());
          pc.addSubscribeSection(s);
        }
        RefSpec newRefSpec = new RefSpec(subbranch.get() + ":" + superBranch.get());

        if (!s.getMatchingRefSpecs().contains(newRefSpec)) {
          // For the migration we use only exact RefSpecs, we're not trying to
          // generalize it.
          s.addMatchingRefSpec(newRefSpec);
        }

        pc.commit(md);
      }
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (ConfigInvalidException | IOException e) {
      throw new OrmException(e);
    }
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    ui.message("Generating Superproject subscriptions table to submodule ACLs");

    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT "
                    + "super_project_project_name, "
                    + "super_project_branch_name, "
                    + "submodule_project_name, "
                    + "submodule_branch_name "
                    + "FROM submodule_subscriptions")) {
      while (rs.next()) {
        Project.NameKey superproject = new Project.NameKey(rs.getString(1));
        Branch.NameKey superbranch = new Branch.NameKey(superproject, rs.getString(2));

        Project.NameKey submodule = new Project.NameKey(rs.getString(3));
        Branch.NameKey subbranch = new Branch.NameKey(submodule, rs.getString(4));

        allowSubmoduleSubscription(subbranch, superbranch);
      }
    }
  }
}
