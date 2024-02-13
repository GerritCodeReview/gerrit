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

package com.google.gerrit.server.index.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Listener for ref update events that reindexes accounts in case the updated Git reference was used
 * to compute contents of an index document.
 *
 * <p>Will reindex accounts when the account's NoteDb ref changes.
 */
public class ReindexAccountsAfterRefUpdate implements GitBatchRefUpdateListener {
    private final AllUsersName allUsersName;
    private final Provider<AccountIndexer> accountIndexer;

    @Inject
    ReindexAccountsAfterRefUpdate(
            AllUsersName allUsersName, Provider<AccountIndexer> accountIndexer) {
        this.allUsersName = allUsersName;
        this.accountIndexer = accountIndexer;
    }

    @Override
    public void onGitBatchRefUpdate(Event event) {
        if (!allUsersName.get().equals(event.getProjectName())) {
            return;
        }
        for (UpdatedRef ref : event.getUpdatedRefs()) {
            if (RefNames.isRefsUsers(ref.getRefName()) && !RefNames.isRefsEdit(ref.getRefName())) {
                Account.Id accountId = Account.Id.fromRef(ref.getRefName());
                if (accountId != null) {
                    accountIndexer.get().index(accountId);
                }
            }
        }
    }
}
