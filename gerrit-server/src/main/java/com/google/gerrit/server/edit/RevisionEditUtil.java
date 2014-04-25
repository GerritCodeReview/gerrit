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

package com.google.gerrit.server.edit;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/**
 * Utility functions to manipulate revision edits.
 * <p>
 * This class contains method to retrieve edits.
 */
@Singleton
public class RevisionEditUtil {
  private final GitRepositoryManager gitManager;
  private final Provider<IdentifiedUser> user;

  @Inject
  public RevisionEditUtil(GitRepositoryManager gitManager,
      Provider<IdentifiedUser> user) {
    this.gitManager = gitManager;
    this.user = user;
  }

  /**
   * Retrieve edits for a change and user. Max one change can exist
   * per user and change. Return collection to not mess around with
   * null values.
   * <p>
   * @param change
   * @return Collection with revision edits for this change
   * @throws AuthException
   * @throws IOException
   */
  public ImmutableList<RevisionEdit> byChange(Change change)
      throws AuthException, IOException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      IdentifiedUser identifiedUser = (IdentifiedUser) user.get();
      Ref ref = repo.getRefDatabase().getRef(editRefName(
          identifiedUser.getAccountId(), change.getId()));
      if (ref == null) {
        return ImmutableList.of();
      }
      return ImmutableList.of(new RevisionEdit(identifiedUser, change, ref));
    } finally {
      repo.close();
    }
  }

  /**
   * Returns reference for this revision edit with sharded user and change number:
   * refs/users/UU/UUUU/edit-CCCC.
   * <p>
   * @param accountId accout id
   * @param changeId change number
   * @return reference for this revision edit
   */
  static String editRefName(Account.Id accountId, Change.Id changeId) {
    return String.format("%s/edit-%d",
        RefNames.refsUsers(accountId),
        changeId.get());
  }
}
