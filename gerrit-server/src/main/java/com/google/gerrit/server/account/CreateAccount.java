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

import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.InvalidSshKeyException;
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
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.api.accounts.AccountExternalIdCreator;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
  private final Provider<IdentifiedUser> currentUser;
  private final GroupsCollection groupsCollection;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final AccountCache accountCache;
  private final AccountsUpdate.User accountsUpdate;
  private final AccountByEmailCache byEmailCache;
  private final AccountLoader.Factory infoLoader;
  private final DynamicSet<AccountExternalIdCreator> externalIdCreators;
  private final AuditService auditService;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.User externalIdsUpdateFactory;
  private final OutgoingEmailValidator validator;
  private final String username;

  @Inject
  CreateAccount(
      ReviewDb db,
      Provider<IdentifiedUser> currentUser,
      GroupsCollection groupsCollection,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      AccountCache accountCache,
      AccountsUpdate.User accountsUpdate,
      AccountByEmailCache byEmailCache,
      AccountLoader.Factory infoLoader,
      DynamicSet<AccountExternalIdCreator> externalIdCreators,
      AuditService auditService,
      ExternalIds externalIds,
      ExternalIdsUpdate.User externalIdsUpdateFactory,
      OutgoingEmailValidator validator,
      @Assisted String username) {
    this.db = db;
    this.currentUser = currentUser;
    this.groupsCollection = groupsCollection;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.accountCache = accountCache;
    this.accountsUpdate = accountsUpdate;
    this.byEmailCache = byEmailCache;
    this.infoLoader = infoLoader;
    this.externalIdCreators = externalIdCreators;
    this.auditService = auditService;
    this.externalIds = externalIds;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
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

    if (!username.matches(Account.USER_NAME_PATTERN)) {
      throw new BadRequestException(
          "Username '" + username + "' must contain only letters, numbers, _, - or .");
    }

    Set<AccountGroup.Id> groups = parseGroups(input.groups);

    Account.Id id = new Account.Id(db.nextAccountId());

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
            db,
            id,
            a -> {
              a.setFullName(input.name);
              a.setPreferredEmail(input.email);
            });

    for (AccountGroup.Id groupId : groups) {
      AccountGroupMember m = new AccountGroupMember(new AccountGroupMember.Key(id, groupId));
      auditService.dispatchAddAccountsToGroup(
          currentUser.get().getAccountId(), Collections.singleton(m));
      db.accountGroupMembers().insert(Collections.singleton(m));
    }

    if (input.sshKey != null) {
      try {
        authorizedKeys.addKey(id, input.sshKey);
        sshKeyCache.evict(username);
      } catch (InvalidSshKeyException e) {
        throw new BadRequestException(e.getMessage());
      }
    }

    accountCache.evictByUsername(username);
    byEmailCache.evict(input.email);

    AccountLoader loader = infoLoader.create(true);
    AccountInfo info = loader.get(id);
    loader.fill();
    return Response.created(info);
  }

  private Set<AccountGroup.Id> parseGroups(List<String> groups)
      throws UnprocessableEntityException {
    Set<AccountGroup.Id> groupIds = new HashSet<>();
    if (groups != null) {
      for (String g : groups) {
        groupIds.add(GroupDescriptions.toAccountGroup(groupsCollection.parseInternal(g)).getId());
      }
    }
    return groupIds;
  }
}
