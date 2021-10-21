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

import static java.util.stream.Collectors.joining;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.SortedSet;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_130 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Remove force option from 'Push Annotated Tag' permission\n"
          + "\n"
          + "The force option on 'Push Annotated Tag' had no effect and is no longer\n"
          + "supported.";

  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;
  private final Config cfg;

  @Inject
  Schema_130(
      Provider<Schema_129> prior,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverUser,
      @GerritServerConfig Config cfg) {
    super(prior);
    this.repoManager = repoManager;
    this.serverUser = serverUser;
    this.cfg = cfg;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    SortedSet<Project.NameKey> repoList = getSortedProjectsFromCache(repoManager);
    ui.message("\tMigrating " + repoList.size() + " repositories ...");
    Collection<Project.NameKey> repoUpgraded =
        (Collection<Project.NameKey>)
            runParallelTasks(
                createExecutor(ui),
                repoList,
                (repo) -> removePushTagForcePerms((Project.NameKey) repo),
                ui);
    ui.message("\tMigration completed:  " + repoUpgraded.size() + " repositories updated:");
    ui.message("\t" + repoUpgraded.stream().map(Project.NameKey::get).collect(joining(" ")));
  }

  @Override
  protected int getThreads() {
    return cfg.getInt("cache", "projects", "loadThreads", super.getThreads());
  }

  private Project.NameKey removePushTagForcePerms(Project.NameKey project) throws OrmException {
    try (Repository git = repoManager.openRepository(project);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, git)) {
      ProjectConfigSchemaUpdate cfg = ProjectConfigSchemaUpdate.read(md);
      cfg.removeForceFromPermission("pushTag");
      cfg.save(serverUser, COMMIT_MSG);
      return cfg.isUpdated() ? project : null;
    } catch (Exception ex) {
      throw new OrmException("Cannot migrate project " + project, ex);
    }
  }
}
