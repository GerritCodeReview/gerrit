// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * View of a single {@link Change} and its metadata.
 */
public class NoteDbNotes {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final AllUsersName draftsProject;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        AllUsersNameProvider allUsers) {
      this.repoManager = repoManager;
      this.draftsProject = allUsers.get();
    }

    public NoteDbNotes create(Change change, Account.Id accountId) {
      return new NoteDbNotes(
          new ChangeNotes(repoManager, change),
          new DraftCommentNotes(repoManager, draftsProject, change, accountId));
    }
  }

  private final ChangeNotes changeNotes;
  private final DraftCommentNotes draftCommentNotes;

  @Inject
  NoteDbNotes(ChangeNotes changeNotes, DraftCommentNotes draftCommentNotes) {
    this.changeNotes = changeNotes;
    this.draftCommentNotes = draftCommentNotes;
  }

  public ChangeNotes getChangeNotes() {
    return changeNotes;
  }

  public DraftCommentNotes getDraftCommentNotes() {
    return draftCommentNotes;
  }

  public void load() throws OrmException {
    changeNotes.load();
    draftCommentNotes.load();
  }
}
