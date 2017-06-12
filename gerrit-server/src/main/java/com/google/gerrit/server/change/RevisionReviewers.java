// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.mail.Address;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;

@Singleton
public class RevisionReviewers implements ChildCollection<RevisionResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final ApprovalsUtil approvalsUtil;
  private final AccountsCollection accounts;
  private final ReviewerResource.Factory resourceFactory;
  private final ListRevisionReviewers list;

  @Inject
  RevisionReviewers(
      Provider<ReviewDb> dbProvider,
      ApprovalsUtil approvalsUtil,
      AccountsCollection accounts,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<ReviewerResource>> views,
      ListRevisionReviewers list) {
    this.dbProvider = dbProvider;
    this.approvalsUtil = approvalsUtil;
    this.accounts = accounts;
    this.resourceFactory = resourceFactory;
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<ReviewerResource>> views() {
    return views;
  }

  @Override
  public RestView<RevisionResource> list() {
    return list;
  }

  @Override
  public ReviewerResource parse(RevisionResource rsrc, IdString id)
      throws OrmException, ResourceNotFoundException, AuthException, MethodNotAllowedException,
          IOException {
    if (!rsrc.isCurrent()) {
      throw new MethodNotAllowedException("Cannot access on non-current patch set");
    }
    Address address = Address.tryParse(id.get());

    Account.Id accountId = null;
    try {
      accountId = accounts.parse(TopLevelResource.INSTANCE, id).getUser().getAccountId();
    } catch (ResourceNotFoundException e) {
      if (address == null) {
        throw e;
      }
    }
    Collection<Account.Id> reviewers =
        approvalsUtil.getReviewers(dbProvider.get(), rsrc.getNotes()).all();
    // See if the id exists as a reviewer for this change
    if (reviewers.contains(accountId)) {
      return resourceFactory.create(rsrc, accountId);
    }

    // See if the address exists as a reviewer on the change
    if (address != null && rsrc.getNotes().getReviewersByEmail().all().contains(address)) {
      return new ReviewerResource(rsrc, address);
    }

    throw new ResourceNotFoundException(id);
  }
}
