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

package com.google.gerrit.audit;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Singleton
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final DynamicSet<AuditListener> auditListeners;
  private final DynamicSet<GroupMemberAuditListener> groupMemberAuditListeners;

  @Inject
  public AuditService(DynamicSet<AuditListener> auditListeners,
      DynamicSet<GroupMemberAuditListener> groupMemberAuditListeners) {
    this.auditListeners = auditListeners;
    this.groupMemberAuditListeners = groupMemberAuditListeners;
  }

  public void dispatch(AuditEvent action) {
    for (AuditListener auditListener : auditListeners) {
      auditListener.onAuditableAction(action);
    }
  }

  public void dispatchAddGroupMembers(Account.Id actor, Collection<AccountGroupMember> added) {
    for (GroupMemberAuditListener auditListener : groupMemberAuditListeners) {
      try {
        auditListener.onAddMembers(actor, added);
      } catch (Exception e) {
        log.error("failed to log add group member event", e);
      }
    }
  }

  public void dispatchDeleteGroupMembers(Account.Id actor,
      Collection<AccountGroupMember> removed) {
    for (GroupMemberAuditListener auditListener : groupMemberAuditListeners) {
      try {
        auditListener.onDeleteMembers(actor, removed);
      } catch (Exception e) {
        log.error("failed to log delete group member event", e);
      }
    }
  }
}
