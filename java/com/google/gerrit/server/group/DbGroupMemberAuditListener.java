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
import com.google.gerrit.audit.GroupMemberAuditListener;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;

class DbGroupMemberAuditListener implements GroupMemberAuditListener {
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DbGroupMemberAuditListener.class);

  private final SchemaFactory<ReviewDb> schema;
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final UniversalGroupBackend groupBackend;

  @Inject
  DbGroupMemberAuditListener(
      SchemaFactory<ReviewDb> schema,
      AccountCache accountCache,
      GroupCache groupCache,
      UniversalGroupBackend groupBackend) {
    this.schema = schema;
    this.accountCache = accountCache;
    this.groupCache = groupCache;
    this.groupBackend = groupBackend;
  }

  @Override
  public void onAddAccountsToGroup(Account.Id me, Collection<AccountGroupMember> added) {
    List<AccountGroupMemberAudit> auditInserts = new ArrayList<>();
    for (AccountGroupMember m : added) {
      AccountGroupMemberAudit audit = new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
      auditInserts.add(audit);
    }
    try (ReviewDb db = schema.open()) {
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmExceptionForAccounts(
          "Cannot log add accounts to group event performed by user", me, added, e);
    }
  }

  @Override
  public void onDeleteAccountsFromGroup(Account.Id me, Collection<AccountGroupMember> removed) {
    List<AccountGroupMemberAudit> auditInserts = new ArrayList<>();
    List<AccountGroupMemberAudit> auditUpdates = new ArrayList<>();
    try (ReviewDb db = schema.open()) {
      for (AccountGroupMember m : removed) {
        AccountGroupMemberAudit audit = null;
        for (AccountGroupMemberAudit a :
            db.accountGroupMembersAudit().byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
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
      db.accountGroupMembersAudit().update(auditUpdates);
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmExceptionForAccounts(
          "Cannot log delete accounts from group event performed by user", me, removed, e);
    }
  }

  @Override
  public void onAddGroupsToGroup(Account.Id me, Collection<AccountGroupById> added) {
    List<AccountGroupByIdAud> includesAudit = new ArrayList<>();
    for (AccountGroupById groupInclude : added) {
      AccountGroupByIdAud audit = new AccountGroupByIdAud(groupInclude, me, TimeUtil.nowTs());
      includesAudit.add(audit);
    }
    try (ReviewDb db = schema.open()) {
      db.accountGroupByIdAud().insert(includesAudit);
    } catch (OrmException e) {
      logOrmExceptionForGroups(
          "Cannot log add groups to group event performed by user", me, added, e);
    }
  }

  @Override
  public void onDeleteGroupsFromGroup(Account.Id me, Collection<AccountGroupById> removed) {
    final List<AccountGroupByIdAud> auditUpdates = new ArrayList<>();
    try (ReviewDb db = schema.open()) {
      for (AccountGroupById g : removed) {
        AccountGroupByIdAud audit = null;
        for (AccountGroupByIdAud a :
            db.accountGroupByIdAud().byGroupInclude(g.getGroupId(), g.getIncludeUUID())) {
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
      db.accountGroupByIdAud().update(auditUpdates);
    } catch (OrmException e) {
      logOrmExceptionForGroups(
          "Cannot log delete groups from group event performed by user", me, removed, e);
    }
  }

  private void logOrmExceptionForAccounts(
      String header, Account.Id me, Collection<AccountGroupMember> values, OrmException e) {
    List<String> descriptions = new ArrayList<>();
    for (AccountGroupMember m : values) {
      Account.Id accountId = m.getAccountId();
      String userName = accountCache.get(accountId).getUserName();
      AccountGroup.Id groupId = m.getAccountGroupId();
      String groupName = groupCache.get(groupId).getName();

      descriptions.add(
          MessageFormat.format(
              "account {0}/{1}, group {2}/{3}", accountId, userName, groupId, groupName));
    }
    logOrmException(header, me, descriptions, e);
  }

  private void logOrmExceptionForGroups(
      String header, Account.Id me, Collection<AccountGroupById> values, OrmException e) {
    List<String> descriptions = new ArrayList<>();
    for (AccountGroupById m : values) {
      AccountGroup.UUID groupUuid = m.getIncludeUUID();
      String groupName = groupBackend.get(groupUuid).getName();
      AccountGroup.Id targetGroupId = m.getGroupId();
      String targetGroupName = groupCache.get(targetGroupId).getName();

      descriptions.add(
          MessageFormat.format(
              "group {0}/{1}, group {2}/{3}",
              groupUuid, groupName, targetGroupId, targetGroupName));
    }
    logOrmException(header, me, descriptions, e);
  }

  private void logOrmException(String header, Account.Id me, Iterable<?> values, OrmException e) {
    StringBuilder message = new StringBuilder(header);
    message.append(" ");
    message.append(me);
    message.append("/");
    message.append(accountCache.get(me).getUserName());
    message.append(": ");
    message.append(Joiner.on("; ").join(values));
    log.error(message.toString(), e);
  }
}
