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

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Map;

class ListReviewers implements RestReadView<ChangeResource> {
  private final AccountCache accountCache;
  private final Provider<ReviewDb> dbProvider;
  private final ReviewerJson json;

  @Inject
  ListReviewers(AccountCache accountCache,
      Provider<ReviewDb> dbProvider,
      ReviewerJson json) {
    this.accountCache = accountCache;
    this.dbProvider = dbProvider;
    this.json = json;
  }

  @Override
  public Object apply(ChangeResource rsrc) throws BadRequestException,
      OrmException {
    Map<Account.Id, Object> reviewers = Maps.newLinkedHashMap();
    ReviewDb db = dbProvider.get();
    Change.Id changeId = rsrc.getChange().getId();
    for (PatchSetApproval patchSetApproval
         : db.patchSetApprovals().byChange(changeId)) {
      Account.Id accountId = patchSetApproval.getAccountId();
      if (!reviewers.containsKey(accountId)) {
        Account account = accountCache.get(accountId).getAccount();
        reviewers.put(accountId,
                      json.format(new ReviewerResource(rsrc, account)));
      }
    }
    return reviewers.values();
  }
}
