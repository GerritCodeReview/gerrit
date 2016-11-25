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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Schema_130 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Remove force option from 'Push Annotated Tag' permission\n"
      + "\n"
      + "The force option on 'Push Annotated Tag' had no effect and is no longer\n"
      + "supported.";

  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;
  private final boolean skip;

  @Inject
  Schema_130(Provider<Schema_129> prior,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverUser,
      @SkipOptionalMigrations Boolean skip) {
    super(prior);
    this.repoManager = repoManager;
    this.serverUser = serverUser;
    this.skip = skip;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    if(skip) {
      ui.message("\tSkipped: migration will happen on-line at first use");
      return;
    }

    SortedSet<Project.NameKey> repoList = repoManager.list();
    SortedSet<Project.NameKey> repoUpgraded = new TreeSet<>();
    ui.message("\tMigrating " + repoList.size() + " repositories ...");
    for (Project.NameKey projectName : repoList) {
      try (Repository git = repoManager.openRepository(projectName);
          MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
              projectName, git)) {
        ProjectConfigSchemaUpdate cfg = ProjectConfigSchemaUpdate.read(md);
        cfg.removeForceFromPermission("pushTag");
        if (cfg.isUpdated()) {
          repoUpgraded.add(projectName);
        }
        cfg.save(serverUser, COMMIT_MSG);
      } catch (ConfigInvalidException | IOException ex) {
        throw new OrmException("Cannot migrate project " + projectName, ex);
      }
    }
    ui.message("\tMigration completed:  " + repoUpgraded.size()
        + " repositories updated:");
    ui.message("\t"
        + repoUpgraded.stream().map(n -> n.get())
            .collect(Collectors.joining(" ")));
  }
}
