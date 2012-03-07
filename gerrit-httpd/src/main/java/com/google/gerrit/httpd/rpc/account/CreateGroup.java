// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

class CreateGroup extends Handler<AccountGroup.Id> {
  interface Factory {
    CreateGroup create(String groupName);
  }

  private final PerformCreateGroup.Factory performCreateGroupFactory;
  private final IdentifiedUser user;
  private final String groupName;

  @Inject
  CreateGroup(final PerformCreateGroup.Factory performCreateGroupFactory,
      final IdentifiedUser user, @Assisted final String groupName) {
    this.performCreateGroupFactory = performCreateGroupFactory;
    this.user = user;
    this.groupName = groupName;
  }

  @Override
  public AccountGroup.Id call() throws OrmException, NameAlreadyUsedException,
      PermissionDeniedException {
    final PerformCreateGroup performCreateGroup = performCreateGroupFactory.create();
    final Account.Id me = user.getAccountId();
    return performCreateGroup.createGroup(groupName, null, false, null, Collections.singleton(me), null);
  }
}
