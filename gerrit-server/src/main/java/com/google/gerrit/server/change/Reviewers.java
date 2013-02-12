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
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Set;

public class Reviewers implements
    ChildCollection<ChangeResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final Parser parser;
  private final ReviewerResource.Factory resourceFactory;
  private final Provider<ListReviewers> list;

  static class Parser {
    private final AccountResolver resolver;

    @Inject
    Parser(AccountResolver resolver) {
      this.resolver = resolver;
    }

    Account.Id parse(ChangeResource rsrc, String id)
        throws OrmException, AuthException {
      if (id.equals("self")) {
        CurrentUser user = rsrc.getControl().getCurrentUser();
        if (user instanceof IdentifiedUser) {
          return ((IdentifiedUser) user).getAccountId();
        } else if (user instanceof AnonymousUser) {
          throw new AuthException("Authentication required");
        } else {
          return null;
        }
      } else {
        Set<Account.Id> matches = resolver.findAll(id);
        if (matches.size() != 1) {
          return null;
        }
        return Iterables.getOnlyElement(matches);
      }
    }
  }


  @Inject
  Reviewers(Provider<ReviewDb> dbProvider,
      Parser parser,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<ReviewerResource>> views,
      Provider<ListReviewers> list) {
    this.dbProvider = dbProvider;
    this.parser = parser;
    this.resourceFactory = resourceFactory;
    this.views = views;
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
    Account.Id accountId = parser.parse(rsrc, id.get());
    // See if the id exists as a reviewer for this change
    if (accountId != null && fetchAccountIds(rsrc).contains(accountId)) {
      return resourceFactory.create(rsrc, accountId);
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
