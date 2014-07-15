// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.common.collect.Lists;
import com.google.gerrit.audit.GroupMemberAuditListener;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class DbGroupMemberAuditListener implements GroupMemberAuditListener {

  private Provider<ReviewDb> db;

  @Inject
  public DbGroupMemberAuditListener(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public void onAddMembers(Id me, Collection<AccountGroupMember> toBeAdded)
      throws IOException {
    List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for (AccountGroupMember m : toBeAdded) {
      AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
      auditInserts.add(audit);
    }
    try {
      db.get().accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      throw new IOException("Cannot log add group member event", e);
    }
  }

  @Override
  public void onDeleteMembers(Id me, Collection<AccountGroupMember> toBeRemoved)
      throws IOException {
    try {
      List<AccountGroupMemberAudit> auditUpdates = Lists.newLinkedList();
      List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
      ReviewDb reviewDB = db.get();
      for (final AccountGroupMember m : toBeRemoved) {
        AccountGroupMemberAudit audit = null;
        for (AccountGroupMemberAudit a : reviewDB.accountGroupMembersAudit()
            .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me, TimeUtil.nowTs());
          auditUpdates.add(audit);
        } else {
          audit = new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
          audit.removedLegacy();
          auditInserts.add(audit);
        }
      }
      reviewDB.accountGroupMembersAudit().update(auditUpdates);
      reviewDB.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      throw new IOException("Cannot log delete group member event", e);
    }
  }
}
