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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.audit.group.GroupAuditEvent;
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
import java.util.Optional;
import org.slf4j.Logger;

class DbGroupAuditListener implements GroupAuditListener {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DbGroupAuditListener.class);

  private final SchemaFactory<ReviewDb> schema;
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final UniversalGroupBackend groupBackend;

  @Inject
  DbGroupAuditListener(
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
    Optional<InternalGroup> updatedGroup = groupCache.get(event.getUpdatedGroup());
    if (!updatedGroup.isPresent()) {
      logFailToLoadUpdatedGroup(event);
      return;
    }

    InternalGroup group = updatedGroup.get();
    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupMembersAudit().insert(toAccountGroupMemberAudits(event, group.getId()));
    } catch (OrmException e) {
      logOrmException(
          "Cannot log add accounts to group event performed by user", event, group.getName(), e);
    }
  }

  @Override
  public void onDeleteMembers(GroupMemberAuditEvent event) {
    Optional<InternalGroup> updatedGroup = groupCache.get(event.getUpdatedGroup());
    if (!updatedGroup.isPresent()) {
      logFailToLoadUpdatedGroup(event);
      return;
    }

    InternalGroup group = updatedGroup.get();
    List<AccountGroupMemberAudit> auditInserts = new ArrayList<>();
    List<AccountGroupMemberAudit> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (Account.Id accountId : event.getModifiedMembers()) {
        AccountGroupMemberAudit audit = null;
        ResultSet<AccountGroupMemberAudit> audits =
            db.accountGroupMembersAudit().byGroupAccount(group.getId(), accountId);
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
        AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, group.getId());
        audit =
            new AccountGroupMemberAudit(
                new AccountGroupMember(key), event.getActor(), event.getTimestamp());
        audit.removedLegacy();
        auditInserts.add(audit);
      }
      db.accountGroupMembersAudit().update(auditUpdates);
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      logOrmException(
          "Cannot log delete accounts from group event performed by user",
          event,
          group.getName(),
          e);
    }
  }

  @Override
  public void onAddSubgroups(GroupSubgroupAuditEvent event) {
    Optional<InternalGroup> updatedGroup = groupCache.get(event.getUpdatedGroup());
    if (!updatedGroup.isPresent()) {
      logFailToLoadUpdatedGroup(event);
      return;
    }

    InternalGroup group = updatedGroup.get();
    try (ReviewDb db = unwrapDb(schema.open())) {
      db.accountGroupByIdAud().insert(toAccountGroupByIdAudits(event, group.getId()));
    } catch (OrmException e) {
      logOrmException(
          "Cannot log add groups to group event performed by user", event, group.getName(), e);
    }
  }

  @Override
  public void onDeleteSubgroups(GroupSubgroupAuditEvent event) {
    Optional<InternalGroup> updatedGroup = groupCache.get(event.getUpdatedGroup());
    if (!updatedGroup.isPresent()) {
      logFailToLoadUpdatedGroup(event);
      return;
    }

    InternalGroup group = updatedGroup.get();
    List<AccountGroupByIdAud> auditUpdates = new ArrayList<>();
    try (ReviewDb db = unwrapDb(schema.open())) {
      for (AccountGroup.UUID uuid : event.getModifiedSubgroups()) {
        AccountGroupByIdAud audit = null;
        ResultSet<AccountGroupByIdAud> audits =
            db.accountGroupByIdAud().byGroupInclude(updatedGroup.get().getId(), uuid);
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
      logOrmException(
          "Cannot log delete groups from group event performed by user", event, group.getName(), e);
    }
  }

  private void logFailToLoadUpdatedGroup(GroupAuditEvent event) {
    ImmutableList<String> descriptions = createEventDescriptions(event, "(fail to load group)");
    String message =
        createErrorMessage("Fail to load the updated group", event.getActor(), descriptions);
    log.error(message);
  }

  private void logOrmException(
      String header, GroupAuditEvent event, String updatedGroupName, OrmException e) {
    ImmutableList<String> descriptions = createEventDescriptions(event, updatedGroupName);
    String message = createErrorMessage(header, event.getActor(), descriptions);
    log.error(message, e);
  }

  private ImmutableList<String> createEventDescriptions(
      GroupAuditEvent event, String updatedGroupName) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    if (event instanceof GroupMemberAuditEvent) {
      GroupMemberAuditEvent memberAuditEvent = (GroupMemberAuditEvent) event;
      for (Account.Id accountId : memberAuditEvent.getModifiedMembers()) {
        String userName = getUserName(accountId).orElse("");
        builder.add(
            MessageFormat.format(
                "account {0}/{1}, group {2}/{3}",
                accountId, userName, event.getUpdatedGroup(), updatedGroupName));
      }
    } else if (event instanceof GroupSubgroupAuditEvent) {
      GroupSubgroupAuditEvent subgroupAuditEvent = (GroupSubgroupAuditEvent) event;
      for (AccountGroup.UUID groupUuid : subgroupAuditEvent.getModifiedSubgroups()) {
        String groupName = groupBackend.get(groupUuid).getName();
        builder.add(
            MessageFormat.format(
                "group {0}/{1}, group {2}/{3}",
                groupUuid, groupName, subgroupAuditEvent.getUpdatedGroup(), updatedGroupName));
      }
    }

    return builder.build();
  }

  private String createErrorMessage(
      String header, Account.Id me, ImmutableList<String> descriptions) {
    StringBuilder message = new StringBuilder(header);
    message.append(" ");
    message.append(me);
    message.append("/");
    message.append(getUserName(me).orElse(null));
    message.append(": ");
    message.append(Joiner.on("; ").join(descriptions));
    return message.toString();
  }

  private Optional<String> getUserName(Account.Id accountId) {
    return accountCache.get(accountId).map(AccountState::getUserName).orElse(Optional.empty());
  }

  private static ImmutableSet<AccountGroupMemberAudit> toAccountGroupMemberAudits(
      GroupMemberAuditEvent event, AccountGroup.Id updatedGroupId) {
    Timestamp timestamp = event.getTimestamp();
    Account.Id actor = event.getActor();
    return event
        .getModifiedMembers()
        .stream()
        .map(
            member ->
                new AccountGroupMemberAudit(
                    new AccountGroupMemberAudit.Key(member, updatedGroupId, timestamp), actor))
        .collect(toImmutableSet());
  }

  private static ImmutableSet<AccountGroupByIdAud> toAccountGroupByIdAudits(
      GroupSubgroupAuditEvent event, AccountGroup.Id updatedGroupId) {
    Timestamp timestamp = event.getTimestamp();
    Account.Id actor = event.getActor();
    return event
        .getModifiedSubgroups()
        .stream()
        .map(
            subgroup ->
                new AccountGroupByIdAud(
                    new AccountGroupByIdAud.Key(updatedGroupId, subgroup, timestamp), actor))
        .collect(toImmutableSet());
  }
}
