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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.AttentionSetUnchangedOp;
import com.google.gerrit.server.change.AttentionSetUpdateCondition;
import com.google.gerrit.server.change.CommentThread;
import com.google.gerrit.server.change.CommentThreads;
import com.google.gerrit.server.change.RemoveFromAttentionSetOp;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * This class is used to update the attention set when performing a review or replying on a change.
 */
public class ReplyAttentionSetUpdates {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final AddToAttentionSetOp.Factory addToAttentionSetOpFactory;
  private final RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;
  private final ServiceUserClassifier serviceUserClassifier;
  private final CommentsUtil commentsUtil;
  private final DraftCommentsReader draftCommentsReader;
  private final ProjectCache projectCache;

  @Inject
  ReplyAttentionSetUpdates(
      PermissionBackend permissionBackend,
      AddToAttentionSetOp.Factory addToAttentionSetOpFactory,
      RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory,
      ApprovalsUtil approvalsUtil,
      AccountResolver accountResolver,
      ServiceUserClassifier serviceUserClassifier,
      CommentsUtil commentsUtil,
      DraftCommentsReader draftCommentsReader,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.addToAttentionSetOpFactory = addToAttentionSetOpFactory;
    this.removeFromAttentionSetOpFactory = removeFromAttentionSetOpFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
    this.serviceUserClassifier = serviceUserClassifier;
    this.commentsUtil = commentsUtil;
    this.draftCommentsReader = draftCommentsReader;
    this.projectCache = projectCache;
  }

  /** Adjusts the attention set but only based on the automatic rules. */
  public void processAutomaticAttentionSetRulesOnReply(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      boolean readyForReview,
      CurrentUser currentUser,
      List<HumanComment> commentsToBePublished) {
    if (serviceUserClassifier.isServiceUser(currentUser.getAccountId())) {
      return;
    }
    processRules(
        bu,
        /* postReviewOp= */ null,
        changeNotes,
        readyForReview,
        currentUser,
        commentsToBePublished.stream().collect(toImmutableSet()));
  }

  /**
   * Adjusts the attention set when a review is posted.
   *
   * <p>If the same user should be added and removed or added/removed twice, the user will only be
   * added/removed once, based on first addition/removal.
   *
   * @param postReviewOp the {@link PostReviewOp} that is being executed before the attention set
   *     updates
   */
  public void updateAttentionSetOnPostReview(
      BatchUpdate bu,
      PostReviewOp postReviewOp,
      ChangeNotes changeNotes,
      ReviewInput input,
      CurrentUser currentUser)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    requireNonNull(postReviewOp, "postReviewOp must not be null");

    processManualUpdates(bu, changeNotes, input);
    if (input.ignoreAutomaticAttentionSetRules) {

      // If we ignore automatic attention set rules it means we need to pass this information to
      // ChangeUpdate. Also, we should stop all other attention set updates that are part of
      // this method and happen in PostReview.
      bu.addOp(changeNotes.getChangeId(), new AttentionSetUnchangedOp());
      return;
    }
    boolean isReadyForReview = isReadyForReview(changeNotes, input);

    if (isReadyForReview && serviceUserClassifier.isServiceUser(currentUser.getAccountId())) {
      botsWithNegativeLabelsAddOwnerAndUploader(bu, postReviewOp, changeNotes);
      return;
    }

    processRules(
        bu,
        postReviewOp,
        changeNotes,
        isReadyForReview,
        currentUser,
        getAllNewComments(changeNotes, input, currentUser));
  }

  private ImmutableSet<HumanComment> getAllNewComments(
      ChangeNotes changeNotes, ReviewInput input, CurrentUser currentUser) {
    Set<HumanComment> newComments = new HashSet<>();
    if (input.comments != null) {
      for (ReviewInput.CommentInput commentInput :
          input.comments.values().stream().flatMap(x -> x.stream()).collect(Collectors.toList())) {
        newComments.add(
            commentsUtil.newHumanComment(
                changeNotes,
                currentUser,
                TimeUtil.now(),
                commentInput.path,
                commentInput.patchSet == null
                    ? changeNotes.getChange().currentPatchSetId()
                    : PatchSet.id(changeNotes.getChange().getId(), commentInput.patchSet),
                commentInput.side(),
                commentInput.message,
                commentInput.unresolved,
                commentInput.inReplyTo,
                CommentsUtil.createFixSuggestionsFromInput(commentInput.fixSuggestions)));
      }
    }
    List<HumanComment> drafts = new ArrayList<>();
    if (input.drafts == ReviewInput.DraftHandling.PUBLISH) {
      drafts =
          draftCommentsReader.getDraftsByPatchSetAndDraftAuthor(
              changeNotes, changeNotes.getChange().currentPatchSetId(), currentUser.getAccountId());
    }
    if (input.drafts == ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS) {
      drafts =
          draftCommentsReader.getDraftsByChangeAndDraftAuthor(
              changeNotes, currentUser.getAccountId());
    }
    return Stream.concat(newComments.stream(), drafts.stream()).collect(toImmutableSet());
  }

  /**
   * Process the automatic rules of the attention set.
   *
   * <p>All of the automatic rules except adding/removing reviewers and entering/exiting WIP state
   * are done here, and the rest are done in {@link ChangeUpdate}.
   *
   * @param postReviewOp {@link PostReviewOp} that is being executed before the attention set
   *     updates, may be {@code null}
   */
  private void processRules(
      BatchUpdate bu,
      @Nullable PostReviewOp postReviewOp,
      ChangeNotes changeNotes,
      boolean readyForReview,
      CurrentUser currentUser,
      ImmutableSet<HumanComment> allNewComments) {
    updateAttentionSetForCurrentUser(bu, postReviewOp, changeNotes, currentUser);

    // The rest of the conditions only apply if the change is open.
    if (changeNotes.getChange().getStatus().isClosed()) {
      // We still add the owner if a new comment thread was created, on closed changes.
      if (allNewComments.stream().anyMatch(c -> c.parentUuid == null)) {
        addToAttentionSet(
            bu,
            changeNotes,
            changeNotes.getChange().getOwner(),
            "A new comment thread was created",
            false);
      }
      return;
    }
    // The rest of the conditions only apply if the change is ready for review and reply is not
    // posted by a bot.
    if (!readyForReview || serviceUserClassifier.isServiceUser(currentUser.getAccountId())) {
      return;
    }

    addOwnerAndUploaderToAttentionSetIfSomeoneElseReplied(
        bu, postReviewOp, changeNotes, currentUser, readyForReview, allNewComments);
    addAllAuthorsOfCommentThreads(bu, changeNotes, allNewComments, currentUser);
  }

  /**
   * Updates the attention set for the current user.
   *
   * <p>Removes the current user from the attention set (since they replied) unless they voted on an
   * outdated patch set and some of the votes were not copied to the current patch set (in this case
   * they should be in the attention set to re-apply their votes).
   *
   * <p>If the current user voted on an outdated patch set and some of the votes were not copied to
   * the current patch set:
   *
   * <ul>
   *   <li>the current user is added to the attention set (if they are not in the attention set yet)
   *       or
   *   <li>the reason for the current user to be in the attention set is updated (if they are
   *       already in the attention set).
   * </ul>
   */
  private void updateAttentionSetForCurrentUser(
      BatchUpdate bu,
      @Nullable PostReviewOp postReviewOp,
      ChangeNotes changeNotes,
      CurrentUser currentUser) {
    if (postReviewOp == null) {
      // Replying removes the current user from the attention set.
      removeFromAttentionSet(
          bu, changeNotes, currentUser.getAccountId(), "removed on reply", false);
    } else {
      // If the current user voted on an outdated patch set and some of the votes were not copied to
      // the current patch set the current user should stay in the attention set, or be added to the
      // attention set. In case the user stays in the attention set, this updates the reason for
      // being in the attention set.
      AttentionSetUpdateCondition addOrKeepCondition =
          () ->
              postReviewOp
                  .getResult()
                  .appliedVotesOnOutdatedPatchSetThatWereNotCopiedToCurrentPatchSet();
      maybeAddToAttentionSet(
          bu,
          addOrKeepCondition,
          changeNotes,
          currentUser.getAccountId(),
          "Some votes were not copied to the current patch set",
          false);

      // Otherwise replying removes the current user from the attention set.
      AttentionSetUpdateCondition removeCondition = () -> !addOrKeepCondition.check();
      maybeRemoveFromAttentionSet(
          bu, removeCondition, changeNotes, currentUser.getAccountId(), "removed on reply", false);
    }
  }

  /**
   * Adds the owner and uploader to the attention set if someone else replied.
   *
   * <p>Replying means they either updated the votes on the current patch set (either directly on
   * the current patch set or the votes were copied to the current patch set), they posted a change
   * message, they marked the change as ready or they posted new comments.
   */
  private void addOwnerAndUploaderToAttentionSetIfSomeoneElseReplied(
      BatchUpdate bu,
      @Nullable PostReviewOp postReviewOp,
      ChangeNotes changeNotes,
      CurrentUser currentUser,
      boolean readyForReview,
      ImmutableSet<HumanComment> allNewComments) {
    AttentionSetUpdateCondition condition =
        postReviewOp != null
            ? () ->
                postReviewOp.getResult().updatedAnyVoteOnCurrentPatchSet()
                    || postReviewOp.getResult().postedChangeMessage()
                    || (changeNotes.getChange().isWorkInProgress() && readyForReview)
                    || !allNewComments.isEmpty()
            : () ->
                (changeNotes.getChange().isWorkInProgress() && readyForReview)
                    || !allNewComments.isEmpty();

    Account.Id owner = changeNotes.getChange().getOwner();
    if (!currentUser.getAccountId().equals(owner)) {
      maybeAddToAttentionSet(
          bu, condition, changeNotes, owner, "Someone else replied on the change", false);
    }

    Account.Id uploader = changeNotes.getCurrentPatchSet().uploader();
    if (!owner.equals(uploader) && !currentUser.getAccountId().equals(uploader)) {
      maybeAddToAttentionSet(
          bu, condition, changeNotes, uploader, "Someone else replied on the change", false);
    }
  }

  /** Adds all authors of all comment threads that received a reply during this update */
  private void addAllAuthorsOfCommentThreads(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      ImmutableSet<HumanComment> allNewComments,
      CurrentUser currentUser) {
    boolean isOwnerOrUploader =
        currentUser.getAccountId().equals(changeNotes.getChange().getOwner())
            || currentUser.getAccountId().equals(changeNotes.getCurrentPatchSet().uploader());

    boolean noCRLabel = false;
    Optional<LabelValue> maxCRValue =
        projectCache
            .get(changeNotes.getChange().getProject())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Couldn't find project \"%s\" for a change \"%s\"",
                            changeNotes.getChange().getProject(), changeNotes.getChangeId())))
            .getLabelTypes(changeNotes)
            .byLabel(LabelId.CODE_REVIEW)
            .map(l -> l.getMax());

    ImmutableSet<Account.Id> maxCrApprovers;
    if (maxCRValue.isPresent()) {
      maxCrApprovers =
          changeNotes.getApprovals().all().get(changeNotes.getCurrentPatchSet().id()).stream()
              .filter(
                  a ->
                      a.label().equals(LabelId.CODE_REVIEW)
                          && a.value() == maxCRValue.get().getValue())
              .map(a -> a.accountId())
              .collect(toImmutableSet());
    } else {
      noCRLabel = true;
      maxCrApprovers = ImmutableSet.of();
    }

    // Include newly published comments, when building threads.
    ImmutableList<HumanComment> relevantComments =
        Stream.concat(
                commentsUtil.publishedHumanCommentsByChange(changeNotes).stream(),
                allNewComments.stream())
            .collect(toImmutableList());
    ImmutableSet<CommentThread<HumanComment>> repliedToCommentThreads =
        CommentThreads.forComments(relevantComments).getThreadsForChildren(allNewComments);

    LinkedHashSet<Account.Id> repliedToUsers = new LinkedHashSet<>();
    for (CommentThread<HumanComment> thread : repliedToCommentThreads) {
      // If thread is resolved, we only bring back the commenters who have not yet left max
      // Code-Review vote.
      // If Owner replied but didn't resolve, we assume clarification was asked add everyone on the
      // thread to attention set.
      boolean ignoreVoteCheck = noCRLabel || (thread.unresolved() && isOwnerOrUploader);
      if (thread.unresolved() && !isOwnerOrUploader) {
        // Reviewer replied. Owner is still the one to act. No need to add commenters.
        continue;
      }
      thread.comments().stream()
          .map(comment -> comment.author.getId())
          .filter(
              a ->
                  !a.equals(currentUser.getAccountId())
                      && (ignoreVoteCheck || !maxCrApprovers.contains(a)))
          .forEach(repliedToUsers::add);
    }
    ImmutableSet<Account.Id> possibleUsersToAdd = approvalsUtil.getReviewers(changeNotes).all();
    SetView<Account.Id> usersToAdd = Sets.intersection(possibleUsersToAdd, repliedToUsers);

    for (Account.Id user : usersToAdd) {
      addToAttentionSet(
          bu, changeNotes, user, "Someone else replied on a comment you posted", false);
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
   * Bots don't process automatic rules, the only attention set change they do is this rule: Add
   * owner and uploader when a bot votes negatively on the current patch set, but only if the change
   * is open.
   */
  private void botsWithNegativeLabelsAddOwnerAndUploader(
      BatchUpdate bu, PostReviewOp postReviewOp, ChangeNotes changeNotes) {
    if (changeNotes.getChange().isClosed()) {
      return;
    }

    AttentionSetUpdateCondition condition =
        () -> postReviewOp.getResult().updatedAnyNegativeVoteOnCurrentPatchSet();

    Account.Id owner = changeNotes.getChange().getOwner();
    maybeAddToAttentionSet(
        bu, condition, changeNotes, owner, "A robot voted negatively on a label", false);

    Account.Id uploader = changeNotes.getCurrentPatchSet().uploader();
    if (!owner.equals(uploader)) {
      maybeAddToAttentionSet(
          bu, condition, changeNotes, uploader, "A robot voted negatively on a label", false);
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
    AddToAttentionSetOp addToAttentionSet = addToAttentionSetOpFactory.create(user, reason, notify);
    bu.addOp(changeNotes.getChangeId(), addToAttentionSet);
  }

  /**
   * Adds the user to the attention set if the given condition is true.
   *
   * @param bu BatchUpdate to perform the updates to the attention set
   * @param condition condition that decides whether the attention set update should be performed
   * @param changeNotes current change
   * @param user user to add to the attention set
   * @param reason reason for adding
   * @param notify whether or not to notify about this addition
   */
  private void maybeAddToAttentionSet(
      BatchUpdate bu,
      AttentionSetUpdateCondition condition,
      ChangeNotes changeNotes,
      Account.Id user,
      String reason,
      boolean notify) {
    AddToAttentionSetOp addToAttentionSet =
        addToAttentionSetOpFactory.create(user, reason, notify).setCondition(condition);
    bu.addOp(changeNotes.getChangeId(), addToAttentionSet);
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

  /**
   * Removes the user from the attention set if the given condition is true.
   *
   * @param bu BatchUpdate to perform the updates to the attention set.
   * @param condition condition that decides whether the attention set update should be performed
   * @param changeNotes current change.
   * @param user user to add remove from the attention set.
   * @param reason reason for removing.
   * @param notify whether or not to notify about this removal.
   */
  private void maybeRemoveFromAttentionSet(
      BatchUpdate bu,
      AttentionSetUpdateCondition condition,
      ChangeNotes changeNotes,
      Account.Id user,
      String reason,
      boolean notify) {
    RemoveFromAttentionSetOp removeFromAttentionSetOp =
        removeFromAttentionSetOpFactory.create(user, reason, notify).setCondition(condition);
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
    try {
      Account.Id attentionUserId =
          getAccountIdAndValidateUser(
              changeNotes, add.user, accountsChangedInCommit, AttentionSetUpdate.Operation.ADD);
      addToAttentionSet(bu, changeNotes, attentionUserId, add.reason, false);
    } catch (AccountResolver.UnresolvableAccountException ex) {
      // This happens only when the account doesn't exist. Silently ignore it. If we threw an error
      // message here, then it would be possible to probe whether an account exists.
    } catch (AuthException ex) {
      // adding users without permission to the attention set should fail silently.
      logger.atFine().log("%s", ex.getMessage());
    }
  }

  private void removeFromAttentionSet(
      BatchUpdate bu,
      ChangeNotes changeNotes,
      AttentionSetInput remove,
      Set<Account.Id> accountsChangedInCommit)
      throws BadRequestException, IOException, PermissionBackendException,
          UnprocessableEntityException, ConfigInvalidException {
    AttentionSetUtil.validateInput(remove);
    try {
      Account.Id attentionUserId =
          getAccountIdAndValidateUser(
              changeNotes,
              remove.user,
              accountsChangedInCommit,
              AttentionSetUpdate.Operation.REMOVE);
      removeFromAttentionSet(bu, changeNotes, attentionUserId, remove.reason, false);
    } catch (AccountResolver.UnresolvableAccountException ex) {
      // This happens only when the account doesn't exist. Silently ignore it. If we threw an error
      // message here, then it would be possible to probe whether an account exists.
    } catch (AuthException ex) {
      // this should never happen since removing users with permissions should work.
      logger.atSevere().log("%s", ex.getMessage());
    }
  }

  private Account.Id getAccountId(
      ChangeNotes changeNotes, String user, AttentionSetUpdate.Operation operation)
      throws ConfigInvalidException, IOException, UnprocessableEntityException,
          PermissionBackendException, AuthException {
    Account.Id attentionUserId = accountResolver.resolve(user).asUnique().account().id();
    try {
      permissionBackend
          .absentUser(attentionUserId)
          .change(changeNotes)
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      // If the change is private, it is okay to add the user to the attention set since that
      // person will be granted visibility when a reviewer.
      if (!changeNotes.getChange().isPrivate()) {

        // Removing users without access is allowed, adding is not allowed
        if (operation == AttentionSetUpdate.Operation.ADD) {
          throw new AuthException(
              "Can't modify attention set: Read not permitted for " + attentionUserId, e);
        }
      }
    }
    return attentionUserId;
  }

  private Account.Id getAccountIdAndValidateUser(
      ChangeNotes changeNotes,
      String user,
      Set<Account.Id> accountsChangedInCommit,
      AttentionSetUpdate.Operation operation)
      throws ConfigInvalidException, IOException, PermissionBackendException,
          UnprocessableEntityException, BadRequestException, AuthException {
    try {
      Account.Id attentionUserId = getAccountId(changeNotes, user, operation);
      if (accountsChangedInCommit.contains(attentionUserId)) {
        throw new BadRequestException(
            String.format(
                "%s can not be added/removed twice, and can not be added and "
                    + "removed at the same time",
                user));
      }
      accountsChangedInCommit.add(attentionUserId);
      return attentionUserId;
    } catch (AccountResolver.UnresolvableAccountException ex) {
      // This can only happen if this user can't see the account or the account doesn't exist.
      // Silently modify the account's attention set anyway, if the account exists.
      return accountResolver.resolveIgnoreVisibility(user).asUnique().account().id();
    }
  }
}
