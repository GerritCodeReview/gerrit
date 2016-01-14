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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class Sequences {
  private final Provider<ReviewDb> db;
  private final NotesMigration migration;
  private final RepoSequence changeSeq;

  @Inject
  Sequences(Provider<ReviewDb> db,
      NotesMigration migration,
      GitRepositoryManager repoManager,
      AllProjectsName allProjects) {
    this.db = db;
    this.migration = migration;
    if (migration.readChanges()) {
      changeSeq = new RepoSequence(
          repoManager, allProjects, "changes", ReviewDb.FIRST_CHANGE_ID, 100);
    } else {
      changeSeq = null;
    }
  }

  @SuppressWarnings("deprecation")
  public int nextChangeId() throws OrmException {
    if (migration.readChanges()) {
      return changeSeq.next();
    } else {
      return db.get().nextChangeId();
    }
  }
}
