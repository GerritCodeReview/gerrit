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

package com.google.gerrit.pgm.init.api;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SequencesOnInit {
  private final GitRepositoryManager repoManager;
  private final AllUsersNameOnInitProvider allUsersName;

  @Inject
  SequencesOnInit(GitRepositoryManagerOnInit repoManager, AllUsersNameOnInitProvider allUsersName) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  public int nextAccountId() {
    RepoSequence accountSeq =
        new RepoSequence(
            repoManager,
            GitReferenceUpdated.DISABLED,
            Project.nameKey(allUsersName.get()),
            Sequences.NAME_ACCOUNTS,
            () -> Sequences.FIRST_ACCOUNT_ID,
            1);
    return accountSeq.next();
  }
}
