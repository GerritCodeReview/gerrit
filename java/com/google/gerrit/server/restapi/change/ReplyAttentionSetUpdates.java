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
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.RobotClassifier;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.AttentionSetUnchangedOp;
import com.google.gerrit.server.change.RemoveFromAttentionSetOp;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
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
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * This class is used to update the attention set when performing a review or replying on a change.
 */
public class ReplyAttentionSetUpdates {

  private final PermissionBackend permissionBackend;
  private final AddToAttentionSetOp.Factory addToAttentionSetOpFactory;
  private final RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;
  private final RobotClassifier robotClassifier;

  @Inject
  ReplyAttentionSetUpdates(
      PermissionBackend permissionBackend,
      AddToAttentionSetOp.Factory addToAttentionSetOpFactory,
      RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory,
      ApprovalsUtil approvalsUtil,
      AccountResolver accountResolver,
      RobotClassifier robotClassifier) {
    this.permissionBackend = permissionBackend;
    this.addToAttentionSetOpFactory = addToAttentionSetOpFactory;
    this.removeFromAttentionSetOpFactory = removeFromAttentionSetOpFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
    this.robotClassifier = robotClassifier;
  }

  /** Adjusts the attention set but only based on the automatic rules. */
  public void processAutomaticAttentionSetRulesOnReply(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      boolean readyForReview,
      Set<String> potentiallyRemovedReviewers,
      Account.Id currentUser)
      throws IOException, ConfigInvalidException, PermissionBackendException,
          UnprocessableEntityException {
    if (robotClassifier.isRobot(currentUser)) {
      return;
    }
    Set<Account.Id> potentiallyRemovedReviewerIds = new HashSet();
    for (String reviewer : potentiallyRemovedReviewers) {
      potentiallyRemovedReviewerIds.add(getAccountId(changeNotes, reviewer));
    }
    processRules(
        bu,
        changeNotes,
        readyForReview,
        getUpdatedReviewers(changeNotes, potentiallyRemovedReviewerIds),
        currentUser);
  }

  /**
   * Adjusts the attention set by adding and removing users. If the same user should be added and
   * removed or added/removed twice, the user will only be added/removed once, based on first
   * addition/removal.
   */
  public void updateAttentionSet(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      ReviewInput input,
      List<ReviewerAdder.ReviewerAddition> reviewerResults,
      Account.Id currentUser)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    processManualUpdates(bu, changeNotes, input);
    if (input.ignoreAutomaticAttentionSetRules) {

      // If we ignore automatic attention set rules it means we need to pass this information to
      // ChangeUpdate. Also, we should stop all other attention set updates that are part of
      // this method and happen in PostReview.
      bu.addOp(changeNotes.getChangeId(), new AttentionSetUnchangedOp());
      return;
    }
    if (robotClassifier.isRobot(currentUser)) {
      botsWithNegativeLabelsAddOwnerAndUploader(bu, changeNotes, input);
      return;
    }
    // Gets a set of all the CCs in this change. Updated reviewers will be defined as reviewers who
    // didn't become CC (therefore this is a set of potentially removed reviewers - those that were
    // reviewers but became cc).
    Set<Account.Id> potentiallyRemovedReviewers =
        reviewerResults.stream()
            .filter(r -> r.state() == ReviewerState.CC)
            .map(r -> r.reviewers)
            .flatMap(x -> x.stream())
            .collect(toSet());
    processRules(
        bu,
        changeNotes,
        isReadyForReview(changeNotes, input),
        getUpdatedReviewers(changeNotes, potentiallyRemovedReviewers),
        currentUser);
  }

  private Set<Account.Id> getUpdatedReviewers(
      ChangeNotes changeNotes, Set<Account.Id> potentiallyRemovedReviewers) {
    // Filter by users that are currently reviewers and remove CCs.
    return approvalsUtil.getReviewers(changeNotes).byState(REVIEWER).stream()
        .filter(r -> !potentiallyRemovedReviewers.contains(r))
        .collect(Collectors.toSet());
  }

  /**
   * Process the automatic rules of the attention set. All of the automatic rules except
   * adding/removing reviewers and entering/exiting WIP state are done here, and the rest are done
   * in {@link ChangeUpdate}
   */
  private void processRules(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      boolean readyForReview,
      Set<Account.Id> reviewers,
      Account.Id currentUser) {
    // Replying removes the publishing user from the attention set.
    removeFromAttentionSet(bu, changeNotes, currentUser, "removed on reply", false);

    // The rest of the conditions only apply if the change is ready for review
    if (!readyForReview) {
      return;
    }
    Account.Id uploader = changeNotes.getCurrentPatchSet().uploader();
    Account.Id owner = changeNotes.getChange().getOwner();
    if (currentUser.equals(uploader) && !uploader.equals(owner)) {
      // When the uploader replies, add the owner to the attention set.
      addToAttentionSet(bu, changeNotes, owner, "uploader replied", false);
    }
    if (currentUser.equals(uploader) || currentUser.equals(owner)) {
      // When the owner or uploader replies, add the reviewers to the attention set.
      for (Account.Id reviewer : reviewers) {
        addToAttentionSet(bu, changeNotes, reviewer, "owner or uploader replied", false);
      }
    }
    if (!currentUser.equals(uploader) && !currentUser.equals(owner)) {
      // When neither the uploader nor the owner (reviewer or cc) replies, add the owner and the
      // uploader to the attention set.
      addToAttentionSet(bu, changeNotes, owner, "reviewer or cc replied", false);

      if (owner.get() != uploader.get()) {
        addToAttentionSet(bu, changeNotes, uploader, "reviewer or cc replied", false);
      }
    }
  }

  /** Process the manual updates of the attention set. */
  private void processManualUpdates(BatchUpdate bu, ChangeNotes changeNotes, ReviewInput input)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    Set<Account.Id> accountsChangedInCommit = new HashSet<>();
    // If we specify a user to remove, and the user is in the attention set, we remove it.
    if (input.removeFromAttentionSet != null) {
      for (AttentionSetInput remove : input.removeFromAttentionSet) {
        removeFromAttentionSet(bu, changeNotes, remove, accountsChangedInCommit);
      }
    }

    // If we don't specify a user to remove, but we specify addition for that user, the user will be
    // added if they are not in the attention set yet.
    if (input.addToAttentionSet != null) {
      for (AttentionSetInput add : input.addToAttentionSet) {
        addToAttentionSet(bu, changeNotes, add, accountsChangedInCommit);
      }
    }
  }

  /**
   * Bots don't process manual rules, but they do have one special rule: if voted negatively on a
   * label, add the owner and uploader.
   */
  private void botsWithNegativeLabelsAddOwnerAndUploader(
      BatchUpdate bu, ChangeNotes changeNotes, ReviewInput input) {
    if (input.labels.values().stream().anyMatch(vote -> vote < 0)) {
      Account.Id uploader = changeNotes.getCurrentPatchSet().uploader();
      Account.Id owner = changeNotes.getChange().getOwner();
      addToAttentionSet(bu, changeNotes, owner, "A robot voted negatively on a label", false);
      addToAttentionSet(bu, changeNotes, uploader, "A robot voted negatively on a label", false);
    }
  }

  /**
   * Adds the user to the attention set
   *
   * @param bu BatchUpdate to perform the updates to the attention set
   * @param changeNotes current change
   * @param user user to add to the attention set
   * @param reason reason for adding
   * @param notify whether or not to notify about this addition
   */
  private void addToAttentionSet(
      BatchUpdate bu, ChangeNotes changeNotes, Account.Id user, String reason, boolean notify) {
    AddToAttentionSetOp addOwnerToAttentionSet =
        addToAttentionSetOpFactory.create(user, reason, notify);
    bu.addOp(changeNotes.getChangeId(), addOwnerToAttentionSet);
  }

  /**
   * Removes the user from the attention set
   *
   * @param bu BatchUpdate to perform the updates to the attention set.
   * @param changeNotes current change.
   * @param user user to add remove from the attention set.
   * @param reason reason for removing.
   * @param notify whether or not to notify about this removal.
   */
  private void removeFromAttentionSet(
      BatchUpdate bu, ChangeNotes changeNotes, Account.Id user, String reason, boolean notify) {
    RemoveFromAttentionSetOp removeFromAttentionSetOp =
        removeFromAttentionSetOpFactory.create(user, reason, notify);
    bu.addOp(changeNotes.getChangeId(), removeFromAttentionSetOp);
  }

  private static boolean isReadyForReview(ChangeNotes changeNotes, ReviewInput input) {
    return (!changeNotes.getChange().isWorkInProgress() && !input.workInProgress) || input.ready;
  }

  private void addToAttentionSet(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      AttentionSetInput add,
      Set<Account.Id> accountsChangedInCommit)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    AttentionSetUtil.validateInput(add);
    Account.Id attentionUserId =
        getAccountIdAndValidateUser(changeNotes, add.user, accountsChangedInCommit);

    addToAttentionSet(bu, changeNotes, attentionUserId, add.reason, true);
  }

  private void removeFromAttentionSet(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      AttentionSetInput remove,
      Set<Account.Id> accountsChangedInCommit)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    AttentionSetUtil.validateInput(remove);
    Account.Id attentionUserId =
        getAccountIdAndValidateUser(changeNotes, remove.user, accountsChangedInCommit);

    removeFromAttentionSet(bu, changeNotes, attentionUserId, remove.reason, true);
  }

  private Account.Id getAccountId(ChangeNotes changeNotes, String user)
      throws ConfigInvalidException, IOException, UnprocessableEntityException,
          PermissionBackendException {
    Account.Id attentionUserId = accountResolver.resolve(user).asUnique().account().id();
    try {
      permissionBackend
          .absentUser(attentionUserId)
          .change(changeNotes)
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          "Can't add to attention set: Read not permitted for " + attentionUserId, e);
    }
    return attentionUserId;
  }

  private Account.Id getAccountIdAndValidateUser(
      ChangeNotes changeNotes, String user, Set<Account.Id> accountsChangedInCommit)
      throws ConfigInvalidException, IOException, PermissionBackendException,
          UnprocessableEntityException, BadRequestException {
    Account.Id attentionUserId = getAccountId(changeNotes, user);
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
