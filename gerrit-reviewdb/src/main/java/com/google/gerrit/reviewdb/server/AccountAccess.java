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
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

/** Access interface for {@link Account}. */
public interface AccountAccess extends Access<Account, Account.Id> {
  /** Locate an account by our locally generated identity. */
  @Override
  @PrimaryKey("accountId")
  Account get(Account.Id key) throws OrmException;

  @Query("WHERE preferredEmail = ? LIMIT 2")
  ResultSet<Account> byPreferredEmail(String email) throws OrmException;

  @Query("WHERE fullName = ? LIMIT 2")
  ResultSet<Account> byFullName(String name) throws OrmException;

  @Query("WHERE fullName >= ? AND fullName <= ? ORDER BY fullName LIMIT ?")
  ResultSet<Account> suggestByFullName(String nameA, String nameB, int limit) throws OrmException;

  @Query("WHERE preferredEmail >= ? AND preferredEmail <= ? ORDER BY preferredEmail LIMIT ?")
  ResultSet<Account> suggestByPreferredEmail(String nameA, String nameB, int limit)
      throws OrmException;

  @Query("LIMIT 1")
  ResultSet<Account> anyAccounts() throws OrmException;

  @Query("ORDER BY accountId LIMIT ?")
  ResultSet<Account> firstNById(int n) throws OrmException;

  @Query("ORDER BY accountId")
  ResultSet<Account> all() throws OrmException;
}
