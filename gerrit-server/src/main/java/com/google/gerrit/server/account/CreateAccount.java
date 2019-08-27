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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.api.accounts.AccountExternalIdCreator;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.group.GroupsUpdate;
import com.google.gerrit.server.group.UserInitiated;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
public class CreateAccount implements RestModifyView<TopLevelResource, AccountInput> {
  public interface Factory {
    CreateAccount create(String username);
  }

  private final ReviewDb db;
  private final Sequences seq;
  private final GroupsCollection groupsCollection;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final AccountsUpdate.User accountsUpdate;
  private final AccountLoader.Factory infoLoader;
  private final DynamicSet<AccountExternalIdCreator> externalIdCreators;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.User externalIdsUpdateFactory;
  private final Provider<GroupsUpdate> groupsUpdate;
  private final OutgoingEmailValidator validator;
  private final String username;

  @Inject
  CreateAccount(
      ReviewDb db,
      Sequences seq,
      GroupsCollection groupsCollection,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      AccountsUpdate.User accountsUpdate,
      AccountLoader.Factory infoLoader,
      DynamicSet<AccountExternalIdCreator> externalIdCreators,
      ExternalIds externalIds,
      ExternalIdsUpdate.User externalIdsUpdateFactory,
      @UserInitiated Provider<GroupsUpdate> groupsUpdate,
      OutgoingEmailValidator validator,
      @Assisted String username) {
    this.db = db;
    this.seq = seq;
    this.groupsCollection = groupsCollection;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.accountsUpdate = accountsUpdate;
    this.infoLoader = infoLoader;
    this.externalIdCreators = externalIdCreators;
    this.externalIds = externalIds;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
    this.groupsUpdate = groupsUpdate;
    this.validator = validator;
    this.username = username;
  }

  @Override
  public Response<AccountInfo> apply(TopLevelResource rsrc, @Nullable AccountInput input)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException,
          OrmException, IOException, ConfigInvalidException {
    return apply(input != null ? input : new AccountInput());
  }

  public Response<AccountInfo> apply(AccountInput input)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException,
          OrmException, IOException, ConfigInvalidException {
    if (input.username != null && !username.equals(input.username)) {
      throw new BadRequestException("username must match URL");
    }

    if (!ExternalId.isValidUsername(username)) {
      throw new BadRequestException("Invalid username '" + username + "'");
    }

    Set<AccountGroup.UUID> groups = parseGroups(input.groups);

    Account.Id id = new Account.Id(seq.nextAccountId());

    ExternalId extUser = ExternalId.createUsername(username, id, input.httpPassword);
    if (externalIds.get(extUser.key()) != null) {
      throw new ResourceConflictException("username '" + username + "' already exists");
    }
    if (input.email != null) {
      if (externalIds.get(ExternalId.Key.create(SCHEME_MAILTO, input.email)) != null) {
        throw new UnprocessableEntityException("email '" + input.email + "' already exists");
      }
      if (!validator.isValid(input.email)) {
        throw new BadRequestException("invalid email address");
      }
    }

    List<ExternalId> extIds = new ArrayList<>();
    extIds.add(extUser);
    for (AccountExternalIdCreator c : externalIdCreators) {
      extIds.addAll(c.create(id, username, input.email));
    }

    ExternalIdsUpdate externalIdsUpdate = externalIdsUpdateFactory.create();
    try {
      externalIdsUpdate.insert(extIds);
    } catch (OrmDuplicateKeyException duplicateKey) {
      throw new ResourceConflictException("username '" + username + "' already exists");
    }

    if (input.email != null) {
      try {
        externalIdsUpdate.insert(ExternalId.createEmail(id, input.email));
      } catch (OrmDuplicateKeyException duplicateKey) {
        try {
          externalIdsUpdate.delete(extUser);
        } catch (IOException | ConfigInvalidException cleanupError) {
          // Ignored
        }
        throw new UnprocessableEntityException("email '" + input.email + "' already exists");
      }
    }

    accountsUpdate
        .create()
        .insert(
            id,
            a -> {
              a.setFullName(input.name);
              a.setPreferredEmail(input.email);
            });

    for (AccountGroup.UUID groupUuid : groups) {
      try {
        groupsUpdate.get().addGroupMember(db, groupUuid, id);
      } catch (NoSuchGroupException e) {
        throw new UnprocessableEntityException(String.format("Group %s not found", groupUuid));
      }
    }

    if (input.sshKey != null) {
      try {
        authorizedKeys.addKey(id, input.sshKey);
        sshKeyCache.evict(username);
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    AccountLoader loader = infoLoader.create(true);
    AccountInfo info = loader.get(id);
    loader.fill();
    return Response.created(info);
  }

  private Set<AccountGroup.UUID> parseGroups(List<String> groups)
      throws UnprocessableEntityException {
    Set<AccountGroup.UUID> groupUuids = new HashSet<>();
    if (groups != null) {
      for (String g : groups) {
        GroupDescription.Internal internalGroup = groupsCollection.parseInternal(g);
        groupUuids.add(internalGroup.getGroupUUID());
      }
    }
    return groupUuids;
  }
}
