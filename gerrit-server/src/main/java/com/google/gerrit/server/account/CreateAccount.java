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

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CreateAccount.Input;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
public class CreateAccount implements RestModifyView<TopLevelResource, Input> {
  public static class Input {
    @DefaultInput
    public String username;
    public String name;
    public String email;
    public String sshKey;
    public String httpPassword;
    public List<String> groups;
  }

  public static interface Factory {
    CreateAccount create(String username);
  }

  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final GroupsCollection groupsCollection;
  private final SshKeyCache sshKeyCache;
  private final AccountCache accountCache;
  private final AccountByEmailCache byEmailCache;
  private final String username;

  @Inject
  CreateAccount(ReviewDb db, IdentifiedUser currentUser,
      GroupsCollection groupsCollection, SshKeyCache sshKeyCache,
      AccountCache accountCache, AccountByEmailCache byEmailCache,
      @Assisted String username) {
    this.db = db;
    this.currentUser = currentUser;
    this.groupsCollection = groupsCollection;
    this.sshKeyCache = sshKeyCache;
    this.accountCache = accountCache;
    this.byEmailCache = byEmailCache;
    this.username = username;
  }

  @Override
  public Object apply(TopLevelResource rsrc, Input input)
      throws BadRequestException, ResourceConflictException,
      UnprocessableEntityException, OrmException {
    if (input == null) {
      input = new Input();
    }
    if (input.username != null && !username.equals(input.username)) {
      throw new BadRequestException("username must match URL");
    }

    if (!username.matches(Account.USER_NAME_PATTERN)) {
      throw new BadRequestException("Username '" + username + "'"
          + " must contain only letters, numbers, _, - or .");
    }

    Set<AccountGroup.Id> groups = parseGroups(input.groups);

    Account.Id id = new Account.Id(db.nextAccountId());
    AccountSshKey key = createSshKey(id, input.sshKey);

    AccountExternalId extUser =
        new AccountExternalId(id, new AccountExternalId.Key(
            AccountExternalId.SCHEME_USERNAME, username));

    if (input.httpPassword != null) {
      extUser.setPassword(input.httpPassword);
    }

    if (db.accountExternalIds().get(extUser.getKey()) != null) {
      throw new ResourceConflictException(
          "username '" + username + "' already exists");
    }
    if (input.email != null
        && db.accountExternalIds().get(getEmailKey(input.email)) != null) {
      throw new UnprocessableEntityException(
          "email '" + input.email + "' already exists");
    }

    try {
      db.accountExternalIds().insert(Collections.singleton(extUser));
    } catch (OrmDuplicateKeyException duplicateKey) {
      throw new ResourceConflictException(
          "username '" + username + "' already exists");
    }

    if (input.email != null) {
      AccountExternalId extMailto =
          new AccountExternalId(id, getEmailKey(input.email));
      extMailto.setEmailAddress(input.email);
      try {
        db.accountExternalIds().insert(Collections.singleton(extMailto));
      } catch (OrmDuplicateKeyException duplicateKey) {
        try {
          db.accountExternalIds().delete(Collections.singleton(extUser));
        } catch (OrmException cleanupError) {
        }
        throw new UnprocessableEntityException(
            "email '" + input.email + "' already exists");
      }
    }

    Account a = new Account(id);
    a.setFullName(input.name);
    a.setPreferredEmail(input.email);
    db.accounts().insert(Collections.singleton(a));

    if (key != null) {
      db.accountSshKeys().insert(Collections.singleton(key));
    }

    for (AccountGroup.Id groupId : groups) {
      AccountGroupMember m =
          new AccountGroupMember(new AccountGroupMember.Key(id, groupId));
      db.accountGroupMembersAudit().insert(Collections.singleton(
          new AccountGroupMemberAudit(m, currentUser.getAccountId())));
      db.accountGroupMembers().insert(Collections.singleton(m));
    }

    sshKeyCache.evict(username);
    accountCache.evictByUsername(username);
    byEmailCache.evict(input.email);

    return Response.created(AccountInfo.parse(a, true));
  }

  private Set<AccountGroup.Id> parseGroups(List<String> groups)
      throws UnprocessableEntityException {
    Set<AccountGroup.Id> groupIds = Sets.newHashSet();
    if (groups != null) {
      for (String g : groups) {
        groupIds.add(GroupDescriptions.toAccountGroup(
            groupsCollection.parseInternal(g)).getId());
      }
    }
    return groupIds;
  }

  private AccountSshKey createSshKey(Account.Id id, String sshKey)
      throws BadRequestException {
    if (sshKey == null) {
      return null;
    }
    try {
      return sshKeyCache.create(new AccountSshKey.Id(id, 1), sshKey.trim());
    } catch (InvalidSshKeyException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private AccountExternalId.Key getEmailKey(String email) {
    return new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
  }
}
