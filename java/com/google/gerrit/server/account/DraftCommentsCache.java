// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import java.util.List;

/** Tracks the change IDs containing drafts for a given account ID. */
public interface DraftCommentsCache {
  /**
   * Returns the list of {@link Change.Id}(s) containing draft comments for the user identified by
   * the {@code accountId} parameter.
   */
  List<Change.Id> get(Account.Id accountId);

  /** Remove the association of the list of changes containing drafts for a given account ID. */
  void evict(Account.Id accountId);
}
