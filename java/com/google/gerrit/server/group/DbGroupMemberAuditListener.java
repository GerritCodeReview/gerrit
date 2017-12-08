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

import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;

import com.google.common.base.Joiner;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.audit.GroupMemberAuditListener;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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
  public void onAddAccountsToGroup(
      Account.Id me,
      Collection<Account.Id> addedAccounts,
      AccountGroup.Id groupId,
      Timestamp addedOn) {
    List<AccountGroupMemberAudit> auditInserts =
        addedAccounts
            .stream()
            .map(t -> toAccountGroupMemberAudit(t, groupId, addedOn, me))
            .collect(Collectors.toList());

    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmExceptionForAccounts(
          "Cannot log add accounts to group event performed by user",
          me,
          addedAccounts,
          groupId,
          e);
    }
  }

  @Override
  public void onDeleteAccountsFromGroup(
      Account.Id me,
      Collection<Account.Id> deletedAccounts,
      AccountGroup.Id groupId,
      Timestamp deletedOn) {
    List<AccountGroupMemberAudit> auditInserts = new ArrayList<>();
    List<AccountGroupMemberAudit> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (Account.Id id : deletedAccounts) {
        AccountGroupMemberAudit audit = null;
        for (AccountGroupMemberAudit a :
            db.accountGroupMembersAudit().byGroupAccount(groupId, id)) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me, deletedOn);
          auditUpdates.add(audit);
        } else {
          audit = toAccountGroupMemberAudit(id, groupId, deletedOn, me);
          audit.removedLegacy();
          auditInserts.add(audit);
        }
      }
      db.accountGroupMembersAudit().update(auditUpdates);
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmExceptionForAccounts(
          "Cannot log delete accounts from group event performed by user",
          me,
          deletedAccounts,
          groupId,
          e);
    }
  }

  @Override
  public void onAddGroupsToGroup(
      Account.Id me,
      Collection<AccountGroup.UUID> addedSubgroups,
      AccountGroup.Id parentGroupId,
      Timestamp addedOn) {
    List<AccountGroupByIdAud> includesAudit =
        addedSubgroups
            .stream()
            .map(t -> toAccountGroupByIdAud(t, parentGroupId, addedOn, me))
            .collect(Collectors.toList());

    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupByIdAud().insert(includesAudit);
    } catch (OrmException e) {
      logOrmExceptionForGroups(
          "Cannot log add groups to group event performed by user",
          me,
          addedSubgroups,
          parentGroupId,
          e);
    }
  }

  @Override
  public void onDeleteGroupsFromGroup(
      Account.Id me,
      Collection<AccountGroup.UUID> removed,
      AccountGroup.Id parentGroupId,
      Timestamp deletedOn) {
    final List<AccountGroupByIdAud> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (AccountGroup.UUID uuid : removed) {
        AccountGroupByIdAud audit = null;
        for (AccountGroupByIdAud a : db.accountGroupByIdAud().byGroupInclude(parentGroupId, uuid)) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me, deletedOn);
          auditUpdates.add(audit);
        }
      }
      db.accountGroupByIdAud().update(auditUpdates);
    } catch (OrmException e) {
      logOrmExceptionForGroups(
          "Cannot log delete groups from group event performed by user",
          me,
          removed,
          parentGroupId,
          e);
    }
  }

  private static AccountGroupMemberAudit toAccountGroupMemberAudit(
      Account.Id id, AccountGroup.Id groupId, Timestamp ts, Account.Id me) {
    return new AccountGroupMemberAudit(new AccountGroupMemberAudit.Key(id, groupId, ts), me);
  }

  private static AccountGroupByIdAud toAccountGroupByIdAud(
      AccountGroup.UUID uuid, AccountGroup.Id groupId, Timestamp ts, Account.Id me) {
    return new AccountGroupByIdAud(new AccountGroupByIdAud.Key(groupId, uuid, ts), me);
  }

  private void logOrmExceptionForAccounts(
      String header,
      Account.Id me,
      Collection<Account.Id> accounts,
      AccountGroup.Id groupId,
      OrmException e) {
    List<String> descriptions = new ArrayList<>();
    for (Account.Id id : accounts) {
      String userName = accountCache.get(id).getUserName();
      String groupName = getGroupName(groupId);
      descriptions.add(
          MessageFormat.format("account {0}/{1}, group {2}/{3}", id, userName, groupId, groupName));
    }
    logOrmException(header, me, descriptions, e);
  }

  private void logOrmExceptionForGroups(
      String header,
      Account.Id me,
      Collection<AccountGroup.UUID> uuids,
      AccountGroup.Id groupId,
      OrmException e) {
    List<String> descriptions = new ArrayList<>();
    for (AccountGroup.UUID uuid : uuids) {
      String groupName = groupBackend.get(uuid).getName();
      String targetGroupName = getGroupName(groupId);

      descriptions.add(
          MessageFormat.format(
              "group {0}/{1}, group {2}/{3}", uuid, groupName, groupId, targetGroupName));
    }
    logOrmException(header, me, descriptions, e);
  }

  private String getGroupName(AccountGroup.Id groupId) {
    return groupCache.get(groupId).map(InternalGroup::getName).orElse("Deleted group " + groupId);
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
