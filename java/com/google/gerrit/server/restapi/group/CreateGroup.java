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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupUuid;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.ZoneId;
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
  private final ZoneId serverZoneId;
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
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
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
    this.serverZoneId = serverIdent.get().getZoneId();
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
  public Response<GroupInfo> apply(TopLevelResource resource, IdString id, GroupInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
          ResourceConflictException, IOException, ConfigInvalidException, ResourceNotFoundException,
          PermissionBackendException {
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
    args.uuid = Strings.isNullOrEmpty(input.uuid) ? null : AccountGroup.UUID.parse(input.uuid);
    if (args.uuid != null) {
      if (!args.uuid.isInternalGroup()) {
        throw new BadRequestException(String.format("invalid group UUID '%s'", args.uuid.get()));
      }
      if (groupCache.get(args.uuid).isPresent()) {
        throw new ResourceConflictException(
            String.format("group with UUID '%s' already exists", args.uuid.get()));
      }
    }
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
        members.add(a.id());
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

    return Response.created(json.format(new InternalGroupDescription(createGroup(args))));
  }

  private AccountGroup.UUID owner(GroupInput input) throws UnprocessableEntityException {
    if (input.ownerId != null) {
      GroupDescription.Internal d = groups.parseInternal(Url.decode(input.ownerId));
      return d.getGroupUUID();
    }
    return null;
  }

  private InternalGroup createGroup(CreateGroupArgs createGroupArgs)
      throws ResourceConflictException, IOException, ConfigInvalidException {

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

    AccountGroup.Id groupId = AccountGroup.id(sequences.nextGroupId());
    AccountGroup.UUID uuid =
        MoreObjects.firstNonNull(
            createGroupArgs.uuid,
            GroupUuid.make(
                createGroupArgs.getGroupName(),
                self.get().newCommitterIdent(TimeUtil.now(), serverZoneId)));
    InternalGroupCreation groupCreation =
        InternalGroupCreation.builder()
            .setGroupUUID(uuid)
            .setNameKey(createGroupArgs.getGroup())
            .setId(groupId)
            .build();
    GroupDelta.Builder groupDeltaBuilder =
        GroupDelta.builder().setVisibleToAll(createGroupArgs.visibleToAll);
    if (createGroupArgs.ownerGroupUuid != null) {
      Optional<InternalGroup> ownerGroup = groupCache.get(createGroupArgs.ownerGroupUuid);
      ownerGroup.map(InternalGroup::getGroupUUID).ifPresent(groupDeltaBuilder::setOwnerGroupUUID);
    }
    if (createGroupArgs.groupDescription != null) {
      groupDeltaBuilder.setDescription(createGroupArgs.groupDescription);
    }
    groupDeltaBuilder.setMemberModification(
        members -> ImmutableSet.copyOf(createGroupArgs.initialMembers));
    try {
      return groupsUpdateProvider.get().createGroup(groupCreation, groupDeltaBuilder.build());
    } catch (DuplicateKeyException e) {
      throw new ResourceConflictException(
          "group '" + createGroupArgs.getGroupName() + "' already exists", e);
    }
  }
}
