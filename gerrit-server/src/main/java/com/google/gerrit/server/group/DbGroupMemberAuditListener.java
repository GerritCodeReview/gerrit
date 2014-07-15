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
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class DbGroupMemberAuditListener implements GroupMemberAuditListener {

  private Provider<ReviewDb> db;

  @Inject
  public DbGroupMemberAuditListener(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public void onAddAccountsToGroup(Id me, Collection<AccountGroupMember> added)
      throws IOException {
    List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for (AccountGroupMember m : added) {
      AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
      auditInserts.add(audit);
    }
    try {
      db.get().accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      throw new IOException("Cannot log add add accounts to group event", e);
    }
  }

  @Override
  public void onDeleteAccountsFromGroup(Id me,
      Collection<AccountGroupMember> removed) throws IOException {
    try {
      List<AccountGroupMemberAudit> auditUpdates = Lists.newLinkedList();
      List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
      ReviewDb reviewDB = db.get();
      for (AccountGroupMember m : removed) {
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
      throw new IOException("Cannot log delete accounts from group event", e);
    }
  }

  @Override
  public void onAddGroupsToGroup(Id actor, Collection<AccountGroupById> added)
      throws IOException {
    try {
      List<AccountGroupByIdAud> includesAudit = new ArrayList<>();
      for (AccountGroupById groupInclude : added) {
        AccountGroupByIdAud audit =
            new AccountGroupByIdAud(groupInclude, actor, TimeUtil.nowTs());
        includesAudit.add(audit);
      }
      db.get().accountGroupByIdAud().insert(includesAudit);
    } catch (OrmException e) {
      throw new IOException("Cannot log add groups to group event", e);
    }
  }

  @Override
  public void onDeleteGroupsFromGroup(Id me,
      Collection<AccountGroupById> removed) throws IOException {
    try {
      final List<AccountGroupByIdAud> auditUpdates = Lists.newLinkedList();
      for (final AccountGroupById g : removed) {
        AccountGroupByIdAud audit = null;
        for (AccountGroupByIdAud a : db.get().accountGroupByIdAud()
            .byGroupInclude(g.getGroupId(), g.getIncludeUUID())) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me, TimeUtil.nowTs());
          auditUpdates.add(audit);
        }
      }
      db.get().accountGroupByIdAud().update(auditUpdates);
    } catch (OrmException e) {
      throw new IOException("Cannot log delete groups from group event", e);
    }
  }
}
