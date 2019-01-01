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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.CommentJsonMigrator;
import com.google.gerrit.server.notedb.CommentJsonMigrator.ProjectMigrationResult;
import com.google.gerrit.server.notedb.MutableNotesMigration;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Migrate NoteDb inline comments to JSON format. */
public class Schema_169 extends SchemaVersion {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final CommentJsonMigrator migrator;
  private final GitRepositoryManager repoManager;
  private final NotesMigration notesMigration;

  @Inject
  Schema_169(
      Provider<Schema_168> prior,
      CommentJsonMigrator migrator,
      GitRepositoryManager repoManager,
      @GerritServerConfig Config config) {
    super(prior);
    this.migrator = migrator;
    this.repoManager = repoManager;
    this.notesMigration = MutableNotesMigration.fromConfig(config);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    migrateData(ui);
  }

  @VisibleForTesting
  protected void migrateData(UpdateUI ui) throws OrmException {
    //  If the migration hasn't started, no need to look for non-JSON
    if (!notesMigration.commitChangeWrites()) {
      return;
    }

    boolean ok = true;
    ProgressMonitor pm = new TextProgressMonitor();
    SortedSet<Project.NameKey> projects = repoManager.list();
    pm.beginTask("Migrating projects", projects.size());
    int skipped = 0;
    for (Project.NameKey project : projects) {
      try (Repository repo = repoManager.openRepository(project)) {
        ProjectMigrationResult progress = migrator.migrateProject(project, repo, false);
        skipped += progress.skipped;
      } catch (IOException | LargeObjectException e) {
        ok = false;
        ui.message("Error migrating project " + project);
        e.printStackTrace();
        logger.atWarning().log("Error migrating project " + project, e);
      }
      pm.update(1);
    }

    pm.endTask();
    ui.message(
        "Skipped " + skipped + " project" + (skipped == 1 ? "" : "s") + " with no legacy comments");

    if (!ok) {
      throw new OrmException("Migration failed");
    }
  }
}
