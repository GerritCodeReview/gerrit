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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ReviewerJson;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ListReviewers implements RestReadView<ChangeResource> {
  private final ApprovalsUtil approvalsUtil;
  private final ReviewerJson json;
  private final ReviewerResource.Factory resourceFactory;

  @Inject
  ListReviewers(
      ApprovalsUtil approvalsUtil, ReviewerResource.Factory resourceFactory, ReviewerJson json) {
    this.approvalsUtil = approvalsUtil;
    this.resourceFactory = resourceFactory;
    this.json = json;
  }

  @Override
  public List<ReviewerInfo> apply(ChangeResource rsrc) throws PermissionBackendException {
    Map<String, ReviewerResource> reviewers = new LinkedHashMap<>();
    for (Account.Id accountId : approvalsUtil.getReviewers(rsrc.getNotes()).all()) {
      if (!reviewers.containsKey(accountId.toString())) {
        reviewers.put(accountId.toString(), resourceFactory.create(rsrc, accountId));
      }
    }
    for (Address adr : rsrc.getNotes().getReviewersByEmail().all()) {
      if (!reviewers.containsKey(adr.toString())) {
        reviewers.put(adr.toString(), new ReviewerResource(rsrc, adr));
      }
    }
    return json.format(reviewers.values());
  }
}
