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

import java.util.Collections;

class PutMember implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String nameOrEmail;
  }

  private final GroupControl.Factory groupControlFactory;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final ReviewDb db;

  @Inject
  PutMember(final GroupControl.Factory groupControlFactory,
      final AccountManager accountManager,
      final AuthConfig authConfig,
      final AccountResolver accountResolver,
      final AccountCache accountCache, final ReviewDb db) {
    this.groupControlFactory = groupControlFactory;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.db = db;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public MemberInfo apply(GroupResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    final Account a = findAccount(input.nameOrEmail);
    if (!a.isActive()) {
      throw new InactiveAccountException(a.getFullName());
    }

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    final GroupControl control = groupControlFactory.controlFor(internalGroup);
    if (!control.canAddMember(a.getId())) {
      throw new AuthException("Cannot add member: " + a.getFullName());
    }

    final AccountGroupMember.Key key =
        new AccountGroupMember.Key(a.getId(), internalGroup.getId());
    AccountGroupMember m = db.accountGroupMembers().get(key);
    if (m == null) {
      m = new AccountGroupMember(key);
      db.accountGroupMembersAudit().insert(
          Collections.singleton(new AccountGroupMemberAudit(m,
              m.getAccountId())));
      db.accountGroupMembers().insert(Collections.singleton(m));
      accountCache.evict(m.getAccountId());
    }

    return MembersCollection.parse(a);
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
}
