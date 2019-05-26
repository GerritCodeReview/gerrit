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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.AuditEvent;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.audit.group.GroupMemberAuditEvent;
import com.google.gerrit.server.audit.group.GroupSubgroupAuditEvent;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;

@Singleton
public class AuditService implements GroupAuditService {
  private final PluginSetContext<AuditListener> auditListeners;
  private final PluginSetContext<GroupAuditListener> groupAuditListeners;

  @Inject
  public AuditService(
      PluginSetContext<AuditListener> auditListeners,
      PluginSetContext<GroupAuditListener> groupAuditListeners) {
    this.auditListeners = auditListeners;
    this.groupAuditListeners = groupAuditListeners;
  }

  @Override
  public void dispatch(AuditEvent action) {
    auditListeners.runEach(l -> l.onAuditableAction(action));
  }

  @Override
  public void dispatchAddMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> addedMembers,
      Timestamp addedOn) {
    GroupMemberAuditEvent event =
        GroupMemberAuditEvent.create(actor, updatedGroup, addedMembers, addedOn);
    groupAuditListeners.runEach(l -> l.onAddMembers(event));
  }

  @Override
  public void dispatchDeleteMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> deletedMembers,
      Timestamp deletedOn) {
    GroupMemberAuditEvent event =
        GroupMemberAuditEvent.create(actor, updatedGroup, deletedMembers, deletedOn);
    groupAuditListeners.runEach(l -> l.onDeleteMembers(event));
  }

  @Override
  public void dispatchAddSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> addedSubgroups,
      Timestamp addedOn) {
    GroupSubgroupAuditEvent event =
        GroupSubgroupAuditEvent.create(actor, updatedGroup, addedSubgroups, addedOn);
    groupAuditListeners.runEach(l -> l.onAddSubgroups(event));
  }

  @Override
  public void dispatchDeleteSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> deletedSubgroups,
      Timestamp deletedOn) {
    GroupSubgroupAuditEvent event =
        GroupSubgroupAuditEvent.create(actor, updatedGroup, deletedSubgroups, deletedOn);
    groupAuditListeners.runEach(l -> l.onDeleteSubgroups(event));
  }
}
