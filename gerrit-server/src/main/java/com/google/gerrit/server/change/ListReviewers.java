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

import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
class ListReviewers implements RestReadView<ChangeResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ApprovalsUtil approvalsUtil;
  private final ReviewerJson json;
  private final ReviewerResource.Factory resourceFactory;

  @Inject
  ListReviewers(
      Provider<ReviewDb> dbProvider,
      ApprovalsUtil approvalsUtil,
      ReviewerResource.Factory resourceFactory,
      ReviewerJson json) {
    this.dbProvider = dbProvider;
    this.approvalsUtil = approvalsUtil;
    this.resourceFactory = resourceFactory;
    this.json = json;
  }

  @Override
  public List<ReviewerInfo> apply(ChangeResource rsrc) throws OrmException {
    Map<Account.Id, ReviewerResource> reviewers = new LinkedHashMap<>();
    ReviewDb db = dbProvider.get();
    for (Account.Id accountId : approvalsUtil.getReviewers(db, rsrc.getNotes()).all()) {
      if (!reviewers.containsKey(accountId)) {
        reviewers.put(accountId, resourceFactory.create(rsrc, accountId));
      }
    }
    return json.format(reviewers.values());
  }
}
