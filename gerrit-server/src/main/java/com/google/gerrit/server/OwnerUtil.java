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

package com.google.gerrit.server;

import com.google.gerrit.common.data.OwnerInfo;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

public class OwnerUtil {
  private final Provider<ReviewDb> schema;
  private final Provider<CurrentUser> currentUser;
  private final AccountCache accountCache;
  private final AccountResolver accountResolver;

  @Inject
  OwnerUtil(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final AccountCache accountCache,
      final AccountResolver accountResolver) {
    this.schema = schema;
    this.currentUser = currentUser;
    this.accountCache = accountCache;
    this.accountResolver = accountResolver;
  }

  public OwnerInfo getOwnerInfo(final Owner.Id id)
      throws OrmException, NoSuchEntityException, NoSuchAccountException,
      NoSuchGroupException, NoSuchProjectException {
    try {
      switch(id.getType()) {
        case USER:    return new OwnerInfo(getAccount(id));
        case GROUP:   return new OwnerInfo(getAccountGroup(id));
        case PROJECT: return new OwnerInfo(getProject(id));
        case SITE:    return new OwnerInfo();
      }
    } catch (NullPointerException npe) {
      switch(id.getType()) {
        case USER:    throw new NoSuchAccountException(id.get());
        case GROUP:   throw new NoSuchGroupException(new AccountGroup.NameKey(id.get()));
        case PROJECT: throw new NoSuchProjectException(id.getProjectNameKey());
      }
    }
    throw new NoSuchEntityException();
  }

  private Account getAccount(final Owner.Id id) throws OrmException {
    IdentifiedUser user = getCurrentIdentifiedUser();
    if ("".equals(id.get()) && user != null) {
      return user.getAccount();
    }

    Account a = null;
    try {
      a = accountCache.get(id.getAccountId()).getAccount();
    } catch (NumberFormatException e) { }

    if (a == null) {
      AccountState as = accountCache.getByUsername(id.get());
      if (as != null) {
        a = as.getAccount();
      }
    }

    if (a == null) {
      a = accountResolver.find(id.get());
    }
    return a;
  }

  private AccountGroup getAccountGroup(final Owner.Id id)
      throws OrmException {
    ReviewDb db = schema.get();
    AccountGroup grp = null;

    try {
      grp = db.accountGroups().get(id.getAccountGroupId());
    } catch (NumberFormatException e) { }

    if (grp == null) {
      AccountGroup.NameKey gkey = new AccountGroup.NameKey(id.get());
      List<AccountGroup> groups = db.accountGroups().byName(gkey).toList();
      if (groups.size() > 0) {
        grp = groups.get(0);
      }
    }
    return grp;
  }

  private Project getProject(final Owner.Id id)
      throws OrmException {
    return schema.get().projects().get(new Project.NameKey(id.get()));
  }

  protected IdentifiedUser getCurrentIdentifiedUser() {
    try {
      CurrentUser u = currentUser.get();
      if (u instanceof IdentifiedUser) {
        return (IdentifiedUser) u;
      }
    } catch(Exception e) {}
    return null;
  }
}
