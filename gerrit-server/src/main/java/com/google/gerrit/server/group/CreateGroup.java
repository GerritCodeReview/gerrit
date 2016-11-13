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
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
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
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

@RequiresCapability(GlobalCapability.CREATE_GROUP)
public class CreateGroup implements RestModifyView<TopLevelResource, GroupInput> {
  public interface Factory {
    CreateGroup create(@Assisted String name);
  }

  private final Provider<IdentifiedUser> self;
  private final PersonIdent serverIdent;
  private final ReviewDb db;
  private final GroupCache groupCache;
  private final GroupsCollection groups;
  private final GroupJson json;
  private final DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners;
  private final AddMembers addMembers;
  private final boolean defaultVisibleToAll;
  private final String name;

  @Inject
  CreateGroup(
      Provider<IdentifiedUser> self,
      @GerritPersonIdent PersonIdent serverIdent,
      ReviewDb db,
      GroupCache groupCache,
      GroupsCollection groups,
      GroupJson json,
      DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners,
      AddMembers addMembers,
      @GerritServerConfig Config cfg,
      @Assisted String name) {
    this.self = self;
    this.serverIdent = serverIdent;
    this.db = db;
    this.groupCache = groupCache;
    this.groups = groups;
    this.json = json;
    this.groupCreationValidationListeners = groupCreationValidationListeners;
    this.addMembers = addMembers;
    this.defaultVisibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    this.name = name;
  }

  public CreateGroup addOption(ListGroupsOption o) {
    json.addOption(o);
    return this;
  }

  public CreateGroup addOptions(Collection<ListGroupsOption> o) {
    json.addOptions(o);
    return this;
  }

  @Override
  public GroupInfo apply(TopLevelResource resource, GroupInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
          ResourceConflictException, OrmException, IOException {
    if (input == null) {
      input = new GroupInput();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    AccountGroup.Id ownerId = owner(input);
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name);
    args.groupDescription = Strings.emptyToNull(input.description);
    args.visibleToAll = MoreObjects.firstNonNull(input.visibleToAll, defaultVisibleToAll);
    args.ownerGroupId = ownerId;
    if (input.members != null && !input.members.isEmpty()) {
      List<Account.Id> members = new ArrayList<>();
      for (String nameOrEmailOrId : input.members) {
        Account a = addMembers.findAccount(nameOrEmailOrId);
        if (!a.isActive()) {
          throw new UnprocessableEntityException(
              String.format("Account Inactive: %s", nameOrEmailOrId));
        }
        members.add(a.getId());
      }
      args.initialMembers = members;
    } else {
      args.initialMembers =
          ownerId == null
              ? Collections.singleton(self.get().getAccountId())
              : Collections.<Account.Id>emptySet();
    }

    for (GroupCreationValidationListener l : groupCreationValidationListeners) {
      try {
        l.validateNewGroup(args);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }

    return json.format(GroupDescriptions.forAccountGroup(createGroup(args)));
  }

  private AccountGroup.Id owner(GroupInput input) throws UnprocessableEntityException {
    if (input.ownerId != null) {
      GroupDescription.Basic d = groups.parseInternal(Url.decode(input.ownerId));
      return GroupDescriptions.toAccountGroup(d).getId();
    }
    return null;
  }

  private AccountGroup createGroup(CreateGroupArgs createGroupArgs)
      throws OrmException, ResourceConflictException, IOException {

    // Do not allow creating groups with the same name as system groups
    List<String> sysGroupNames = SystemGroupBackend.getNames();
    for (String name : sysGroupNames) {
      if (name.toLowerCase(Locale.US)
          .equals(createGroupArgs.getGroupName().toLowerCase(Locale.US))) {
        throw new ResourceConflictException("group '" + name + "' already exists");
      }
    }

    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.UUID uuid =
        GroupUUID.make(
            createGroupArgs.getGroupName(),
            self.get().newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone()));
    AccountGroup group = new AccountGroup(createGroupArgs.getGroup(), groupId, uuid);
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
      throw new ResourceConflictException(
          "group '" + createGroupArgs.getGroupName() + "' already exists");
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers.addMembers(groupId, createGroupArgs.initialMembers);

    groupCache.onCreateGroup(createGroupArgs.getGroup());

    return group;
  }
}
