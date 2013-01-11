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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.group.MembersCollection.MemberInfo;
import com.google.gerrit.server.group.PutMember.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

class PutMember implements RestModifyView<GroupResource, Input> {
  static class Input {
    List<String> members;

    @DefaultInput
    String _oneMember;
  }

  private final GroupControl.Factory groupControlFactory;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  PutMember(final GroupControl.Factory groupControlFactory,
      final AccountManager accountManager,
      final AuthConfig authConfig,
      final AccountResolver accountResolver,
      final AccountCache accountCache, final ReviewDb db,
      final Provider<CurrentUser> self) {
    this.groupControlFactory = groupControlFactory;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.db = db;
    this.self = self;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public List<MemberInfo> apply(GroupResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    if (input == null) {
      input = new Input();
    }

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    final GroupControl control = groupControlFactory.controlFor(internalGroup);
    final List<AccountGroupMember> newAccountGroupMembers = Lists.newLinkedList();
    final List<AccountGroupMemberAudit> newAccountGroupMemberAudits = Lists.newLinkedList();
    final List<String> errors = Lists.newLinkedList();
    final List<MemberInfo> newMembers = Lists.newLinkedList();
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();

    for (final String nameOrEmail : input.members) {
      final Account a;
      try {
        a = findAccount(nameOrEmail);
      } catch (NoSuchAccountException e) {
        errors.add(e.getMessage());
        continue;
      }

      if (!a.isActive()) {
        errors.add((new InactiveAccountException(a.getFullName())).getMessage());
        continue;
      }

      if (!control.canAddMember(a.getId())) {
        throw new AuthException("Cannot add member: " + a.getFullName());
      }

      final AccountGroupMember.Key key =
          new AccountGroupMember.Key(a.getId(), internalGroup.getId());
      AccountGroupMember m = db.accountGroupMembers().get(key);
      if (m == null) {
        m = new AccountGroupMember(key);
        newAccountGroupMembers.add(m);
        newAccountGroupMemberAudits.add(new AccountGroupMemberAudit(m, me));
      }

      newMembers.add(MembersCollection.parse(a));
    }

    failWithBadRequestOnError(errors);

    db.accountGroupMembersAudit().insert(newAccountGroupMemberAudits);
    db.accountGroupMembers().insert(newAccountGroupMembers);
    for (final AccountGroupMember m : newAccountGroupMembers) {
      accountCache.evict(m.getAccountId());
    }

    return newMembers;
  }

  private Account findAccount(final String nameOrEmail) throws OrmException,
      NoSuchAccountException {
    Account r = accountResolver.find(nameOrEmail);
    if (r == null) {
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          r = createAccountByLdap(nameOrEmail);
          break;
        default:
      }
      if (r == null) {
        new NoSuchAccountException(nameOrEmail);
      }
    }
    return r;
  }

  private Account createAccountByLdap(String user) {
    if (!user.matches(Account.USER_NAME_PATTERN)) {
      return null;
    }

    try {
      final AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountCache.get(accountManager.authenticate(req).getAccountId())
          .getAccount();
    } catch (AccountException e) {
      return null;
    }
  }

  private static void failWithBadRequestOnError(final List<String> errors)
      throws BadRequestException {
    if (errors.isEmpty()) {
      return;
    }

    if (errors.size() == 1) {
      throw new BadRequestException(errors.get(0));
    }

    final StringBuilder b = new StringBuilder();
    b.append("Multiple errors on adding new group members:");
    for (final String error : errors) {
      b.append("\n");
      b.append(error);
    }
    throw new BadRequestException(b.toString());
  }
}
