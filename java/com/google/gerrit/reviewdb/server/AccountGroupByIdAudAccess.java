// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface AccountGroupByIdAudAccess
    extends Access<AccountGroupByIdAud, AccountGroupByIdAud.Key> {
  @Override
  @PrimaryKey("key")
  AccountGroupByIdAud get(AccountGroupByIdAud.Key key) throws OrmException;

  @Query("WHERE key.groupId = ? AND key.includeUUID = ?")
  ResultSet<AccountGroupByIdAud> byGroupInclude(
      AccountGroup.Id groupId, AccountGroup.UUID incGroupUUID) throws OrmException;

  @Query("WHERE key.groupId = ?")
  ResultSet<AccountGroupByIdAud> byGroup(AccountGroup.Id groupId) throws OrmException;
}
