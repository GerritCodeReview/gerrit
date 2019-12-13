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

package com.google.gerrit.server.index.account;

import com.google.gerrit.entities.Account;

/** Interface for indexing a Gerrit account. */
public interface AccountIndexer {

  /**
   * Synchronously index an account.
   *
   * @param id account id to index.
   */
  void index(Account.Id id);

  /**
   * Synchronously reindex an account if it is stale.
   *
   * @param id account id to index.
   * @return whether the account was reindexed
   */
  boolean reindexIfStale(Account.Id id);
}
