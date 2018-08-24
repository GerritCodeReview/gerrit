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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.audit.group.GroupMemberAuditEvent;
import com.google.gerrit.server.audit.group.GroupSubgroupAuditEvent;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.logging.PluginContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;

@Singleton
public class AuditService implements GroupAuditService {
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
    PluginContext.invokeIgnoreExceptions(auditListeners, l -> l.onAuditableAction(action));
  }

  @Override
  public void dispatchAddMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> addedMembers,
      Timestamp addedOn) {
    GroupMemberAuditEvent event =
        GroupMemberAuditEvent.create(actor, updatedGroup, addedMembers, addedOn);
    PluginContext.invokeIgnoreExceptions(groupAuditListeners, l -> l.onAddMembers(event));
  }

  @Override
  public void dispatchDeleteMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> deletedMembers,
      Timestamp deletedOn) {
    GroupMemberAuditEvent event =
        GroupMemberAuditEvent.create(actor, updatedGroup, deletedMembers, deletedOn);
    PluginContext.invokeIgnoreExceptions(groupAuditListeners, l -> l.onDeleteMembers(event));
  }

  @Override
  public void dispatchAddSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> addedSubgroups,
      Timestamp addedOn) {
    GroupSubgroupAuditEvent event =
        GroupSubgroupAuditEvent.create(actor, updatedGroup, addedSubgroups, addedOn);
    PluginContext.invokeIgnoreExceptions(groupAuditListeners, l -> l.onAddSubgroups(event));
  }

  @Override
  public void dispatchDeleteSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> deletedSubgroups,
      Timestamp deletedOn) {
    GroupSubgroupAuditEvent event =
        GroupSubgroupAuditEvent.create(actor, updatedGroup, deletedSubgroups, deletedOn);
    PluginContext.invokeIgnoreExceptions(groupAuditListeners, l -> l.onDeleteSubgroups(event));
  }
}
