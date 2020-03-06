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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.change.AttentionSetEntryResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AttentionSet implements ChildCollection<ChangeResource, AttentionSetEntryResource> {
  private final DynamicMap<RestView<AttentionSetEntryResource>> views;
  private final ApprovalsUtil approvalsUtil;
  private final ReviewerResource.Factory resourceFactory;
  private final ListReviewers list;
  private final AccountResolver accountResolver;

  @Inject
  AttentionSet(
      ApprovalsUtil approvalsUtil,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<AttentionSetEntryResource>> views,
      ListReviewers list,
      AccountResolver accountResolver) {
    this.approvalsUtil = approvalsUtil;
    this.resourceFactory = resourceFactory;
    this.views = views;
    this.list = list;
    this.accountResolver = accountResolver;
    System.out.println("##### views: " + views);
  }

  @Override
  public DynamicMap<RestView<AttentionSetEntryResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public AttentionSetEntryResource parse(ChangeResource rsrc, IdString idString)
      throws ResourceNotFoundException, AuthException, IOException, ConfigInvalidException {
    System.out.println("##### got IdString: " + idString.get());
    int accountIdInt = Integer.parseInt(idString.get()); // ö exception?

    try {
      Account.Id accountId =
          accountResolver.resolve(String.valueOf(accountIdInt)).asUnique().account().id();
      return new AttentionSetEntryResource(accountId, "foo", true);
    } catch (UnresolvableAccountException e) {
      throw new ResourceNotFoundException(idString, e);
    }
  }
}
