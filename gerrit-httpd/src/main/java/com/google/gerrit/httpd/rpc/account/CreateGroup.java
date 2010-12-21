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
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.Collections;

class CreateGroup extends Handler<AccountGroup.Id> {
  interface Factory {
    CreateGroup create(String newName);
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final AccountCache accountCache;
  private final PersonIdent serverIdent;

  private final String name;

  @Inject
  CreateGroup(final ReviewDb db, final IdentifiedUser user,
      final AccountCache accountCache,
      @GerritPersonIdent final PersonIdent serverIdent,

      @Assisted final String newName) {
    this.db = db;
    this.user = user;
    this.accountCache = accountCache;
    this.serverIdent = serverIdent;

    this.name = newName;
  }

  @Override
  public AccountGroup.Id call() throws OrmException, NameAlreadyUsedException {
    final AccountGroup.NameKey key = new AccountGroup.NameKey(name);

    if (db.accountGroupNames().get(key) != null) {
      throw new NameAlreadyUsedException();
    }

    final AccountGroup.Id id = new AccountGroup.Id(db.nextAccountGroupId());
    final Account.Id me = user.getAccountId();
    final AccountGroup.UUID uuid = GroupUUID.make(name, //
        user.newCommitterIdent( //
            serverIdent.getWhen(), //
            serverIdent.getTimeZone()));
    final AccountGroup group = new AccountGroup(key, id, uuid);
    db.accountGroups().insert(Collections.singleton(group));

    try {
      final AccountGroupName n = new AccountGroupName(group);
      db.accountGroupNames().insert(Collections.singleton(n));
    } catch (OrmDuplicateKeyException dupeErr) {
      db.accountGroups().delete(Collections.singleton(group));
      throw new NameAlreadyUsedException();
    }

    AccountGroupMember member = new AccountGroupMember(//
        new AccountGroupMember.Key(me, id));

    db.accountGroupMembersAudit().insert(
        Collections.singleton(new AccountGroupMemberAudit(member, me)));
    db.accountGroupMembers().insert(Collections.singleton(member));

    accountCache.evict(me);
    return id;
  }
}
