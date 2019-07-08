// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Singleton
public class GetAuditLog implements RestReadView<GroupResource> {
  private final Provider<ReviewDb> db;
  private final AccountLoader.Factory accountLoaderFactory;
  private final GroupCache groupCache;
  private final GroupJson groupJson;
  private final GroupBackend groupBackend;

  @Inject
  public GetAuditLog(
      Provider<ReviewDb> db,
      AccountLoader.Factory accountLoaderFactory,
      GroupCache groupCache,
      GroupJson groupJson,
      GroupBackend groupBackend) {
    this.db = db;
    this.accountLoaderFactory = accountLoaderFactory;
    this.groupCache = groupCache;
    this.groupJson = groupJson;
    this.groupBackend = groupBackend;
  }

  @Override
  public List<? extends GroupAuditEventInfo> apply(GroupResource rsrc)
      throws AuthException, ResourceNotFoundException, MethodNotAllowedException, OrmException {
    if (rsrc.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    } else if (!rsrc.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    AccountGroup group = db.get().accountGroups().get(rsrc.toAccountGroup().getId());
    if (group == null) {
      throw new ResourceNotFoundException();
    }

    AccountLoader accountLoader = accountLoaderFactory.create(true);

    List<GroupAuditEventInfo> auditEvents = new ArrayList<>();

    for (AccountGroupMemberAudit auditEvent :
        db.get().accountGroupMembersAudit().byGroup(group.getId()).toList()) {
      AccountInfo member = accountLoader.get(auditEvent.getKey().getParentKey());

      auditEvents.add(
          GroupAuditEventInfo.createAddUserEvent(
              accountLoader.get(auditEvent.getAddedBy()),
              auditEvent.getKey().getAddedOn(),
              member));

      if (!auditEvent.isActive()) {
        auditEvents.add(
            GroupAuditEventInfo.createRemoveUserEvent(
                accountLoader.get(auditEvent.getRemovedBy()), auditEvent.getRemovedOn(), member));
      }
    }

    for (AccountGroupByIdAud auditEvent :
        db.get().accountGroupByIdAud().byGroup(group.getId()).toList()) {
      AccountGroup.UUID includedGroupUUID = auditEvent.getKey().getIncludeUUID();
      AccountGroup includedGroup = groupCache.get(includedGroupUUID);
      GroupInfo member;
      if (includedGroup != null) {
        member = groupJson.format(GroupDescriptions.forAccountGroup(includedGroup));
      } else {
        GroupDescription.Basic groupDescription = groupBackend.get(includedGroupUUID);
        member = new GroupInfo();
        member.id = Url.encode(includedGroupUUID.get());
        if (groupDescription != null) {
          member.name = groupDescription.getName();
        }
      }

      auditEvents.add(
          GroupAuditEventInfo.createAddGroupEvent(
              accountLoader.get(auditEvent.getAddedBy()),
              auditEvent.getKey().getAddedOn(),
              member));

      if (!auditEvent.isActive()) {
        auditEvents.add(
            GroupAuditEventInfo.createRemoveGroupEvent(
                accountLoader.get(auditEvent.getRemovedBy()), auditEvent.getRemovedOn(), member));
      }
    }

    accountLoader.fill();

    // sort by date in reverse order so that the newest audit event comes first
    Collections.sort(
        auditEvents,
        new Comparator<GroupAuditEventInfo>() {
          @Override
          public int compare(GroupAuditEventInfo e1, GroupAuditEventInfo e2) {
            return e2.date.compareTo(e1.date);
          }
        });

    return auditEvents;
  }
}
