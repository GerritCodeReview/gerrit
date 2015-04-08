// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.CreateGroup.Input;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RequiresCapability(GlobalCapability.CREATE_GROUP)
public class CreateGroup implements RestModifyView<TopLevelResource, Input> {
  public static class Input {
    public String name;
    public String description;
    public Boolean visibleToAll;
    public String ownerId;
  }

  public static interface Factory {
    CreateGroup create(@Assisted String name);
  }

  private final Provider<IdentifiedUser> self;
  private final PersonIdent serverIdent;
  private final ReviewDb db;
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final GroupsCollection groups;
  private final GroupJson json;
  private final AuditService auditService;
  private final DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners;
  private final boolean defaultVisibleToAll;
  private final String name;

  @Inject
  CreateGroup(
      Provider<IdentifiedUser> self,
      @GerritPersonIdent PersonIdent serverIdent,
      ReviewDb db,
      AccountCache accountCache,
      GroupCache groupCache,
      GroupsCollection groups,
      GroupJson json,
      AuditService auditService,
      DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners,
      @GerritServerConfig Config cfg,
      @Assisted String name) {
    this.self = self;
    this.serverIdent = serverIdent;
    this.db = db;
    this.accountCache = accountCache;
    this.groupCache = groupCache;
    this.groups = groups;
    this.json = json;
    this.auditService = auditService;
    this.groupCreationValidationListeners = groupCreationValidationListeners;
    this.defaultVisibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    this.name = name;
  }

  @Override
  public GroupInfo apply(TopLevelResource resource, Input input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      ResourceConflictException, OrmException {
    if (input == null) {
      input = new Input();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    AccountGroup.Id ownerId = owner(input);
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name);
    args.groupDescription = Strings.emptyToNull(input.description);
    args.visibleToAll = MoreObjects.firstNonNull(input.visibleToAll,
        defaultVisibleToAll);
    args.ownerGroupId = ownerId;
    args.initialMembers = ownerId == null
        ? Collections.singleton(self.get().getAccountId())
        : Collections.<Account.Id> emptySet();

    for (GroupCreationValidationListener l : groupCreationValidationListeners) {
      try {
        l.validateNewGroup(args);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }

    return json.format(GroupDescriptions.forAccountGroup(createGroup(args)));
  }

  private AccountGroup.Id owner(Input input)
      throws UnprocessableEntityException {
    if (input.ownerId != null) {
      GroupDescription.Basic d = groups.parseInternal(Url.decode(input.ownerId));
      return GroupDescriptions.toAccountGroup(d).getId();
    }
    return null;
  }

  private AccountGroup createGroup(CreateGroupArgs createGroupArgs)
      throws OrmException, ResourceConflictException {
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.UUID uuid =
        GroupUUID.make(
            createGroupArgs.getGroupName(),
            self.get().newCommitterIdent(serverIdent.getWhen(),
                serverIdent.getTimeZone()));
    AccountGroup group =
        new AccountGroup(createGroupArgs.getGroup(), groupId, uuid);
    group.setVisibleToAll(createGroupArgs.visibleToAll);
    if (createGroupArgs.ownerGroupId != null) {
      AccountGroup ownerGroup = groupCache.get(createGroupArgs.ownerGroupId);
      if (ownerGroup != null) {
        group.setOwnerGroupUUID(ownerGroup.getGroupUUID());
      }
    }
    if (createGroupArgs.groupDescription != null) {
      group.setDescription(createGroupArgs.groupDescription);
    }
    AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw new ResourceConflictException("group '"
          + createGroupArgs.getGroupName() + "' already exists");
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, createGroupArgs.initialMembers);

    groupCache.onCreateGroup(createGroupArgs.getGroup());

    return group;
  }

  private void addMembers(AccountGroup.Id groupId,
      Collection<? extends Account.Id> members) throws OrmException {
    List<AccountGroupMember> memberships = new ArrayList<>();
    for (Account.Id accountId : members) {
      AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);
    }
    db.accountGroupMembers().insert(memberships);
    auditService.dispatchAddAccountsToGroup(self.get().getAccountId(),
        memberships);

    for (Account.Id accountId : members) {
      accountCache.evict(accountId);
    }
  }
}
