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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RenameChangesTask implements Task {
  private static final Logger log = LoggerFactory
      .getLogger(RenameChangesTask.class);

  private final ReviewDb db;

  private final Project.NameKey source;
  private final Project.NameKey destination;

  public interface Factory extends Task.Factory {
    RenameChangesTask create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  @Inject
  public RenameChangesTask(ReviewDb db,
      @Assisted("source") Project.NameKey source,
      @Assisted("destination") Project.NameKey destination) {
    this.db = db;
    this.source = source;
    this.destination = destination;
  }

  private void renameChanges(Project.NameKey renameFrom,
      Project.NameKey renameTo) throws ProjectRenamingFailedException {
    try {
      List<Change> changes = db.changes().byProject(renameFrom)
          .toList();
      for(Change change: changes) {
        change.setProject(renameTo);
      }
      db.changes().update(changes);
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not update project "
          + "changes", e);
    }
  }

  @Override
  public void carryOut() throws ProjectRenamingFailedException {
    renameChanges(source, destination);
  }

  @Override
  public void rollback() {
    try {
      renameChanges(destination, source);
    } catch (Throwable e) {
      log.error("Could not roll back renaming changes for " + source + ". "
          + "Please rename changes for " + destination + " to " + source + ".",
          e);
    }
  }
}
