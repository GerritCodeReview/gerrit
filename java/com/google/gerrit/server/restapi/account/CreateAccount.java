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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.InvalidSshKeyException;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountExternalIdCreator;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
@Singleton
public class CreateAccount
    implements RestCollectionCreateView<TopLevelResource, AccountResource, AccountInput> {
  private final Sequences seq;
  private final GroupResolver groupResolver;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final AccountLoader.Factory infoLoader;
  private final PluginSetContext<AccountExternalIdCreator> externalIdCreators;
  private final Provider<GroupsUpdate> groupsUpdate;
  private final OutgoingEmailValidator validator;

  @Inject
  CreateAccount(
      Sequences seq,
      GroupResolver groupResolver,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      AccountLoader.Factory infoLoader,
      PluginSetContext<AccountExternalIdCreator> externalIdCreators,
      @UserInitiated Provider<GroupsUpdate> groupsUpdate,
      OutgoingEmailValidator validator) {
    this.seq = seq;
    this.groupResolver = groupResolver;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.infoLoader = infoLoader;
    this.externalIdCreators = externalIdCreators;
    this.groupsUpdate = groupsUpdate;
    this.validator = validator;
  }

  @Override
  public Response<AccountInfo> apply(
      TopLevelResource rsrc, IdString id, @Nullable AccountInput input)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException,
          OrmException, IOException, ConfigInvalidException, PermissionBackendException {
    return apply(id, input != null ? input : new AccountInput());
  }

  public Response<AccountInfo> apply(IdString id, AccountInput input)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException,
          OrmException, IOException, ConfigInvalidException, PermissionBackendException {
    String username = id.get();
    if (input.username != null && !username.equals(input.username)) {
      throw new BadRequestException("username must match URL");
    }
    if (!ExternalId.isValidUsername(username)) {
      throw new BadRequestException("Invalid username '" + username + "'");
    }

    Set<AccountGroup.UUID> groups = parseGroups(input.groups);

    Account.Id accountId = new Account.Id(seq.nextAccountId());
    List<ExternalId> extIds = new ArrayList<>();

    if (input.email != null) {
      if (!validator.isValid(input.email)) {
        throw new BadRequestException("invalid email address");
      }
      extIds.add(ExternalId.createEmail(accountId, input.email));
    }

    extIds.add(ExternalId.createUsername(username, accountId, input.httpPassword));
    externalIdCreators.runEach(c -> extIds.addAll(c.create(accountId, username, input.email)));

    try {
      accountsUpdateProvider
          .get()
          .insert(
              "Create Account via API",
              accountId,
              u -> u.setFullName(input.name).setPreferredEmail(input.email).addExternalIds(extIds));
    } catch (DuplicateExternalIdKeyException e) {
      if (e.getDuplicateKey().isScheme(SCHEME_USERNAME)) {
        throw new ResourceConflictException(
            "username '" + e.getDuplicateKey().id() + "' already exists");
      } else if (e.getDuplicateKey().isScheme(SCHEME_MAILTO)) {
        throw new UnprocessableEntityException(
            "email '" + e.getDuplicateKey().id() + "' already exists");
      } else {
        // AccountExternalIdCreator returned an external ID that already exists
        throw e;
      }
    }

    for (AccountGroup.UUID groupUuid : groups) {
      try {
        addGroupMember(groupUuid, accountId);
      } catch (NoSuchGroupException e) {
        throw new UnprocessableEntityException(String.format("Group %s not found", groupUuid));
      }
    }

    if (input.sshKey != null) {
      try {
        authorizedKeys.addKey(accountId, input.sshKey);
        sshKeyCache.evict(username);
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    AccountLoader loader = infoLoader.create(true);
    AccountInfo info = loader.get(accountId);
    loader.fill();
    return Response.created(info);
  }

  private Set<AccountGroup.UUID> parseGroups(List<String> groups)
      throws UnprocessableEntityException {
    Set<AccountGroup.UUID> groupUuids = new HashSet<>();
    if (groups != null) {
      for (String g : groups) {
        GroupDescription.Internal internalGroup = groupResolver.parseInternal(g);
        groupUuids.add(internalGroup.getGroupUUID());
      }
    }
    return groupUuids;
  }

  private void addGroupMember(AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ImmutableSet.of(accountId)))
            .build();
    groupsUpdate.get().updateGroup(groupUuid, groupUpdate);
  }
}
