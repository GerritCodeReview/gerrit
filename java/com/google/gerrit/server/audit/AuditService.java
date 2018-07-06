// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.audit;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.audit.group.GroupMemberAuditEvent;
import com.google.gerrit.server.audit.group.GroupSubgroupAuditEvent;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;

@Singleton
public class AuditService implements GroupAuditService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<AuditListener> auditListeners;
  private final DynamicSet<GroupAuditListener> groupAuditListeners;

  @Inject
  public AuditService(
      DynamicSet<AuditListener> auditListeners,
      DynamicSet<GroupAuditListener> groupAuditListeners) {
    this.auditListeners = auditListeners;
    this.groupAuditListeners = groupAuditListeners;
  }

  public void dispatch(AuditEvent action) {
    for (AuditListener auditListener : auditListeners) {
      auditListener.onAuditableAction(action);
    }
  }

  @Override
  public void dispatchAddMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> addedMembers,
      Timestamp addedOn) {
    for (GroupAuditListener auditListener : groupAuditListeners) {
      try {
        GroupMemberAuditEvent event =
            GroupMemberAuditEvent.create(actor, updatedGroup, addedMembers, addedOn);
        auditListener.onAddMembers(event);
      } catch (RuntimeException e) {
        logger.atSevere().withCause(e).log("failed to log add accounts to group event");
      }
    }
  }

  @Override
  public void dispatchDeleteMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> deletedMembers,
      Timestamp deletedOn) {
    for (GroupAuditListener auditListener : groupAuditListeners) {
      try {
        GroupMemberAuditEvent event =
            GroupMemberAuditEvent.create(actor, updatedGroup, deletedMembers, deletedOn);
        auditListener.onDeleteMembers(event);
      } catch (RuntimeException e) {
        logger.atSevere().withCause(e).log("failed to log delete accounts from group event");
      }
    }
  }

  @Override
  public void dispatchAddSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> addedSubgroups,
      Timestamp addedOn) {
    for (GroupAuditListener auditListener : groupAuditListeners) {
      try {
        GroupSubgroupAuditEvent event =
            GroupSubgroupAuditEvent.create(actor, updatedGroup, addedSubgroups, addedOn);
        auditListener.onAddSubgroups(event);
      } catch (RuntimeException e) {
        logger.atSevere().withCause(e).log("failed to log add groups to group event");
      }
    }
  }

  @Override
  public void dispatchDeleteSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> deletedSubgroups,
      Timestamp deletedOn) {
    for (GroupAuditListener auditListener : groupAuditListeners) {
      try {
        GroupSubgroupAuditEvent event =
            GroupSubgroupAuditEvent.create(actor, updatedGroup, deletedSubgroups, deletedOn);
        auditListener.onDeleteSubgroups(event);
      } catch (RuntimeException e) {
        logger.atSevere().withCause(e).log("failed to log delete groups from group event");
      }
    }
  }
}
