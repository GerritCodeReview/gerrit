// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DeleteReviewersUtil {
  private final DeleteReviewerOp.Factory deleteReviewerOpFactory;
  private final DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory;
  private final AccountResolver accountResolver;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  DeleteReviewersUtil(
      DeleteReviewerOp.Factory deleteReviewerOpFactory,
      DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory,
      AccountResolver accountResolver,
      ApprovalsUtil approvalsUtil) {
    this.deleteReviewerOpFactory = deleteReviewerOpFactory;
    this.deleteReviewerByEmailOpFactory = deleteReviewerByEmailOpFactory;
    this.accountResolver = accountResolver;
    this.approvalsUtil = approvalsUtil;
  }

  public void addDeleteReviewerOpToBatchUpdate(
      BatchUpdate batchUpdate, ChangeNotes changeNotes, ReviewerInput reviewerInput)
      throws IOException, ConfigInvalidException, AuthException, ResourceNotFoundException {

    try {
      AccountResolver.Result result =
          accountResolver.resolveIgnoreVisibility(reviewerInput.reviewer);
      if (fetchAccountIds(changeNotes).contains(result.asUniqueUser().getAccountId())) {
        DeleteReviewerInput deleteReviewerInput = new DeleteReviewerInput();
        deleteReviewerInput.notify = reviewerInput.notify;
        deleteReviewerInput.notifyDetails = reviewerInput.notifyDetails;
        batchUpdate.addOp(
            changeNotes.getChangeId(),
            deleteReviewerOpFactory.create(result.asUnique().account(), deleteReviewerInput));
        return;
      } else {
        return;
      }
    } catch (AccountResolver.UnresolvableAccountException e) {
      if (e.isSelf()) {
        throw new AuthException(e.getMessage(), e);
      }
    }
    Address address = Address.tryParse(reviewerInput.reviewer);
    if (address != null && changeNotes.getReviewersByEmail().all().contains(address)) {
      batchUpdate.addOp(changeNotes.getChangeId(), deleteReviewerByEmailOpFactory.create(address));
      return;
    }

    throw new ResourceNotFoundException(reviewerInput.reviewer);
  }

  private Collection<Account.Id> fetchAccountIds(ChangeNotes changeNotes) {
    return approvalsUtil.getReviewers(changeNotes).all();
  }
}
