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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.group.AddMembers.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class AddMembers implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput String _oneMember;

    List<String> members;

    public static Input fromMembers(List<String> members) {
      Input in = new Input();
      in.members = members;
      return in;
    }

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.members == null) {
        in.members = Lists.newArrayListWithCapacity(1);
      }
      if (!Strings.isNullOrEmpty(in._oneMember)) {
        in.members.add(in._oneMember);
      }
      return in;
    }
  }

  private final Provider<IdentifiedUser> self;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final AccountsCollection accounts;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final AccountLoader.Factory infoFactory;
  private final Provider<ReviewDb> db;
  private final AuditService auditService;

  @Inject
  AddMembers(
      Provider<IdentifiedUser> self,
      AccountManager accountManager,
      AuthConfig authConfig,
      AccountsCollection accounts,
      AccountResolver accountResolver,
      AccountCache accountCache,
      AccountLoader.Factory infoFactory,
      Provider<ReviewDb> db,
      AuditService auditService) {
    this.self = self;
    this.accountManager = accountManager;
    this.auditService = auditService;
    this.authType = authConfig.getAuthType();
    this.accounts = accounts;
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.infoFactory = infoFactory;
    this.db = db;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
          IOException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    GroupControl control = resource.getControl();

    Set<Account.Id> newMemberIds = new HashSet<>();
    for (String nameOrEmailOrId : input.members) {
      Account a = findAccount(nameOrEmailOrId);
      if (!a.isActive()) {
        throw new UnprocessableEntityException(
            String.format("Account Inactive: %s", nameOrEmailOrId));
      }

      if (!control.canAddMember()) {
        throw new AuthException("Cannot add member: " + a.getFullName());
      }
      newMemberIds.add(a.getId());
    }

    addMembers(internalGroup.getId(), newMemberIds);
    return toAccountInfoList(newMemberIds);
  }

  Account findAccount(String nameOrEmailOrId)
      throws AuthException, UnprocessableEntityException, OrmException, IOException {
    try {
      return accounts.parse(nameOrEmailOrId).getAccount();
    } catch (UnprocessableEntityException e) {
      // might be because the account does not exist or because the account is
      // not visible
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          if (accountResolver.find(db.get(), nameOrEmailOrId) == null) {
            // account does not exist, try to create it
            Account a = createAccountByLdap(nameOrEmailOrId);
            if (a != null) {
              return a;
            }
          }
          break;
        case CUSTOM_EXTENSION:
        case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        case HTTP:
        case LDAP_BIND:
        case OAUTH:
        case OPENID:
        case OPENID_SSO:
        default:
      }
      throw e;
    }
  }

  public void addMembers(AccountGroup.Id groupId, Collection<? extends Account.Id> newMemberIds)
      throws OrmException, IOException {
    Map<Account.Id, AccountGroupMember> newAccountGroupMembers = new HashMap<>();
    for (Account.Id accId : newMemberIds) {
      if (!newAccountGroupMembers.containsKey(accId)) {
        AccountGroupMember.Key key = new AccountGroupMember.Key(accId, groupId);
        AccountGroupMember m = db.get().accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          newAccountGroupMembers.put(m.getAccountId(), m);
        }
      }
    }
    if (!newAccountGroupMembers.isEmpty()) {
      auditService.dispatchAddAccountsToGroup(
          self.get().getAccountId(), newAccountGroupMembers.values());
      db.get().accountGroupMembers().insert(newAccountGroupMembers.values());
      for (AccountGroupMember m : newAccountGroupMembers.values()) {
        accountCache.evict(m.getAccountId());
      }
    }
  }

  private Account createAccountByLdap(String user) throws IOException {
    if (!user.matches(Account.USER_NAME_PATTERN)) {
      return null;
    }

    try {
      AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountCache.get(accountManager.authenticate(req).getAccountId()).getAccount();
    } catch (AccountException e) {
      return null;
    }
  }

  private List<AccountInfo> toAccountInfoList(Set<Account.Id> accountIds) throws OrmException {
    List<AccountInfo> result = new LinkedList<>();
    AccountLoader loader = infoFactory.create(true);
    for (Account.Id accId : accountIds) {
      result.add(loader.get(accId));
    }
    loader.fill();
    return result;
  }

  static class PutMember implements RestModifyView<GroupResource, PutMember.Input> {
    static class Input {}

    private final AddMembers put;
    private final String id;

    PutMember(AddMembers put, String id) {
      this.put = put;
      this.id = id;
    }

    @Override
    public AccountInfo apply(GroupResource resource, PutMember.Input input)
        throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException,
            IOException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = id;
      try {
        List<AccountInfo> list = put.apply(resource, in);
        if (list.size() == 1) {
          return list.get(0);
        }
        throw new IllegalStateException();
      } catch (UnprocessableEntityException e) {
        throw new ResourceNotFoundException(id);
      }
    }
  }

  @Singleton
  static class UpdateMember implements RestModifyView<MemberResource, PutMember.Input> {
    private final GetMember get;

    @Inject
    UpdateMember(GetMember get) {
      this.get = get;
    }

    @Override
    public AccountInfo apply(MemberResource resource, PutMember.Input input) throws OrmException {
      // Do nothing, the user is already a member.
      return get.apply(resource);
    }
  }
}
