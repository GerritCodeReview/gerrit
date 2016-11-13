// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface AccountGroupNameAccess extends Access<AccountGroupName, AccountGroup.NameKey> {
  @Override
  @PrimaryKey("name")
  AccountGroupName get(AccountGroup.NameKey name) throws OrmException;

  @Query("ORDER BY name")
  ResultSet<AccountGroupName> all() throws OrmException;

  @Query("WHERE name.name >= ? AND name.name <= ? ORDER BY name LIMIT ?")
  ResultSet<AccountGroupName> suggestByName(String nameA, String nameB, int limit)
      throws OrmException;
}
