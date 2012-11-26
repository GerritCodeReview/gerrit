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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.HashSet;
import java.util.Set;

public class Reviewers implements
    ChildCollection<ChangeResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final AccountCache accountCache;
  private final Provider<ListReviewers> list;

  @Inject
  Reviewers(Provider<ReviewDb> dbProvider,
            DynamicMap<RestView<ReviewerResource>> views,
            AccountCache accountCache,
            Provider<ListReviewers> list) {
    this.dbProvider = dbProvider;
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
  public ReviewerResource parse(ChangeResource changeResource, String id)
      throws OrmException, ResourceNotFoundException {
    // Get the account id
    if (!id.matches("^[0-9]+$")) {
      throw new ResourceNotFoundException(id);
    }
    Account.Id accountId = Account.Id.parse(id);

    // See if the id exists as a reviewer for this change
    if (fetchAccountIds(changeResource).contains(accountId)) {
      Account account = accountCache.get(accountId).getAccount();
      return new ReviewerResource(changeResource, account);
    }
    throw new ResourceNotFoundException(id);
  }

  private Set<Account.Id> fetchAccountIds(ChangeResource changeResource)
      throws OrmException {
    ReviewDb db = dbProvider.get();
    Change.Id changeId = changeResource.getChange().getId();
    Set<Account.Id> accountIds = new HashSet<Account.Id>();
    for (PatchSetApproval patchSetApproval
         : db.patchSetApprovals().byChange(changeId)) {
      accountIds.add(patchSetApproval.getAccountId());
    }
    return accountIds;
  }
}
