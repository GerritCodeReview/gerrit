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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.permissions.AbstractLabelPermission.ForUser.ON_BEHALF_OF;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.ImpersonationPermissionMode;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.CommentsValidator;
import com.google.gerrit.server.change.ModifyReviewersEmail;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerModifier;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModification;
import com.google.gerrit.server.change.ReviewerOp.Result;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdates;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class PostReview implements RestModifyView<RevisionResource, ReviewInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  private static class Metrics {
    final Counter1<String> draftHandling;

    @Inject
    Metrics(MetricMaker metricMaker) {
      draftHandling =
          metricMaker.newCounter(
              "change/post_review/draft_handling",
              new Description(
                      "Total number of draft handling option "
                          + "(KEEP, PUBLISH, PUBLISH_ALL_REVISIONS) "
                          + "selected by users while posting a review.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("type", Metadata.Builder::eventType)
                  .description(
                      "The type of the draft handling option"
                          + " (KEEP, PUBLISH, PUBLISH_ALL_REVISIONS).")
                  .build());
    }
  }

  private static final String ERROR_ADDING_REVIEWER = "error adding reviewer";
  public static final String ERROR_WIP_READY_MUTUALLY_EXCLUSIVE =
      "work_in_progress and ready are mutually exclusive";

  private final RetryHelper retryHelper;
  private final PostReviewOp.Factory postReviewOpFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final AccountCache accountCache;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;
  private final ReviewerModifier reviewerModifier;
  private final Metrics metrics;
  private final ModifyReviewersEmail modifyReviewersEmail;
  private final NotifyResolver notifyResolver;
  private final WorkInProgressOp.Factory workInProgressOpFactory;
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;

  private final ReplyAttentionSetUpdates replyAttentionSetUpdates;
  private final ReviewerAdded reviewerAdded;
  private final boolean strictLabels;
  private final ChangeJson.Factory changeJsonFactory;
  private final CommentsValidator commentsValidator;
  private final CommitUtil commitUtil;

  @Inject
  PostReview(
      RetryHelper retryHelper,
      PostReviewOp.Factory postReviewOpFactory,
      ChangeResource.Factory changeResourceFactory,
      AccountCache accountCache,
      ApprovalsUtil approvalsUtil,
      AccountResolver accountResolver,
      ReviewerModifier reviewerModifier,
      Metrics metrics,
      ModifyReviewersEmail modifyReviewersEmail,
      NotifyResolver notifyResolver,
      @GerritServerConfig Config gerritConfig,
      WorkInProgressOp.Factory workInProgressOpFactory,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      ReplyAttentionSetUpdates replyAttentionSetUpdates,
      ReviewerAdded reviewerAdded,
      ChangeJson.Factory changeJsonFactory,
      CommentsValidator commentsValidator,
      CommitUtil commitUtil) {
    this.retryHelper = retryHelper;
    this.postReviewOpFactory = postReviewOpFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.accountCache = accountCache;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
    this.reviewerModifier = reviewerModifier;
    this.metrics = metrics;
    this.modifyReviewersEmail = modifyReviewersEmail;
    this.notifyResolver = notifyResolver;
    this.workInProgressOpFactory = workInProgressOpFactory;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.replyAttentionSetUpdates = replyAttentionSetUpdates;
    this.reviewerAdded = reviewerAdded;
    this.strictLabels = gerritConfig.getBoolean("change", "strictLabels", false);
    this.changeJsonFactory = changeJsonFactory;
    this.commentsValidator = commentsValidator;
    this.commitUtil = commitUtil;
  }

  @Override
  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input)
      throws RestApiException,
          UpdateException,
          IOException,
          PermissionBackendException,
          ConfigInvalidException,
          PatchListNotAvailableException {
    return apply(revision, input, TimeUtil.now());
  }

  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input, Instant ts)
      throws RestApiException,
          UpdateException,
          IOException,
          PermissionBackendException,
          ConfigInvalidException,
          PatchListNotAvailableException {
    // Respect timestamp, but truncate at change created-on time.
    ts = Ordering.natural().max(ts, revision.getChange().getCreatedOn());
    if (revision.getEdit().isPresent()) {
      throw new ResourceConflictException("cannot post review on edit");
    }
    ProjectState projectState =
        projectCache.get(revision.getProject()).orElseThrow(illegalState(revision.getProject()));
    LabelTypes labelTypes = projectState.getLabelTypes(revision.getNotes());

    logger.atFine().log("strict label checking is %s", (strictLabels ? "enabled" : "disabled"));

    metrics.draftHandling.increment(input.drafts == null ? "N/A" : input.drafts.name());
    input.drafts = firstNonNull(input.drafts, DraftHandling.KEEP);
    logger.atFine().log("draft handling = %s", input.drafts);

    if (input.onBehalfOf != null) {
      revision = onBehalfOf(revision, labelTypes, input);
    }
    if (input.labels != null) {
      checkLabels(revision, labelTypes, input.labels);
    }
    if (input.comments != null) {
      input.comments = cleanUpComments(input.comments);
      commentsValidator.checkComments(revision, input.comments);
    }

    if (input.notify == null) {
      input.notify = defaultNotify(revision.getChange(), input);
    }
    logger.atFine().log("notify handling = %s", input.notify);

    Map<String, ReviewerResult> reviewerJsonResults = null;
    List<ReviewerModification> reviewerResults = Lists.newArrayList();
    boolean hasError = false;
    boolean confirm = false;
    if (input.reviewers != null) {
      reviewerJsonResults = Maps.newHashMap();
      for (ReviewerInput reviewerInput : input.reviewers) {
        ReviewerModification result =
            reviewerModifier.prepare(revision.getNotes(), revision.getUser(), reviewerInput, true);
        reviewerJsonResults.put(reviewerInput.reviewer, result.result);
        if (result.result.error != null) {
          logger.atFine().log(
              "Adding %s as reviewer failed: %s", reviewerInput.reviewer, result.result.error);
          hasError = true;
          continue;
        }
        if (result.result.confirm != null) {
          logger.atFine().log(
              "Adding %s as reviewer requires confirmation", reviewerInput.reviewer);
          confirm = true;
          continue;
        }
        logger.atFine().log("Adding %s as reviewer was prepared", reviewerInput.reviewer);
        reviewerResults.add(result);
      }
    }

    ReviewResult output = new ReviewResult();
    output.reviewers = reviewerJsonResults;
    if (hasError || confirm) {
      output.error = ERROR_ADDING_REVIEWER;
      return Response.withStatusCode(SC_BAD_REQUEST, output);
    }
    output.labels = input.labels;

    Account account = revision.getUser().asIdentifiedUser().getAccount();
    boolean ccOrReviewer = false;
    if (input.labels != null && !input.labels.isEmpty()) {
      ccOrReviewer = input.labels.values().stream().anyMatch(v -> v != 0);
      if (ccOrReviewer) {
        logger.atFine().log("calling user is cc/reviewer on the change due to voting on a label");
      }
    }

    if (!ccOrReviewer) {
      // Check if user was already CCed or reviewing prior to this review.
      ReviewerSet currentReviewers =
          approvalsUtil.getReviewers(revision.getChangeResource().getNotes());
      ccOrReviewer = currentReviewers.all().contains(account.id());
      if (ccOrReviewer) {
        logger.atFine().log("calling user is already cc/reviewer on the change");
      }
    }

    for (ReviewerModification reviewerResult : reviewerResults) {
      reviewerResult.op.suppressEmail(); // Send a single batch email below.
      reviewerResult.op.suppressEvent(); // Send events below, if possible as batch.
      if (!ccOrReviewer && reviewerResult.reviewers.contains(account)) {
        logger.atFine().log("calling user is explicitly added as reviewer or CC");
        ccOrReviewer = true;
      }
    }

    // Notify based on ReviewInput, ignoring the notify settings from any ReviewerInputs.
    NotifyResolver.Result notify = notifyResolver.resolve(input.notify, input.notifyDetails);

    if ((input.ready || input.workInProgress)
        && didWorkInProgressChange(revision.getChange().isWorkInProgress(), input)) {
      if (input.ready && input.workInProgress) {
        output.error = ERROR_WIP_READY_MUTUALLY_EXCLUSIVE;
        return Response.withStatusCode(SC_BAD_REQUEST, output);
      }

      revision
          .getChangeResource()
          .permissions()
          .check(ChangePermission.TOGGLE_WORK_IN_PROGRESS_STATE);

      if (input.ready) {
        output.ready = true;
      }
    }

    BatchUpdates.Result batchUpdateResult =
        runBatchUpdate(projectState, revision, input, ts, notify, reviewerResults, ccOrReviewer);
    ChangeData cd =
        batchUpdateResult.getChangeData(revision.getProject(), revision.getChange().getId());
    for (ReviewerModification reviewerResult : reviewerResults) {
      reviewerResult.gatherResults(cd);
    }

    // Sending emails and events from ReviewersOps was suppressed so we can send a single batch
    // email/event here.
    batchEmailReviewers(revision.getUser(), revision.getChange(), reviewerResults, notify);
    batchReviewerEvents(revision.getUser(), cd, revision.getPatchSet(), reviewerResults, ts);

    if (input.responseFormatOptions != null) {
      output.changeInfo = changeJsonFactory.create(input.responseFormatOptions).format(cd);
    } else {
      output.changeInfo = changeJsonFactory.noOptions().format(cd);
    }

    return Response.ok(output);
  }

  private BatchUpdates.Result runBatchUpdate(
      ProjectState projectState,
      RevisionResource revision,
      ReviewInput input,
      Instant ts,
      NotifyResolver.Result notify,
      List<ReviewerModification> reviewerResults,
      boolean ccOrReviewer)
      throws UpdateException, RestApiException {
    return retryHelper
        .changeUpdate(
            "batchUpdate",
            updateFactory -> {
              try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
                try (BatchUpdate bu =
                    updateFactory.create(
                        revision.getChange().getProject(), revision.getUser(), ts)) {
                  bu.setNotify(notify);

                  // Apply reviewer changes first. Revision emails should be sent to the
                  // updated set of reviewers. Also keep track of whether the user added
                  // themselves as a reviewer or to the CC list.
                  logger.atFine().log("adding reviewer additions");
                  reviewerResults.forEach(
                      reviewerResult -> bu.addOp(revision.getChange().getId(), reviewerResult.op));

                  if (!ccOrReviewer) {
                    // User posting this review isn't currently in the reviewer or CC list,
                    // isn't being explicitly added, and isn't voting on any label.
                    // Automatically CC them on this change so they receive replies.
                    logger.atFine().log("CCing calling user");
                    ReviewerModification selfAddition =
                        reviewerModifier.ccCurrentUser(revision.getUser(), revision);
                    selfAddition.op.suppressEmail();
                    selfAddition.op.suppressEvent();
                    bu.addOp(revision.getChange().getId(), selfAddition.op);
                  }

                  // Add WorkInProgressOp if requested.
                  if ((input.ready || input.workInProgress)
                      && didWorkInProgressChange(revision.getChange().isWorkInProgress(), input)) {
                    logger.atFine().log("setting work-in-progress to %s", input.workInProgress);
                    WorkInProgressOp wipOp =
                        workInProgressOpFactory.create(
                            input.workInProgress, new WorkInProgressOp.Input());
                    if (input.ready && revision.getChange().getRevertOf() != null) {
                      commitUtil.addChangeRevertedNotificationOps(
                          bu,
                          revision.getChange().getRevertOf(),
                          revision.getChange().getId(),
                          revision.getChange().getKey().get().substring(1));
                    }
                    wipOp.suppressEmail();
                    bu.addOp(revision.getChange().getId(), wipOp);
                  }

                  // Add the review ops.
                  logger.atFine().log("posting review");
                  PostReviewOp postReviewOp =
                      postReviewOpFactory.create(
                          projectState,
                          revision.getPatchSet().id(),
                          input,
                          revision.getAccountId());
                  bu.addOp(revision.getChange().getId(), postReviewOp);

                  // Adjust the attention set based on the input
                  replyAttentionSetUpdates.updateAttentionSetOnPostReview(
                      bu, postReviewOp, revision.getNotes(), input, revision.getUser());

                  return bu.execute();
                }
              }
            })
        .call();
  }

  private boolean didWorkInProgressChange(boolean currentWorkInProgress, ReviewInput input) {
    return input.ready == currentWorkInProgress || input.workInProgress != currentWorkInProgress;
  }

  private NotifyHandling defaultNotify(Change c, ReviewInput in) {
    boolean workInProgress = c.isWorkInProgress();
    if (in.workInProgress) {
      workInProgress = true;
    }
    if (in.ready) {
      workInProgress = false;
    }

    if (ChangeMessagesUtil.isAutogenerated(in.tag)) {
      // Autogenerated comments default to lower notify levels.
      return workInProgress ? NotifyHandling.OWNER : NotifyHandling.OWNER_REVIEWERS;
    }

    if (workInProgress && !c.hasReviewStarted()) {
      // If review hasn't started we want to eliminate notifications, no matter who the author is.
      return NotifyHandling.NONE;
    }

    // Otherwise, it's either a non-WIP change, or a WIP change where review has started. Notify
    // everyone.
    return NotifyHandling.ALL;
  }

  private void batchEmailReviewers(
      CurrentUser user,
      Change change,
      List<ReviewerModification> reviewerModifications,
      NotifyResolver.Result notify) {
    try (TraceContext.TraceTimer ignored =
        TraceContext.newTimer(
            getClass().getSimpleName() + "#batchEmailReviewers", Metadata.empty())) {
      List<Account.Id> to = new ArrayList<>();
      List<Account.Id> cc = new ArrayList<>();
      List<Account.Id> removed = new ArrayList<>();
      List<Address> toByEmail = new ArrayList<>();
      List<Address> ccByEmail = new ArrayList<>();
      List<Address> removedByEmail = new ArrayList<>();
      for (ReviewerModification modification : reviewerModifications) {
        Result reviewAdditionResult = modification.op.getResult();
        if (modification.state() == ReviewerState.REVIEWER
            && (!reviewAdditionResult.addedReviewers().isEmpty()
                || !reviewAdditionResult.addedReviewersByEmail().isEmpty())) {
          to.addAll(modification.reviewers.stream().map(Account::id).collect(toImmutableSet()));
          toByEmail.addAll(modification.reviewersByEmail);
        } else if (modification.state() == ReviewerState.CC
            && (!reviewAdditionResult.addedCCs().isEmpty()
                || !reviewAdditionResult.addedCCsByEmail().isEmpty())) {
          cc.addAll(modification.reviewers.stream().map(Account::id).collect(toImmutableSet()));
          ccByEmail.addAll(modification.reviewersByEmail);
        } else if (modification.state() == ReviewerState.REMOVED
            && (reviewAdditionResult.deletedReviewer().isPresent()
                || reviewAdditionResult.deletedReviewerByEmail().isPresent())) {
          reviewAdditionResult.deletedReviewer().ifPresent(d -> removed.add(d));
          reviewAdditionResult.deletedReviewerByEmail().ifPresent(d -> removedByEmail.add(d));
        }
      }
      modifyReviewersEmail.emailReviewersAsync(
          user.asIdentifiedUser(),
          change,
          to,
          cc,
          removed,
          toByEmail,
          ccByEmail,
          removedByEmail,
          notify);
    }
  }

  private void batchReviewerEvents(
      CurrentUser user,
      ChangeData cd,
      PatchSet patchSet,
      List<ReviewerModification> reviewerModifications,
      Instant when) {
    List<AccountState> newlyAddedReviewers = new ArrayList<>();

    // There are no events for CCs and reviewers added/deleted by email.
    for (ReviewerModification modification : reviewerModifications) {
      Result reviewerAdditionResult = modification.op.getResult();
      if (modification.state() == ReviewerState.REVIEWER) {
        newlyAddedReviewers.addAll(
            reviewerAdditionResult.addedReviewers().stream()
                .map(psa -> psa.accountId())
                .map(accountId -> accountCache.get(accountId))
                .flatMap(Streams::stream)
                .collect(toList()));
      } else if (modification.state() == ReviewerState.REMOVED) {
        // There is no batch event for reviewer removals, hence fire the event for each
        // modification that deleted a reviewer immediately.
        modification.op.sendEvent();
      }
    }

    // Fire a batch event for all newly added reviewers.
    reviewerAdded.fire(cd, patchSet, newlyAddedReviewers, user.asIdentifiedUser().state(), when);
  }

  private RevisionResource onBehalfOf(RevisionResource rev, LabelTypes labelTypes, ReviewInput in)
      throws BadRequestException,
          AuthException,
          UnprocessableEntityException,
          ResourceConflictException,
          PermissionBackendException,
          IOException,
          ConfigInvalidException {
    logger.atFine().log("request is executed on behalf of %s", in.onBehalfOf);

    if (in.labels == null || in.labels.isEmpty()) {
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }
    if (in.drafts != DraftHandling.KEEP) {
      throw new AuthException("not allowed to modify other user's drafts");
    }

    logger.atFine().log("label input: %s", in.labels);

    CurrentUser caller = rev.getUser();
    PermissionBackend.ForChange perm = rev.permissions();
    Iterator<Map.Entry<String, Short>> itr = in.labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      Optional<LabelType> type = labelTypes.byLabel(ent.getKey());
      if (!type.isPresent()) {
        logger.atFine().log("label %s not found", ent.getKey());
        if (strictLabels) {
          throw new BadRequestException(
              String.format("label \"%s\" is not a configured label", ent.getKey()));
        }
        logger.atFine().log("ignoring input for unknown label %s", ent.getKey());
        itr.remove();
        continue;
      }

      if (caller.isInternalUser()) {
        logger.atFine().log(
            "skipping on behalf of permission check for label %s"
                + " because caller is an internal user",
            type.get().getName());
      } else {
        try {
          perm.check(new LabelPermission.WithValue(ON_BEHALF_OF, type.get(), ent.getValue()));
        } catch (AuthException e) {
          throw new AuthException(
              String.format(
                  "not permitted to modify label \"%s\" on behalf of \"%s\"",
                  type.get().getName(), in.onBehalfOf),
              e);
        }
      }
    }
    if (in.labels.isEmpty()) {
      logger.atFine().log("labels are empty after unknown labels have been removed");
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }

    IdentifiedUser reviewer =
        accountResolver
            .resolve(in.onBehalfOf)
            .asUniqueUserOnBehalfOf(caller, ImpersonationPermissionMode.THIS_USER);
    logger.atFine().log("on behalf of user was resolved to %s", reviewer.getLoggableName());
    try {
      permissionBackend.user(reviewer).change(rev.getNotes()).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new ResourceConflictException(
          String.format("on_behalf_of account %s cannot see change", reviewer.getAccountId()), e);
    }

    return new RevisionResource(
        changeResourceFactory.create(rev.getNotes(), reviewer), rev.getPatchSet());
  }

  private void checkLabels(RevisionResource rsrc, LabelTypes labelTypes, Map<String, Short> labels)
      throws BadRequestException, AuthException, PermissionBackendException {
    logger.atFine().log("checking label input: %s", labels);

    PermissionBackend.ForChange perm = rsrc.permissions();
    Iterator<Map.Entry<String, Short>> itr = labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      Optional<LabelType> lt = labelTypes.byLabel(ent.getKey());
      if (!lt.isPresent()) {
        logger.atFine().log("label %s not found", ent.getKey());
        if (strictLabels) {
          throw new BadRequestException(
              String.format("label \"%s\" is not a configured label", ent.getKey()));
        }
        logger.atFine().log("ignoring input for unknown label %s", ent.getKey());
        itr.remove();
        continue;
      }

      if (ent.getValue() == null || ent.getValue() == 0) {
        // Always permit 0, even if it is not within range.
        // Later null/0 will be deleted and revoke the label.
        continue;
      }

      if (lt.get().getValue(ent.getValue()) == null) {
        logger.atFine().log("label value %s not found", ent.getValue());
        if (strictLabels) {
          throw new BadRequestException(
              String.format("label \"%s\": %d is not a valid value", ent.getKey(), ent.getValue()));
        }
        logger.atFine().log(
            "ignoring input for label %s because label value is unknown", ent.getKey());
        itr.remove();
        continue;
      }

      short val = ent.getValue();
      try {
        perm.check(new LabelPermission.WithValue(lt.get(), val));
      } catch (AuthException e) {
        throw new AuthException(
            String.format("Applying label \"%s\": %d is restricted", lt.get().getName(), val), e);
      }
    }
  }

  private static <T extends com.google.gerrit.extensions.client.Comment>
      Map<String, List<T>> cleanUpComments(Map<String, List<T>> commentsPerPath) {
    Map<String, List<T>> cleanedUpCommentMap = new HashMap<>();
    for (Map.Entry<String, List<T>> e : commentsPerPath.entrySet()) {
      String path = e.getKey();
      List<T> comments = e.getValue();

      if (comments == null) {
        continue;
      }

      List<T> cleanedUpComments = cleanUpComments(comments);
      if (!cleanedUpComments.isEmpty()) {
        cleanedUpCommentMap.put(path, cleanedUpComments);
      }
    }
    return cleanedUpCommentMap;
  }

  private static <T extends com.google.gerrit.extensions.client.Comment> List<T> cleanUpComments(
      List<T> comments) {
    return comments.stream()
        .filter(Objects::nonNull)
        .filter(comment -> !Strings.nullToEmpty(comment.message).trim().isEmpty())
        .collect(toList());
  }

  /**
   * Used to compare existing {@link HumanComment}-s with {@link CommentInput} comments by copying
   * only the fields to compare.
   */
  @AutoValue
  abstract static class CommentSetEntry {
    private static CommentSetEntry create(
        String filename,
        int patchSetId,
        Integer line,
        Side side,
        HashCode message,
        Comment.Range range) {
      return new AutoValue_PostReview_CommentSetEntry(
          filename, patchSetId, line, side, message, range);
    }

    public static CommentSetEntry create(Comment comment) {
      return create(
          comment.key.filename,
          comment.key.patchSetId,
          comment.lineNbr,
          Side.fromShort(comment.side),
          Hashing.murmur3_128().hashString(comment.message, UTF_8),
          comment.range);
    }

    abstract String filename();

    abstract int patchSetId();

    @Nullable
    abstract Integer line();

    abstract Side side();

    abstract HashCode message();

    @Nullable
    abstract Comment.Range range();
  }
}
