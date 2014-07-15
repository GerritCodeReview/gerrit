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

import com.google.common.base.Joiner;
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

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class DbGroupMemberAuditListener implements GroupMemberAuditListener {
  private static final Logger log = org.slf4j.LoggerFactory
      .getLogger(DbGroupMemberAuditListener.class);

  private Provider<ReviewDb> db;

  @Inject
  public DbGroupMemberAuditListener(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public void onAddAccountsToGroup(Id me, Collection<AccountGroupMember> added) {
    List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for (AccountGroupMember m : added) {
      AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
      auditInserts.add(audit);
    }
    try {
      db.get().accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      StringBuilder message =
          new StringBuilder(
              "Cannot log add accounts to group event performed by user ");
      message.append(me);
      message.append(": ");
      message.append(Joiner.on("; ").join(added));
      log.error(message.toString(), e);
    }
  }

  @Override
  public void onDeleteAccountsFromGroup(Id me,
      Collection<AccountGroupMember> removed) {
    List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    List<AccountGroupMemberAudit> auditUpdates = Lists.newLinkedList();
    ReviewDb reviewDB = db.get();
    try {
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
      StringBuilder message =
          new StringBuilder(
              "Cannot log delete accounts from group event performed by user ");
      message.append(me);
      message.append(": ");
      message.append(Joiner.on("; ").join(removed));
      log.error(message.toString(), e);
    }
  }

  @Override
  public void onAddGroupsToGroup(Id me, Collection<AccountGroupById> added) {
    List<AccountGroupByIdAud> includesAudit = new ArrayList<>();
    for (AccountGroupById groupInclude : added) {
      AccountGroupByIdAud audit =
          new AccountGroupByIdAud(groupInclude, me, TimeUtil.nowTs());
      includesAudit.add(audit);
    }
    try {
      db.get().accountGroupByIdAud().insert(includesAudit);
    } catch (OrmException e) {
      StringBuilder message =
          new StringBuilder(
              "Cannot log add groups to group event performed by user ");
      message.append(me);
      message.append(": ");
      message.append(Joiner.on("; ").join(added));
      log.error(message.toString(), e);
    }
  }

  @Override
  public void onDeleteGroupsFromGroup(Id me,
      Collection<AccountGroupById> removed) {
    final List<AccountGroupByIdAud> auditUpdates = Lists.newLinkedList();
    try {
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
      StringBuilder message =
          new StringBuilder(
              "Cannot log delete groups from group event performed by user ");
      message.append(me);
      message.append(": ");
      message.append(Joiner.on("; ").join(removed));
      log.error(message.toString(), e);
    }
  }
}
