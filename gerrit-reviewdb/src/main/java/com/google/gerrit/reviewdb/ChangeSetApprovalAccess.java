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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface ChangeSetApprovalAccess extends
    Access<ChangeSetApproval, ChangeSetApproval.Key> {
  @PrimaryKey("key")
  ChangeSetApproval get(ChangeSetApproval.Key key) throws OrmException;

  @Query("WHERE key.changeSetId.topicId = ?")
  ResultSet<ChangeSetApproval> byTopic(Topic.Id id) throws OrmException;

  @Query("WHERE key.changeSetId = ?")
  ResultSet<ChangeSetApproval> byChangeSet(ChangeSet.Id id) throws OrmException;

  @Query("WHERE key.changeSetId = ? AND key.accountId = ?")
  ResultSet<ChangeSetApproval> byChangeSetUser(ChangeSet.Id changeSet,
      Account.Id account) throws OrmException;

  @Query("WHERE topicOpen = true AND key.accountId = ?")
  ResultSet<ChangeSetApproval> openByUser(Account.Id account)
      throws OrmException;

  @Query("WHERE topicOpen = false AND key.accountId = ?"
      + " ORDER BY topicSortKey DESC LIMIT 10")
  ResultSet<ChangeSetApproval> closedByUser(Account.Id account)
      throws OrmException;

  @Query("WHERE topicOpen = false AND key.accountId = ? ORDER BY topicSortKey")
  ResultSet<ChangeSetApproval> closedByUserAll(Account.Id account)
      throws OrmException;
}
