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

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;

/** Create account sequence in NoteDb */
public class Schema_155 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Schema_155(
      Provider<Schema_154> prior, GitRepositoryManager repoManager, AllUsersName allUsersName) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    @SuppressWarnings("deprecation")
    RepoSequence.Seed accountSeed = () -> db.nextAccountId();
    RepoSequence accountSeq =
        new RepoSequence(
            repoManager,
            GitReferenceUpdated.DISABLED,
            allUsersName,
            Sequences.NAME_ACCOUNTS,
            accountSeed,
            1);

    // consume one account ID to ensure that the account sequence is initialized in NoteDb
    accountSeq.next();
  }
}
