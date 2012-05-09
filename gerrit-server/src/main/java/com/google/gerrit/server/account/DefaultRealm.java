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

package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Set;

public class DefaultRealm implements Realm {
  private final EmailExpander emailExpander;
  private final AccountByEmailCache byEmail;
  private final MaterializedGroupMembership.Factory groupMembershipFactory;

  @Inject
  DefaultRealm(final EmailExpander emailExpander,
      final AccountByEmailCache byEmail,
      final MaterializedGroupMembership.Factory groupMembershipFactory) {
    this.emailExpander = emailExpander;
    this.byEmail = byEmail;
    this.groupMembershipFactory = groupMembershipFactory;
  }

  @Override
  public boolean allowsEdit(final Account.FieldName field) {
    return true;
  }

  @Override
  public AuthRequest authenticate(final AuthRequest who) {
    if (who.getEmailAddress() == null && who.getLocalUser() != null
        && emailExpander.canExpand(who.getLocalUser())) {
      who.setEmailAddress(emailExpander.expand(who.getLocalUser()));
    }
    return who;
  }

  @Override
  public AuthRequest link(ReviewDb db, Account.Id to, AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest unlink(ReviewDb db, Account.Id from, AuthRequest who) {
    return who;
  }

  @Override
  public void onCreateAccount(final AuthRequest who, final Account account) {
  }

  @Override
  public GroupMembership groups(final AccountState who) {
    return groupMembershipFactory.create(who.getInternalGroups());
  }

  @Override
  public Account.Id lookup(final String accountName) {
    if (emailExpander.canExpand(accountName)) {
      final Set<Account.Id> c = byEmail.get(emailExpander.expand(accountName));
      if (1 == c.size()) {
        return c.iterator().next();
      }
    }
    return null;
  }
}
