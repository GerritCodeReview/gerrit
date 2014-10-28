// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface AccountSshKeyAccess extends
    Access<AccountSshKey, AccountSshKey.Id> {
  @Override
  @PrimaryKey("id")
  AccountSshKey get(AccountSshKey.Id id) throws OrmException;

  @Query("WHERE id.accountId = ?")
  ResultSet<AccountSshKey> byAccount(Account.Id id) throws OrmException;

  @Query("WHERE id.accountId = ? ORDER BY id.seq DESC LIMIT 1")
  ResultSet<AccountSshKey> byAccountLast(Account.Id id) throws OrmException;
}
