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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.CommentJsonMigrator;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class MigrateCommentsToJson extends SiteProgram {
  @Inject private AllUsersName allUsers;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject private CommentJsonMigrator migrator;
  @Inject private GitRepositoryManager repoManager;
  @Inject private NotesMigration notesMigration;
  @Inject private SitePaths sitePaths;

  @Override
  public int run() throws Exception {
    if (!notesMigration.readChanges()) {
      System.out.println("Site does not use NoteDb for changes; nothing to do");
      return 0;
    }

    for (Project.NameKey project : repoManager.list()) {
      try (Repository repo = repoManager.openRepository(project);
          RevWalk rw = new RevWalk(repo);
          // TODO(dborowitz): Use newPackInserter once crbug.com/gerrit/7668 is fixed.
          ObjectInserter ins = repo.newObjectInserter()) {
        BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
        migrator.migrateChanges(repo, rw, ins, bru);
        if (project.equals(allUsers)) {
          migrator.migrateDrafts(repo, rw, ins, bru);
        }

        ins.flush();
        RefUpdateUtil.executeChecked(bru, rw);
      }
    }

    if (!noteUtil.getWriteJson()) {
      // noteDb.writeJson defaults to true, so this means it is explicitly set to false in the site
      // config. Unset any values in both notedb.config and gerrit.config.
      setWriteJsonFalse(sitePaths.notedb_config);
      setWriteJsonFalse(sitePaths.gerrit_config);
    }

    return 0;
  }

  private static void setWriteJsonFalse(Path configPath)
      throws ConfigInvalidException, IOException {
    FileBasedConfig cfg = new FileBasedConfig(configPath.toFile(), FS.detect());
    cfg.load();
    cfg.setBoolean(SECTION_NOTE_DB, null, "writeJson", true);
    cfg.save();
  }
}
