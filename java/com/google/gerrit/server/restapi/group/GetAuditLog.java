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

package com.google.gerrit.server.restapi.group;

import static java.util.Comparator.comparing;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountGroupByIdAudit;
import com.google.gerrit.entities.AccountGroupMemberAudit;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetAuditLog implements RestReadView<GroupResource> {
  private final AccountLoader.Factory accountLoaderFactory;
  private final AllUsersName allUsers;
  private final GroupCache groupCache;
  private final GroupJson groupJson;
  private final GroupBackend groupBackend;
  private final Groups groups;
  private final GitRepositoryManager repoManager;

  @Inject
  public GetAuditLog(
      AccountLoader.Factory accountLoaderFactory,
      AllUsersName allUsers,
      GroupCache groupCache,
      GroupJson groupJson,
      GroupBackend groupBackend,
      Groups groups,
      GitRepositoryManager repoManager) {
    this.accountLoaderFactory = accountLoaderFactory;
    this.allUsers = allUsers;
    this.groupCache = groupCache;
    this.groupJson = groupJson;
    this.groupBackend = groupBackend;
    this.groups = groups;
    this.repoManager = repoManager;
  }

  @Override
  public Response<List<? extends GroupAuditEventInfo>> apply(GroupResource rsrc)
      throws AuthException, NotInternalGroupException, IOException, ConfigInvalidException,
          PermissionBackendException {
    GroupDescription.Internal group =
        rsrc.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (!rsrc.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    AccountLoader accountLoader = accountLoaderFactory.create(true);

    List<GroupAuditEventInfo> auditEvents = new ArrayList<>();

    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      for (AccountGroupMemberAudit auditEvent :
          groups.getMembersAudit(allUsersRepo, group.getGroupUUID())) {
        AccountInfo member = accountLoader.get(auditEvent.memberId());

        auditEvents.add(
            GroupAuditEventInfo.createAddUserEvent(
                accountLoader.get(auditEvent.addedBy()), auditEvent.addedOn(), member));

        if (!auditEvent.isActive()) {
          auditEvents.add(
              GroupAuditEventInfo.createRemoveUserEvent(
                  accountLoader.get(auditEvent.removedBy().orElse(null)),
                  auditEvent.removedOn(),
                  member));
        }
      }

      for (AccountGroupByIdAudit auditEvent :
          groups.getSubgroupsAudit(allUsersRepo, group.getGroupUUID())) {
        AccountGroup.UUID includedGroupUUID = auditEvent.includeUuid();
        Optional<InternalGroup> includedGroup = groupCache.get(includedGroupUUID);
        GroupInfo member;
        if (includedGroup.isPresent()) {
          member = groupJson.format(new InternalGroupDescription(includedGroup.get()));
        } else {
          member = new GroupInfo();
          member.id = Url.encode(includedGroupUUID.get());
          GroupDescription.Basic groupDescription = groupBackend.get(includedGroupUUID);
          if (groupDescription != null) {
            member.name = groupDescription.getName();
          }
        }

        auditEvents.add(
            GroupAuditEventInfo.createAddGroupEvent(
                accountLoader.get(auditEvent.addedBy()), auditEvent.addedOn(), member));

        if (!auditEvent.isActive()) {
          auditEvents.add(
              GroupAuditEventInfo.createRemoveGroupEvent(
                  accountLoader.get(auditEvent.removedBy().orElse(null)),
                  auditEvent.removedOn(),
                  member));
        }
      }
    }

    accountLoader.fill();

    // sort by date and then reverse so that the newest audit event comes first
    auditEvents.sort(comparing((GroupAuditEventInfo a) -> a.date).reversed());
    return Response.ok(auditEvents);
  }
}
