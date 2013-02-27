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
import com.google.common.collect.Maps;
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.BadRequestHandler;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountInfo;
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

import java.util.List;
import java.util.Map;

class AddMembers implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String _oneMember;

    List<String> members;

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

  private final AccountManager accountManager;
  private final AuthType authType;
  private final Provider<AccountsCollection> accounts;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final ReviewDb db;

  @Inject
  AddMembers(AccountManager accountManager,
      AuthConfig authConfig,
      Provider<AccountsCollection> accounts,
      AccountResolver accountResolver,
      AccountCache accountCache,
      ReviewDb db) {
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
    this.accounts = accounts;
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.db = db;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, BadRequestException,
      OrmException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    GroupControl control = resource.getControl();
    Map<Account.Id, AccountGroupMember> newAccountGroupMembers = Maps.newHashMap();
    List<AccountGroupMemberAudit> newAccountGroupMemberAudits = Lists.newLinkedList();
    BadRequestHandler badRequest = new BadRequestHandler("adding new group members");
    List<AccountInfo> result = Lists.newLinkedList();
    Account.Id me = ((IdentifiedUser) control.getCurrentUser()).getAccountId();

    for (String nameOrEmail : input.members) {
      Account a = findAccount(nameOrEmail);
      if (a == null) {
        badRequest.addError(new NoSuchAccountException(nameOrEmail));
        continue;
      }

      if (!a.isActive()) {
        badRequest.addError(new InactiveAccountException(a.getFullName()));
        continue;
      }

      if (!control.canAddMember(a.getId())) {
        throw new AuthException("Cannot add member: " + a.getFullName());
      }

      if (!newAccountGroupMembers.containsKey(a.getId())) {
        AccountGroupMember.Key key =
            new AccountGroupMember.Key(a.getId(), internalGroup.getId());
        AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          newAccountGroupMembers.put(m.getAccountId(), m);
          newAccountGroupMemberAudits.add(new AccountGroupMemberAudit(m, me));
        }
      }
      result.add(AccountInfo.parse(a, true));
    }

    badRequest.failOnError();

    db.accountGroupMembersAudit().insert(newAccountGroupMemberAudits);
    db.accountGroupMembers().insert(newAccountGroupMembers.values());
    for (AccountGroupMember m : newAccountGroupMembers.values()) {
      accountCache.evict(m.getAccountId());
    }

    return result;
  }

  private Account findAccount(String nameOrEmail) throws OrmException {
    try {
      return accounts.get().parse(nameOrEmail).getAccount();
    } catch (AuthException e) {
      return null;
    } catch (ResourceNotFoundException e) {
      // might be because the account does not exist or because the account is not visible
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          if (accountResolver.find(nameOrEmail) == null) {
            // account does not exist, try to create it
            return createAccountByLdap(nameOrEmail);
          }
          break;
        default:
      }
      return null;
    }
  }

  private Account createAccountByLdap(String user) {
    if (!user.matches(Account.USER_NAME_PATTERN)) {
      return null;
    }

    try {
      AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountCache.get(accountManager.authenticate(req).getAccountId())
          .getAccount();
    } catch (AccountException e) {
      return null;
    }
  }

  static class PutMember implements RestModifyView<GroupResource, PutMember.Input> {
    static class Input {
    }

    private final Provider<AddMembers> put;
    private final String id;

    PutMember(Provider<AddMembers> put, String id) {
      this.put = put;
      this.id = id;
    }

    @Override
    public Object apply(GroupResource resource, PutMember.Input input)
        throws AuthException, MethodNotAllowedException, BadRequestException,
        OrmException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = id;
      List<AccountInfo> list = put.get().apply(resource, in);
      if (list.size() == 1) {
        return list.get(0);
      }
      throw new IllegalStateException();
    }
  }

  static class UpdateMember implements RestModifyView<MemberResource, PutMember.Input> {
    static class Input {
    }

    private final Provider<GetMember> get;

    @Inject
    UpdateMember(Provider<GetMember> get) {
      this.get = get;
    }

    @Override
    public Object apply(MemberResource resource, PutMember.Input input) {
      // Do nothing, the user is already a member.
      return get.get().apply(resource);
    }
  }
}
