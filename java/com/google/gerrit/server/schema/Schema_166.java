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

import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.CommentJsonMigrator;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Migrate NoteDb inline comments to JSON format. */
public class Schema_166 extends SchemaVersion {
  private static final Logger log = LoggerFactory.getLogger(Schema_166.class);

  private final AllUsersName allUsers;
  private final ChangeNoteUtil noteUtil;
  private final CommentJsonMigrator migrator;
  private final GitRepositoryManager repoManager;
  private final NotesMigration notesMigration;
  private final SitePaths sitePaths;

  @Inject
  Schema_166(
      Provider<Schema_165> prior,
      AllUsersName allUsers,
      ChangeNoteUtil noteUtil,
      CommentJsonMigrator migrator,
      GitRepositoryManager repoManager,
      NotesMigration notesMigration,
      SitePaths sitePaths) {
    super(prior);
    this.allUsers = allUsers;
    this.noteUtil = noteUtil;
    this.migrator = migrator;
    this.repoManager = repoManager;
    this.notesMigration = notesMigration;
    this.sitePaths = sitePaths;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    migrateData(sitePaths, ui);
  }

  @VisibleForTesting
  protected void migrateData(SitePaths sitePaths, UpdateUI ui) throws OrmException {
    // If there are any changes ever written to NoteDb, they might have legacy comments.
    if (!notesMigration.commitChangeWrites()) {
      return;
    }

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
        log.warn("Error migrating project " + project, e);
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

    if (!noteUtil.getWriteJson()) {
      ui.message("noteDb.writeJson is explicitly set to true; unsetting");
      // noteDb.writeJson defaults to true, so this means it is explicitly set to false in the site
      // config. Unset any values in both notedb.config and gerrit.config.
      ok &= unsetWriteJson(sitePaths.gerrit_config);
      ok &= unsetWriteJson(sitePaths.notedb_config);
    }

    if (!ok) {
      throw new OrmException("Migration failed");
    }
  }

  private static boolean unsetWriteJson(Path configPath) {
    try {
      if (!Files.isRegularFile(configPath)) {
        return true;
      }
      FileBasedConfig cfg = new FileBasedConfig(configPath.toFile(), FS.detect());
      cfg.load();
      cfg.unset(SECTION_NOTE_DB, null, "writeJson");
      cfg.save();
      return true;
    } catch (ConfigInvalidException | IOException e) {
      log.warn("Error saving config option in " + configPath, e);
      return false;
    }
  }
}
