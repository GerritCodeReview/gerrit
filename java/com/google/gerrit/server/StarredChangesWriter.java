// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import java.io.IOException;

/** Interface for writing information about starred changes. */
public interface StarredChangesWriter {
  /**
   * Star the given change for a single {@code Account.Id}.
   *
   * @param changeId the {@code Change.Id}.
   * @param accountId the {@code Account.Id}.
   */
  void star(Account.Id accountId, Change.Id changeId);

  /**
   * Unstar the given change for a single {@code Account.Id}.
   *
   * @param changeId the {@code Change.Id}.
   * @param accountId the {@code Account.Id}.
   */
  void unstar(Account.Id accountId, Change.Id changeId);

  /**
   * Unstar the given change for all users.
   *
   * <p>Intended for use only when we're about to delete a change. For that reason, the change is
   * not reindexed.
   *
   * @param changeId change ID.
   * @throws IOException if an error occurred.
   */
  void unstarAllForChangeDeletion(Change.Id changeId) throws IOException;
}
