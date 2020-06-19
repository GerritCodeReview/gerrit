// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.AttentionSetUnchangedOp;
import com.google.gerrit.server.change.RemoveFromAttentionSetOp;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * This class is used by {@link PostReview} to update the attention set when performing a review.
 */
public class PostReviewAttentionSet {

  private final PermissionBackend permissionBackend;
  private final AddToAttentionSetOp.Factory addToAttentionSetOpFactory;
  private final RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;

  @Inject
  PostReviewAttentionSet(
      PermissionBackend permissionBackend,
      AddToAttentionSetOp.Factory addToAttentionSetOpFactory,
      RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory,
      ApprovalsUtil approvalsUtil,
      AccountResolver accountResolver) {
    this.permissionBackend = permissionBackend;
    this.addToAttentionSetOpFactory = addToAttentionSetOpFactory;
    this.removeFromAttentionSetOpFactory = removeFromAttentionSetOpFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
  }

  /**
   * This method adjusts the attention set by adding and removing users. If the same user should be
   * added and removed or added/removed twice, the user will only be added/removed once, based on
   * first addition/removal.
   */
  public void updateAttentionSet(
      BatchUpdate bu,
      RevisionResource revision,
      ReviewInput input,
      List<ReviewerAdder.ReviewerAddition> reviewerResults)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {

    if (input.removeFromAttentionSet != null
        && input.addToAttentionSet != null
        && input.removeFromAttentionSet.isEmpty()
        && input.addToAttentionSet.isEmpty()) {
      // If both lists are empty (and not null), it means that the attention set must not change.
      bu.addOp(revision.getChange().getId(), new AttentionSetUnchangedOp());
      return;
    }

    Set<Account.Id> accountsChangedInCommit = new HashSet();
    // If we specify a user to remove, we remove it.
    if (input.removeFromAttentionSet != null) {
      for (AttentionSetInput remove : input.removeFromAttentionSet) {
        removeFromAttentionSet(bu, revision, remove, accountsChangedInCommit);
      }
    }

    // If we don't specify a user to remove, the user will be added here.
    if (input.addToAttentionSet != null) {
      for (AttentionSetInput add : input.addToAttentionSet) {
        addToAttentionSet(bu, revision, add, accountsChangedInCommit);
      }
    }

    // Replying removes the publishing user from the attention set.
    RemoveFromAttentionSetOp removeFromAttentionSetOp =
        removeFromAttentionSetOpFactory.create(revision.getAccountId(), "removed on reply", false);
    bu.addOp(revision.getChange().getId(), removeFromAttentionSetOp);

    // The rest of the conditions only apply if the change is ready for review
    if (isReadyForReview(revision, input)) {
      Account.Id uploader = revision.getPatchSet().uploader();
      Account.Id owner = revision.getChange().getOwner();
      Account.Id currentUser = revision.getAccountId();
      if (currentUser.equals(uploader) && !uploader.equals(owner)) {
        // When the uploader replies, add the owner to the attention set.
        AddToAttentionSetOp addToAttentionSetOp =
            addToAttentionSetOpFactory.create(owner, "uploader replied", false);
        bu.addOp(revision.getChange().getId(), addToAttentionSetOp);
      }
      if (currentUser.equals(uploader) || currentUser.equals(owner)) {
        // When the owner or uploader replies, add the reviewers to the attention set.
        // Filter by users that are currently reviewers.
        Set<Account.Id> finalCCs =
            reviewerResults.stream()
                .filter(r -> r.result.ccs == null)
                .map(r -> r.reviewers)
                .flatMap(x -> x.stream())
                .collect(toSet());
        for (Account.Id reviewer :
            approvalsUtil.getReviewers(revision.getChangeResource().getNotes()).byState(REVIEWER)
                .stream()
                .filter(r -> !finalCCs.contains(r))
                .collect(toList())) {
          AddToAttentionSetOp addToAttentionSetOp =
              addToAttentionSetOpFactory.create(reviewer, "owner or uploader replied", false);
          bu.addOp(revision.getChange().getId(), addToAttentionSetOp);
        }
      }
      if (!currentUser.equals(uploader) && !currentUser.equals(owner)) {
        // When neither the uploader nor the owner (reviewer or cc) replies, add the owner and the
        // uploader to the attention set.
        AddToAttentionSetOp addToAttentionSetOp =
            addToAttentionSetOpFactory.create(owner, "reviewer or cc replied", false);
        bu.addOp(revision.getChange().getId(), addToAttentionSetOp);

        if (owner.get() != uploader.get()) {
          addToAttentionSetOp =
              addToAttentionSetOpFactory.create(uploader, "reviewer or cc replied", false);
          bu.addOp(revision.getChange().getId(), addToAttentionSetOp);
        }
      }
    }
  }

  private boolean isReadyForReview(RevisionResource revision, ReviewInput input) {
    return (!revision.getChange().isWorkInProgress() && !input.workInProgress) || input.ready;
  }

  private void addToAttentionSet(
      BatchUpdate bu,
      RevisionResource revision,
      AttentionSetInput add,
      Set<Account.Id> accountsChangedInCommitv)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    AttentionSetUtil.validateInput(add);
    Account.Id attentionUserId =
        getAccountIdAndValidateUser(revision, add.user, accountsChangedInCommitv);

    AddToAttentionSetOp addToAttentionSetOp =
        addToAttentionSetOpFactory.create(attentionUserId, add.reason, false);
    bu.addOp(revision.getChange().getId(), addToAttentionSetOp);
  }

  private void removeFromAttentionSet(
      BatchUpdate bu,
      RevisionResource revision,
      AttentionSetInput remove,
      Set<Account.Id> accountsChangedInCommit)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    AttentionSetUtil.validateInput(remove);
    Account.Id attentionUserId =
        getAccountIdAndValidateUser(revision, remove.user, accountsChangedInCommit);

    RemoveFromAttentionSetOp removeFromAttentionSetOp =
        removeFromAttentionSetOpFactory.create(attentionUserId, remove.reason, false);
    bu.addOp(revision.getChange().getId(), removeFromAttentionSetOp);
  }

  private Account.Id getAccountIdAndValidateUser(
      RevisionResource revision, String user, Set<Account.Id> accountsChangedInCommit)
      throws ConfigInvalidException, IOException, PermissionBackendException,
          UnprocessableEntityException, BadRequestException {
    Account.Id attentionUserId = accountResolver.resolve(user).asUnique().account().id();
    try {
      permissionBackend
          .absentUser(attentionUserId)
          .change(revision.getNotes())
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          "Can't add to attention set: Read not permitted for " + attentionUserId, e);
    }
    if (accountsChangedInCommit.contains(attentionUserId)) {
      throw new BadRequestException(
          String.format(
              "%s can not be added/removed twice, and can not be added and "
                  + "removed at the same time",
              user));
    }
    accountsChangedInCommit.add(attentionUserId);
    return attentionUserId;
  }
}
