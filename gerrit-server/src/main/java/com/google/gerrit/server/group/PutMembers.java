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
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.BadRequestHandler;
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
import com.google.gerrit.server.group.PutMembers.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

class PutMembers implements RestModifyView<GroupResource, Input> {
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

  private final GroupControl.Factory groupControlFactory;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  PutMembers(final GroupControl.Factory groupControlFactory,
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
      throws AuthException, MethodNotAllowedException, BadRequestException,
      OrmException {
    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    input = Input.init(input);

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    final GroupControl control = groupControlFactory.controlFor(internalGroup);
    final Map<Account.Id, AccountGroupMember> newAccountGroupMembers = Maps.newHashMap();
    final List<AccountGroupMemberAudit> newAccountGroupMemberAudits = Lists.newLinkedList();
    final BadRequestHandler badRequest = new BadRequestHandler("adding new group members");
    final List<MemberInfo> newMembers = Lists.newLinkedList();
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();

    for (final String nameOrEmail : input.members) {
      final Account a = findAccount(nameOrEmail);
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
        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(a.getId(), internalGroup.getId());
        AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          newAccountGroupMembers.put(m.getAccountId(), m);
          newAccountGroupMemberAudits.add(new AccountGroupMemberAudit(m, me));
        }
      }
      newMembers.add(MembersCollection.parse(a));
    }

    badRequest.failOnError();

    db.accountGroupMembersAudit().insert(newAccountGroupMemberAudits);
    db.accountGroupMembers().insert(newAccountGroupMembers.values());
    for (final AccountGroupMember m : newAccountGroupMembers.values()) {
      accountCache.evict(m.getAccountId());
    }

    return newMembers;
  }

  private Account findAccount(final String nameOrEmail) throws OrmException {
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

  static class PutMember implements RestModifyView<GroupResource, PutMember.Input> {
    static class Input {
    }

    private final Provider<PutMembers> put;
    private final String id;

    PutMember(final Provider<PutMembers> put, String id) {
      this.put = put;
      this.id = id;
    }

    @Override
    public Class<PutMember.Input> inputType() {
      return PutMember.Input.class;
    }

    @Override
    public Object apply(GroupResource resource, PutMember.Input input)
        throws AuthException, MethodNotAllowedException, BadRequestException,
        OrmException {
      PutMembers.Input in = new PutMembers.Input();
      in._oneMember = id;
      List<MemberInfo> list = put.get().apply(resource, in);
      if (list.size() == 1) {
        return list.get(0);
      } else {
        throw new IllegalStateException();
      }
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
    public Class<PutMember.Input> inputType() {
      return PutMember.Input.class;
    }

    @Override
    public Object apply(MemberResource resource, PutMember.Input input) {
      // Do nothing, the user is already a member.
      return get.get().apply(resource);
    }
  }
}
