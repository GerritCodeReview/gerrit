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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface AccountGroupMemberAuditAccess
    extends Access<AccountGroupMemberAudit, AccountGroupMemberAudit.Key> {
  @Override
  @PrimaryKey("key")
  AccountGroupMemberAudit get(AccountGroupMemberAudit.Key key) throws OrmException;

  @Query("WHERE key.groupId = ? AND key.accountId = ?")
  ResultSet<AccountGroupMemberAudit> byGroupAccount(AccountGroup.Id groupId, Account.Id accountId)
      throws OrmException;

  @Query("WHERE key.groupId = ?")
  ResultSet<AccountGroupMemberAudit> byGroup(AccountGroup.Id groupId) throws OrmException;
}
