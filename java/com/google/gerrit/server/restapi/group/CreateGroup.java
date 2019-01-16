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

package com.google.gerrit.server.restapi.group;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

@RequiresCapability(GlobalCapability.CREATE_GROUP)
@Singleton
public class CreateGroup
    implements RestCollectionCreateView<TopLevelResource, GroupResource, GroupInput> {
  private final Provider<IdentifiedUser> self;
  private final PersonIdent serverIdent;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupCache groupCache;
  private final GroupResolver groups;
  private final GroupJson json;
  private final PluginSetContext<GroupCreationValidationListener> groupCreationValidationListeners;
  private final AddMembers addMembers;
  private final SystemGroupBackend systemGroupBackend;
  private final boolean defaultVisibleToAll;
  private final Sequences sequences;

  @Inject
  CreateGroup(
      Provider<IdentifiedUser> self,
      @GerritPersonIdent PersonIdent serverIdent,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      GroupCache groupCache,
      GroupResolver groups,
      GroupJson json,
      PluginSetContext<GroupCreationValidationListener> groupCreationValidationListeners,
      AddMembers addMembers,
      SystemGroupBackend systemGroupBackend,
      @GerritServerConfig Config cfg,
      Sequences sequences) {
    this.self = self;
    this.serverIdent = serverIdent;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.groupCache = groupCache;
    this.groups = groups;
    this.json = json;
    this.groupCreationValidationListeners = groupCreationValidationListeners;
    this.addMembers = addMembers;
    this.systemGroupBackend = systemGroupBackend;
    this.defaultVisibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    this.sequences = sequences;
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
  public GroupInfo apply(TopLevelResource resource, IdString id, GroupInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
          ResourceConflictException, StorageException, IOException, ConfigInvalidException,
          ResourceNotFoundException, PermissionBackendException {
    String name = id.get();
    if (input == null) {
      input = new GroupInput();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    AccountGroup.UUID ownerUuid = owner(input);
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name);
    args.groupDescription = Strings.emptyToNull(input.description);
    args.visibleToAll = MoreObjects.firstNonNull(input.visibleToAll, defaultVisibleToAll);
    args.ownerGroupUuid = ownerUuid;
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
          ownerUuid == null
              ? Collections.singleton(self.get().getAccountId())
              : Collections.emptySet();
    }

    try {
      groupCreationValidationListeners.runEach(
          l -> l.validateNewGroup(args), ValidationException.class);
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }

    return json.format(new InternalGroupDescription(createGroup(args)));
  }

  private AccountGroup.UUID owner(GroupInput input) throws UnprocessableEntityException {
    if (input.ownerId != null) {
      GroupDescription.Internal d = groups.parseInternal(Url.decode(input.ownerId));
      return d.getGroupUUID();
    }
    return null;
  }

  private InternalGroup createGroup(CreateGroupArgs createGroupArgs)
      throws StorageException, ResourceConflictException, IOException, ConfigInvalidException {

    String nameLower = createGroupArgs.getGroupName().toLowerCase(Locale.US);

    for (String name : systemGroupBackend.getNames()) {
      if (name.toLowerCase(Locale.US).equals(nameLower)) {
        throw new ResourceConflictException("group '" + name + "' already exists");
      }
    }

    for (String name : systemGroupBackend.getReservedNames()) {
      if (name.toLowerCase(Locale.US).equals(nameLower)) {
        throw new ResourceConflictException("group name '" + name + "' is reserved");
      }
    }

    AccountGroup.Id groupId = new AccountGroup.Id(sequences.nextGroupId());
    AccountGroup.UUID uuid =
        GroupUUID.make(
            createGroupArgs.getGroupName(),
            self.get().newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone()));
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(uuid)
            .setNameKey(createGroupArgs.getGroup())
            .setId(groupId)
            .build();
    InternalGroupUpdate.Builder groupUpdateBuilder =
        InternalGroupUpdate.builder().setVisibleToAll(createGroupArgs.visibleToAll);
    if (createGroupArgs.ownerGroupUuid != null) {
      Optional<InternalGroup> ownerGroup = groupCache.get(createGroupArgs.ownerGroupUuid);
      ownerGroup.map(InternalGroup::getGroupUUID).ifPresent(groupUpdateBuilder::setOwnerGroupUUID);
    }
    if (createGroupArgs.groupDescription != null) {
      groupUpdateBuilder.setDescription(createGroupArgs.groupDescription);
    }
    groupUpdateBuilder.setMemberModification(
        members -> ImmutableSet.copyOf(createGroupArgs.initialMembers));
    try {
      return groupsUpdateProvider.get().createGroup(groupCreation, groupUpdateBuilder.build());
    } catch (DuplicateKeyException e) {
      throw new ResourceConflictException(
          "group '" + createGroupArgs.getGroupName() + "' already exists");
    }
  }
}
