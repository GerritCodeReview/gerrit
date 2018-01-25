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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.audit.group.GroupMemberAuditEvent;
import com.google.gerrit.server.audit.group.GroupSubgroupAuditEvent;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

class DbGroupMemberAuditListener implements GroupAuditListener {
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
  public void onAddMembers(GroupMemberAuditEvent event) {
    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupMembersAudit().insert(toAccountGroupMemberAudits(event));
    } catch (OrmException e) {
      logOrmExceptionForMembersEvent(
          "Cannot log add accounts to group event performed by user", event, e);
    }
  }

  @Override
  public void onDeleteMembers(GroupMemberAuditEvent event) {
    List<AccountGroupMemberAudit> auditInserts = new ArrayList<>();
    List<AccountGroupMemberAudit> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (Account.Id accountId : event.getModifiedMembers()) {
        AccountGroupMemberAudit audit = null;
        ResultSet<AccountGroupMemberAudit> audits =
            db.accountGroupMembersAudit().byGroupAccount(event.getUpdatedGroup(), accountId);
        for (AccountGroupMemberAudit a : audits) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(event.getActor(), event.getTimestamp());
          auditUpdates.add(audit);
          continue;
        }
        AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, event.getUpdatedGroup());
        audit =
            new AccountGroupMemberAudit(
                new AccountGroupMember(key), event.getActor(), event.getTimestamp());
        audit.removedLegacy();
        auditInserts.add(audit);
      }
      db.accountGroupMembersAudit().update(auditUpdates);
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmExceptionForMembersEvent(
          "Cannot log delete accounts from group event performed by user", event, e);
    }
  }

  @Override
  public void onAddSubgroups(GroupSubgroupAuditEvent event) {
    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupByIdAud().insert(toAccountGroupByIdAudits(event));
    } catch (OrmException e) {
      logOrmExceptionForSubgroupsEvent(
          "Cannot log add groups to group event performed by user", event, e);
    }
  }

  @Override
  public void onDeleteSubgroups(GroupSubgroupAuditEvent event) {
    final List<AccountGroupByIdAud> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (AccountGroup.UUID uuid : event.getModifiedSubgroups()) {
        AccountGroupByIdAud audit = null;
        ResultSet<AccountGroupByIdAud> audits =
            db.accountGroupByIdAud().byGroupInclude(event.getUpdatedGroup(), uuid);
        for (AccountGroupByIdAud a : audits) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(event.getActor(), event.getTimestamp());
          auditUpdates.add(audit);
        }
      }
      db.accountGroupByIdAud().update(auditUpdates);
    } catch (OrmException e) {
      logOrmExceptionForSubgroupsEvent(
          "Cannot log delete groups from group event performed by user", event, e);
    }
  }

  private void logOrmExceptionForMembersEvent(
      String header, GroupMemberAuditEvent event, OrmException e) {
    AccountGroup.Id groupId = event.getUpdatedGroup();
    String groupName = getGroupName(groupId);

    List<String> descriptions = new ArrayList<>();
    for (Account.Id accountId : event.getModifiedMembers()) {
      String userName = accountCache.get(accountId).getUserName().orElse(null);
      descriptions.add(
          MessageFormat.format(
              "account {0}/{1}, group {2}/{3}", accountId, userName, groupId, groupName));
    }
    logOrmException(header, event.getActor(), descriptions, e);
  }

  private void logOrmExceptionForSubgroupsEvent(
      String header, GroupSubgroupAuditEvent event, OrmException e) {
    AccountGroup.Id parentGroupId = event.getUpdatedGroup();
    String parentGroupName = getGroupName(parentGroupId);

    List<String> descriptions = new ArrayList<>();
    for (AccountGroup.UUID groupUuid : event.getModifiedSubgroups()) {
      String groupName = groupBackend.get(groupUuid).getName();
      descriptions.add(
          MessageFormat.format(
              "group {0}/{1}, group {2}/{3}",
              groupUuid, groupName, parentGroupId, parentGroupName));
    }
    logOrmException(header, event.getActor(), descriptions, e);
  }

  private String getGroupName(AccountGroup.Id groupId) {
    return groupCache.get(groupId).map(InternalGroup::getName).orElse("Deleted group " + groupId);
  }

  private void logOrmException(String header, Account.Id me, Iterable<?> values, OrmException e) {
    StringBuilder message = new StringBuilder(header);
    message.append(" ");
    message.append(me);
    message.append("/");
    message.append(accountCache.get(me).getUserName().orElse(null));
    message.append(": ");
    message.append(Joiner.on("; ").join(values));
    log.error(message.toString(), e);
  }

  private static ImmutableSet<AccountGroupMemberAudit> toAccountGroupMemberAudits(
      GroupMemberAuditEvent event) {
    AccountGroup.Id updatedGroupId = event.getUpdatedGroup();
    Timestamp timestamp = event.getTimestamp();
    Account.Id actor = event.getActor();
    return event
        .getModifiedMembers()
        .stream()
        .map(
            t ->
                new AccountGroupMemberAudit(
                    new AccountGroupMemberAudit.Key(t, updatedGroupId, timestamp), actor))
        .collect(toImmutableSet());
  }

  private static ImmutableSet<AccountGroupByIdAud> toAccountGroupByIdAudits(
      GroupSubgroupAuditEvent event) {
    AccountGroup.Id updatedGroupId = event.getUpdatedGroup();
    Timestamp timestamp = event.getTimestamp();
    Account.Id actor = event.getActor();
    return event
        .getModifiedSubgroups()
        .stream()
        .map(
            t ->
                new AccountGroupByIdAud(
                    new AccountGroupByIdAud.Key(updatedGroupId, t, timestamp), actor))
        .collect(toImmutableSet());
  }
}
