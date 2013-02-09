// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Set;

public class Reviewers implements
    ChildCollection<ChangeResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final AccountResolver resolver;
  private final ReviewerResource.Factory resourceFactory;
  private final AccountCache accountCache;
  private final Provider<ListReviewers> list;

  @Inject
  Reviewers(Provider<ReviewDb> dbProvider,
      AccountResolver resolver,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<ReviewerResource>> views,
      AccountCache accountCache,
      Provider<ListReviewers> list) {
    this.dbProvider = dbProvider;
    this.resolver = resolver;
    this.resourceFactory = resourceFactory;
    this.views = views;
    this.accountCache = accountCache;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<ReviewerResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return list.get();
  }

  @Override
  public ReviewerResource parse(ChangeResource rsrc, IdString id)
      throws OrmException, ResourceNotFoundException, AuthException {
    Account.Id accountId;
    if (id.equals("self")) {
      CurrentUser user = rsrc.getControl().getCurrentUser();
      if (user instanceof IdentifiedUser) {
        accountId = ((IdentifiedUser) user).getAccountId();
      } else if (user instanceof AnonymousUser) {
        throw new AuthException("Authentication required");
      } else {
        throw new ResourceNotFoundException(id);
      }
    } else {
      Set<Account.Id> matches = resolver.findAll(id.get());
      if (matches.size() != 1) {
        throw new ResourceNotFoundException(id);
      }
      accountId = Iterables.getOnlyElement(matches);
    }

    // See if the id exists as a reviewer for this change
    if (fetchAccountIds(rsrc).contains(accountId)) {
      Account account = accountCache.get(accountId).getAccount();
      return resourceFactory.create(rsrc, account);
    }
    throw new ResourceNotFoundException(id);
  }

  private Set<Account.Id> fetchAccountIds(ChangeResource rsrc)
      throws OrmException {
    Set<Account.Id> accountIds = Sets.newHashSet();
    for (PatchSetApproval a
         : dbProvider.get().patchSetApprovals().byChange(rsrc.getChange().getId())) {
      accountIds.add(a.getAccountId());
    }
    return accountIds;
  }
}
