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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class Reviewers implements ChildCollection<ChangeResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final ApprovalsUtil approvalsUtil;
  private final ReviewerResource.Factory resourceFactory;
  private final ListReviewers list;
  private final AccountResolver accountResolver;

  @Inject
  Reviewers(
      ApprovalsUtil approvalsUtil,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<ReviewerResource>> views,
      ListReviewers list,
      AccountResolver accountResolver) {
    this.approvalsUtil = approvalsUtil;
    this.resourceFactory = resourceFactory;
    this.views = views;
    this.list = list;
    this.accountResolver = accountResolver;
  }

  @Override
  public DynamicMap<RestView<ReviewerResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return list;
  }

  @Override
  public ReviewerResource parse(ChangeResource rsrc, IdString id)
      throws ResourceNotFoundException, AuthException, IOException, ConfigInvalidException {
    return parse(rsrc, id, /* includeInactiveAccounts= */ false);
  }

  public ReviewerResource parse(ChangeResource rsrc, IdString id, boolean includeInactiveAccounts)
      throws ResourceNotFoundException, AuthException, IOException, ConfigInvalidException {
    try {
      AccountResolver.Result result =
          includeInactiveAccounts
              ? accountResolver.resolveIncludeInactiveIgnoreVisibility(id.get())
              : accountResolver.resolveIgnoreVisibility(id.get());
      if (fetchAccountIds(rsrc).contains(result.asUniqueUser().getAccountId())) {
        return resourceFactory.create(rsrc, result.asUniqueUser().getAccountId());
      }
    } catch (AccountResolver.UnresolvableAccountException e) {
      if (e.isSelf()) {
        throw new AuthException(e.getMessage(), e);
      }
    }
    Address address = Address.tryParse(id.get());
    if (address != null && rsrc.getNotes().getReviewersByEmail().all().contains(address)) {
      return new ReviewerResource(rsrc, address);
    }

    throw new ResourceNotFoundException(id);
  }

  private Collection<Account.Id> fetchAccountIds(ChangeResource rsrc) {
    return approvalsUtil.getReviewers(rsrc.getNotes()).all();
  }
}
