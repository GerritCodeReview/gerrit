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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.SecondaryKey;

/** Access interface for {@link Account}. */
public interface AccountAccess extends Access<Account, Account.Id> {
  /** Locate an account by our locally generated identity. */
  @PrimaryKey("accountId")
  Account get(Account.Id key) throws OrmException;

  @Query("WHERE preferredEmail = ? LIMIT 2")
  ResultSet<Account> byPreferredEmail(String email) throws OrmException;

  @SecondaryKey("sshUserName")
  Account bySshUserName(String userName) throws OrmException;

  @Query("WHERE fullName = ? LIMIT 2")
  ResultSet<Account> byFullName(String name) throws OrmException;

  @Query("WHERE fullName >= ? AND fullName <= ? ORDER BY fullName LIMIT ?")
  ResultSet<Account> suggestByFullName(String nameA, String nameB, int limit)
      throws OrmException;

  @Query("WHERE preferredEmail >= ? AND preferredEmail <= ? ORDER BY preferredEmail LIMIT ?")
  ResultSet<Account> suggestByPreferredEmail(String nameA, String nameB,
      int limit) throws OrmException;

  @Query("WHERE sshUserName >= ? AND sshUserName <= ? ORDER BY sshUserName LIMIT ?")
  ResultSet<Account> suggestBySshUserName(String nameA, String nameB, int limit)
      throws OrmException;

}
