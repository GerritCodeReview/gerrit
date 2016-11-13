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

import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.TimeUtil;
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
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.api.accounts.AccountExternalIdCreator;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
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
  private final AccountIndexer indexer;
  private final AccountByEmailCache byEmailCache;
  private final AccountLoader.Factory infoLoader;
  private final DynamicSet<AccountExternalIdCreator> externalIdCreators;
  private final AuditService auditService;
  private final String username;

  @Inject
  CreateAccount(
      ReviewDb db,
      Provider<IdentifiedUser> currentUser,
      GroupsCollection groupsCollection,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      AccountCache accountCache,
      AccountIndexer indexer,
      AccountByEmailCache byEmailCache,
      AccountLoader.Factory infoLoader,
      DynamicSet<AccountExternalIdCreator> externalIdCreators,
      AuditService auditService,
      @Assisted String username) {
    this.db = db;
    this.currentUser = currentUser;
    this.groupsCollection = groupsCollection;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.accountCache = accountCache;
    this.indexer = indexer;
    this.byEmailCache = byEmailCache;
    this.infoLoader = infoLoader;
    this.externalIdCreators = externalIdCreators;
    this.auditService = auditService;
    this.username = username;
  }

  @Override
  public Response<AccountInfo> apply(TopLevelResource rsrc, AccountInput input)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException,
          OrmException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new AccountInput();
    }
    if (input.username != null && !username.equals(input.username)) {
      throw new BadRequestException("username must match URL");
    }

    if (!username.matches(Account.USER_NAME_PATTERN)) {
      throw new BadRequestException(
          "Username '" + username + "'" + " must contain only letters, numbers, _, - or .");
    }

    Set<AccountGroup.Id> groups = parseGroups(input.groups);

    Account.Id id = new Account.Id(db.nextAccountId());

    AccountExternalId extUser =
        new AccountExternalId(
            id, new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, username));

    if (input.httpPassword != null) {
      extUser.setPassword(input.httpPassword);
    }

    if (db.accountExternalIds().get(extUser.getKey()) != null) {
      throw new ResourceConflictException("username '" + username + "' already exists");
    }
    if (input.email != null) {
      if (db.accountExternalIds().get(getEmailKey(input.email)) != null) {
        throw new UnprocessableEntityException("email '" + input.email + "' already exists");
      }
      if (!OutgoingEmailValidator.isValid(input.email)) {
        throw new BadRequestException("invalid email address");
      }
    }

    LinkedList<AccountExternalId> externalIds = new LinkedList<>();
    externalIds.add(extUser);
    for (AccountExternalIdCreator c : externalIdCreators) {
      externalIds.addAll(c.create(id, username, input.email));
    }

    try {
      db.accountExternalIds().insert(externalIds);
    } catch (OrmDuplicateKeyException duplicateKey) {
      throw new ResourceConflictException("username '" + username + "' already exists");
    }

    if (input.email != null) {
      AccountExternalId extMailto = new AccountExternalId(id, getEmailKey(input.email));
      extMailto.setEmailAddress(input.email);
      try {
        db.accountExternalIds().insert(Collections.singleton(extMailto));
      } catch (OrmDuplicateKeyException duplicateKey) {
        try {
          db.accountExternalIds().delete(Collections.singleton(extUser));
        } catch (OrmException cleanupError) {
          // Ignored
        }
        throw new UnprocessableEntityException("email '" + input.email + "' already exists");
      }
    }

    Account a = new Account(id, TimeUtil.nowTs());
    a.setFullName(input.name);
    a.setPreferredEmail(input.email);
    db.accounts().insert(Collections.singleton(a));

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
    indexer.index(id);

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

  private AccountExternalId.Key getEmailKey(String email) {
    return new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
  }
}
