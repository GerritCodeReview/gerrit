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
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.CommentJsonMigrator;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;

/** Migrate NoteDb inline comments to JSON format. */
public class Schema_169 extends SchemaVersion {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final AllUsersName allUsers;
  private final CommentJsonMigrator migrator;
  private final GitRepositoryManager repoManager;
  //  private final NotesMigration notesMigration;
  private final SitePaths sitePaths;

  /* NOSUBMIT - how to setup injection for NotesMIgration?
     1) No implementation for com.google.gerrit.server.notedb.NotesMigration was bound.
    while locating com.google.gerrit.server.notedb.NotesMigration
      for the 6th parameter of com.google.gerrit.server.schema.Schema_169.<init>(Schema_169.java:59)
    at com.google.gerrit.server.schema.SchemaUpdater$1.configure(SchemaUpdater.java:74)
    at com.google.gerrit.server.schema.SchemaUpdater.<init>(SchemaUpdater.java:58)
    while locating com.google.gerrit.server.schema.SchemaUpdater
      for the 4th parameter of com.google.gerrit.pgm.init.BaseInit$SiteRun.<init>(BaseInit.java:372)
    while locating com.google.gerrit.pgm.init.BaseInit$SiteRun

  1 error

     */
  @Inject
  Schema_169(
      Provider<Schema_168> prior,
      AllUsersName allUsers,
      CommentJsonMigrator migrator,
      GitRepositoryManager repoManager,
      // NotesMigration notesMigration,
      SitePaths sitePaths) {
    super(prior);
    this.allUsers = allUsers;
    this.migrator = migrator;
    this.repoManager = repoManager;
    //    this.notesMigration = notesMigration;
    this.sitePaths = sitePaths;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    migrateData(sitePaths, ui);
  }

  @VisibleForTesting
  protected void migrateData(SitePaths sitePaths, UpdateUI ui) throws OrmException {
    // If there are any changes ever written to NoteDb, they might have legacy comments.
    /* is this necessary?

     if (!notesMigration.commitChangeWrites()) {
      return;
    }
    */

    boolean ok = true;
    ProgressMonitor pm = new TextProgressMonitor();
    SortedSet<Project.NameKey> projects = repoManager.list();
    pm.beginTask("Migrating projects", projects.size());
    int skipped = 0;
    for (Project.NameKey project : projects) {
      try (Repository repo = repoManager.openRepository(project);
          RevWalk rw = new RevWalk(repo);
          // TODO(dborowitz): Use newPackInserter once crbug.com/gerrit/7668 is fixed.
          ObjectInserter ins = repo.newObjectInserter()) {
        BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
        bru.setAllowNonFastForwards(true);
        ok &= migrator.migrateChanges(project, repo, rw, ins, bru);
        if (project.equals(allUsers)) {
          ok &= migrator.migrateDrafts(allUsers, repo, rw, ins, bru);
        }

        if (!bru.getCommands().isEmpty()) {
          ins.flush();
          RefUpdateUtil.executeChecked(bru, rw);
        } else {
          skipped++;
        }
      } catch (IOException e) {
        logger.atWarning().log("Error migrating project " + project, e);
        ok = false;
      }
      pm.update(1);
    }
    pm.endTask();
    ui.message(
        "Skipped " + skipped + " project" + (skipped == 1 ? "" : "s") + " with no legacy comments");

    if (!ok) {
      throw new OrmException("Migration failed");
    }

    // NOSUBMIT Do we have to set/unset noteDb.writeJson?

    if (!ok) {
      throw new OrmException("Migration failed");
    }
  }
}
