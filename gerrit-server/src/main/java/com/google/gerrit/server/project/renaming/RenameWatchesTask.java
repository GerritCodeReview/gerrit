// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RenameWatchesTask implements Task {
  private static final Logger log = LoggerFactory
      .getLogger(RenameWatchesTask.class);

  private final ReviewDb db;

  private final Project.NameKey source;
  private final Project.NameKey destination;

  public interface Factory extends Task.Factory {
    RenameWatchesTask create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  @Inject
  public RenameWatchesTask(ReviewDb db,
      @Assisted("source") Project.NameKey source,
      @Assisted("destination") Project.NameKey destination) {
    this.db = db;
    this.source = source;
    this.destination = destination;
  }

  private void renameWatches(Project.NameKey renameFrom,
      Project.NameKey renameTo) throws ProjectRenamingFailedException {
    try {
      List<AccountProjectWatch> accountProjectWatches =
          db.accountProjectWatches().byProject(renameFrom).toList();
      if (!accountProjectWatches.isEmpty()) {
        // As the project name is part of the watch's key, we cannot just
        // update the watch, but have delete the watch and reinsert it after
        // updating the project name.
        db.accountProjectWatches().delete(accountProjectWatches);
        for(AccountProjectWatch accountProjectWatch: accountProjectWatches) {
          accountProjectWatch.setProjectNameKey(renameTo);
        }
        db.accountProjectWatches().insert(accountProjectWatches);
      }
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not update project "
          + "watches", e);
    }
  }

  @Override
  public void carryOut() throws ProjectRenamingFailedException {
    renameWatches(source, destination);
  }

  @Override
  public void rollback() {
    try {
      renameWatches(destination, source);
    } catch (Throwable e) {
      log.error("Could not roll back renaming watches for " + source + ". "
          + "Please rename watches for " + destination + " to " + source + ".",
          e);
    }
  }
}
