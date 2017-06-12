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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface PatchSetApprovalAccess extends Access<PatchSetApproval, PatchSetApproval.Key> {
  @Override
  @PrimaryKey("key")
  PatchSetApproval get(PatchSetApproval.Key key) throws OrmException;

  @Query("WHERE key.patchSetId.changeId = ?")
  ResultSet<PatchSetApproval> byChange(Change.Id id) throws OrmException;

  @Query("WHERE key.patchSetId = ?")
  ResultSet<PatchSetApproval> byPatchSet(PatchSet.Id id) throws OrmException;

  @Query("WHERE key.patchSetId = ? AND key.accountId = ?")
  ResultSet<PatchSetApproval> byPatchSetUser(PatchSet.Id patchSet, Account.Id account)
      throws OrmException;
}
