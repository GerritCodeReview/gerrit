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
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class RevisionReviewers implements ChildCollection<RevisionResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil patchSetUtil;
  private final AccountsCollection accounts;
  private final ReviewerResource.Factory resourceFactory;
  private final ListRevisionReviewers list;

  @Inject
  RevisionReviewers(
      ApprovalsUtil approvalsUtil,
      PatchSetUtil patchSetUtil,
      AccountsCollection accounts,
      ReviewerResource.Factory resourceFactory,
      DynamicMap<RestView<ReviewerResource>> views,
      ListRevisionReviewers list) {
    this.approvalsUtil = approvalsUtil;
    this.patchSetUtil = patchSetUtil;
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
      throws ResourceNotFoundException, AuthException, MethodNotAllowedException, IOException,
          ConfigInvalidException {
    // if (!rsrc.isCurrent()) {
    //   throw new MethodNotAllowedException("Cannot access on non-current patch set");
    // }
    Address address = Address.tryParse(id.get());

    PatchSet.Id psId = rsrc.isCurrent() ? rsrc.getChange().currentPatchSetId() : PatchSet.Id.parse(id.get());
    Account.Id accountId = null;
    try {
      accountId = accounts.parse(TopLevelResource.INSTANCE, id).getUser().getAccountId();
    } catch (ResourceNotFoundException e) {
      if (address == null) {
        throw e;
      }
    }

    PatchSet ps = patchSetUtil.get(rsrc.getNotes(), psId);

    for(PatchSetApproval approval : approvalsUtil.byPatchSetUser(rsrc.getNotes(), psId, accountId)){
      if (approval.accountId().equals(accountId)) {
        return resourceFactory.create(new RevisionResource(rsrc.getChangeResource(), ps), accountId);
      }
    }

    // Collection<Account.Id> reviewers = approvalsUtil.getReviewers(rsrc.getNotes()).all();

    // See if the address exists as a reviewer on the change, for maintaining backward compatibility
    if (!rsrc.isCurrent() && address != null && rsrc.getNotes().getReviewersByEmail().all().contains(address)) {
      return new ReviewerResource(new RevisionResource(rsrc.getChangeResource(), ps), address);
    }

    throw new ResourceNotFoundException(id);
  }
}
