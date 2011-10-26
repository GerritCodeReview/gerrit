// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AddGroupMember;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

public class AddGroupMemberHandler extends Handler<GroupDetail> {

  interface Factory {
    AddGroupMemberHandler create(AccountGroup.Id groupId, String nameOrEmail);
  }

  private final AccountResolver accountResolver;
  private final AddGroupMember.Factory addGroupMemberFactory;

  private final AccountGroup.Id groupId;
  private final String nameOrEmail;

  @Inject
  AddGroupMemberHandler(final AccountResolver accountResolver,
      final AddGroupMember.Factory addGroupMemberFactory,
      final @Assisted AccountGroup.Id groupId,
      final @Assisted String nameOrEmail) {
    this.accountResolver = accountResolver;
    this.addGroupMemberFactory = addGroupMemberFactory;
    this.groupId = groupId;
    this.nameOrEmail = nameOrEmail;
  }

  @Override
  public GroupDetail call() throws Exception {
    final Account.Id accountId = findAccount(nameOrEmail);
    final GroupMemberResult result = addGroupMemberFactory.create(groupId,
        Collections.singleton(accountId)).call();
    if (!result.getErrors().isEmpty()) {
      final GroupMemberResult.Error error = result.getErrors().get(0);
      switch (error.getType()) {
        case ACCOUNT_INACTIVE:
          throw new InactiveAccountException(error.getName());
        case ADD_NOT_PERMITTED:
          throw new NoSuchEntityException();
        default:
          throw new IllegalStateException();
      }
    }
    return result.getGroup();
  }

  private Account.Id findAccount(final String nameOrEmail) throws OrmException,
      NoSuchAccountException {
    final Account r = accountResolver.find(nameOrEmail);
    if (r == null) {
      throw new NoSuchAccountException(nameOrEmail);
    }
    return r.getId();
  }
}
