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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Account;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Singleton;

/** Collection of active account indices. See {@link IndexCollection} for details on collections. */
@Singleton
public class AccountIndexCollection
    extends IndexCollection<Account.Id, AccountState, AccountIndex> {
  @VisibleForTesting
  public AccountIndexCollection() {}
}
