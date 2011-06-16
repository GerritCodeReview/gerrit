// Copyright (C) 2009 The Android Open Source Project
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


package com.google.gerrit.server.patch;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class AddReviewer implements Callable<ReviewerResult> {
  public interface Factory {
    AddReviewer create(Change.Id changeId, Collection<String> nameOrEmails);
  }

  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final AccountResolver accountResolver;
  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ApprovalCategory.Id addReviewerCategoryId;

  private final Change.Id changeId;
  private final Collection<String> reviewers;

  @Inject
  AddReviewer(final AddReviewerSender.Factory addReviewerSenderFactory,
      final AccountResolver accountResolver,
      final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final IdentifiedUser currentUser, final ApprovalTypes approvalTypes,
      @Assisted final Change.Id changeId,
      @Assisted final Collection<String> nameOrEmails) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.accountResolver = accountResolver;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.currentUser = currentUser;

    final List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    addReviewerCategoryId =
        allTypes.get(allTypes.size() - 1).getCategory().getId();

    this.changeId = changeId;
    this.reviewers = nameOrEmails;
  }

  @Override
  public ReviewerResult call() throws Exception {
    final Set<Account.Id> reviewerIds = new HashSet<Account.Id>();
    final ChangeControl control = changeControlFactory.validateFor(changeId);

    final ReviewerResult result = new ReviewerResult();
    for (final String nameOrEmail : reviewers) {
      final Account account = accountResolver.find(nameOrEmail);
      if (account == null) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.ACCOUNT_NOT_FOUND, nameOrEmail));
        continue;
      }

      if (!account.isActive()) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.ACCOUNT_INACTIVE,
            formatUser(account, nameOrEmail)));
        continue;
      }

      final IdentifiedUser user = identifiedUserFactory.create(account.getId());
      if (!control.forUser(user).isVisible()) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.CHANGE_NOT_VISIBLE,
            formatUser(account, nameOrEmail)));
        continue;
      }

      reviewerIds.add(account.getId());
    }

    if (reviewerIds.isEmpty()) {
      return result;
    }

    // Add the reviewers to the database
    //
    final Set<Account.Id> added = new HashSet<Account.Id>();
    final List<PatchSetApproval> toInsert = new ArrayList<PatchSetApproval>();
    final PatchSet.Id psid = control.getChange().currentPatchSetId();
    for (final Account.Id reviewer : reviewerIds) {
      if (!exists(psid, reviewer)) {
        // This reviewer has not entered an approval for this change yet.
        //
        final PatchSetApproval myca = dummyApproval(psid, reviewer);
        toInsert.add(myca);
        added.add(reviewer);
      }
    }
    db.patchSetApprovals().insert(toInsert);

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    added.remove(currentUser.getAccountId());
    if (!added.isEmpty()) {
      final AddReviewerSender cm;

      cm = addReviewerSenderFactory.create(control.getChange());
      cm.setFrom(currentUser.getAccountId());
      cm.addReviewers(added);
      cm.send();
    }

    return result;
  }

  private String formatUser(Account account, String nameOrEmail) {
    if (nameOrEmail.matches("^[1-9][0-9]*$")) {
      return RemoveReviewer.formatUser(account, nameOrEmail);
    } else {
      return nameOrEmail;
    }
  }

  private boolean exists(final PatchSet.Id patchSetId,
      final Account.Id reviewerId) throws OrmException {
    return db.patchSetApprovals().byPatchSetUser(patchSetId, reviewerId)
        .iterator().hasNext();
  }

  private PatchSetApproval dummyApproval(final PatchSet.Id patchSetId,
      final Account.Id reviewerId) {
    return new PatchSetApproval(new PatchSetApproval.Key(patchSetId,
        reviewerId, addReviewerCategoryId), (short) 0);
  }
}
