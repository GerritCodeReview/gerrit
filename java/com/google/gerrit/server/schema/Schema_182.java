// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;

/**
 * Schema 182 for Gerrit metadata.
 *
 * <p>Upgrading to this schema version cleans the zombie draft comment refs in NoteDb
 */
public class Schema_182 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    AllUsersName allUsers = args.allUsers;
    GitRepositoryManager gitRepoManager = args.repoManager;
    int cleanupPercentage = 100;
    DeleteZombieCommentsRefs cleanup =
        new DeleteZombieCommentsRefs(allUsers, gitRepoManager, cleanupPercentage);
    cleanup.execute();
  }
}
