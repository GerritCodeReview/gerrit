// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_162 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_162(
      Provider<Schema_161> prior,
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try (Repository git = repoManager.openRepository(allUsersName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      ProjectConfig cfg = ProjectConfig.read(md);
      if (allProjectsName.equals(cfg.getProject().getParent(allProjectsName))) {
        return;
      }
      cfg.getProject().setParentName(allProjectsName);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(
          String.format("Make %s inherit from %s", allUsersName.get(), allProjectsName.get()));
      cfg.commit(md);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }
}
