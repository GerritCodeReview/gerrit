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

import static com.google.gerrit.server.Sequence.LightweightAccounts;

import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.notedb.RepoSequence.RepoSequenceModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SequencesOnInit {
  private final Sequence accountsSequence;

  @Inject
  SequencesOnInit(@LightweightAccounts Sequence accountsSequence) {
    this.accountsSequence = accountsSequence;
  }

  public int nextAccountId() {
    return accountsSequence.next();
  }

  /** A accounts sequence provider that does not fire git reference updates. */
  public static class DisabledGitRefUpdatedRepoAccountsSequenceProvider
      implements Provider<Sequence> {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final Config cfg;

    @Inject
    DisabledGitRefUpdatedRepoAccountsSequenceProvider(
        @GerritServerConfig Config cfg,
        GitRepositoryManagerOnInit repoManager,
        AllUsersName allUsersName) {
      this.repoManager = repoManager;
      this.allUsers = allUsersName;
      this.cfg = cfg;
    }

    @Override
    public Sequence get() {
      int accountBatchSize =
          cfg.getInt(
              RepoSequenceModule.SECTION_NOTE_DB,
              Sequence.NAME_ACCOUNTS,
              RepoSequenceModule.KEY_SEQUENCE_BATCH_SIZE,
              RepoSequenceModule.DEFAULT_ACCOUNTS_SEQUENCE_BATCH_SIZE);
      return new RepoSequence(
          repoManager,
          GitReferenceUpdated.DISABLED,
          allUsers,
          Sequence.NAME_ACCOUNTS,
          accountBatchSize);
    }
  }
}
