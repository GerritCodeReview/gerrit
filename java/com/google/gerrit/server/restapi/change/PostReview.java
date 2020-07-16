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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.server.CommentsUtil.setCommentCommitId;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AddReviewersEmail;
import com.google.gerrit.server.change.AddReviewersOp.Result;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAddition;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class PostReview implements RestModifyView<RevisionResource, ReviewInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ERROR_ADDING_REVIEWER = "error adding reviewer";
  public static final String ERROR_WIP_READY_MUTUALLY_EXCLUSIVE =
      "work_in_progress and ready are mutually exclusive";

  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  private final BatchUpdate.Factory updateFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CommentsUtil commentsUtil;
  private final PublishCommentUtil publishCommentUtil;
  private final PatchSetUtil psUtil;
  private final PatchListCache patchListCache;
  private final AccountResolver accountResolver;
  private final EmailReviewComments.Factory email;
  private final CommentAdded commentAdded;
  private final ReviewerAdder reviewerAdder;
  private final AddReviewersEmail addReviewersEmail;
  private final NotifyResolver notifyResolver;
  private final WorkInProgressOp.Factory workInProgressOpFactory;
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final PluginSetContext<CommentValidator> commentValidators;
  private final PostReviewAttentionSet postReviewAttentionSet;
  private final boolean strictLabels;

  @Inject
  PostReview(
      BatchUpdate.Factory updateFactory,
      ChangeResource.Factory changeResourceFactory,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PublishCommentUtil publishCommentUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      AccountResolver accountResolver,
      EmailReviewComments.Factory email,
      CommentAdded commentAdded,
      ReviewerAdder reviewerAdder,
      AddReviewersEmail addReviewersEmail,
      NotifyResolver notifyResolver,
      @GerritServerConfig Config gerritConfig,
      WorkInProgressOp.Factory workInProgressOpFactory,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      PluginSetContext<CommentValidator> commentValidators,
      PostReviewAttentionSet postReviewAttentionSet) {
    this.updateFactory = updateFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.changeDataFactory = changeDataFactory;
    this.commentsUtil = commentsUtil;
    this.publishCommentUtil = publishCommentUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.accountResolver = accountResolver;
    this.email = email;
    this.commentAdded = commentAdded;
    this.reviewerAdder = reviewerAdder;
    this.addReviewersEmail = addReviewersEmail;
    this.notifyResolver = notifyResolver;
    this.workInProgressOpFactory = workInProgressOpFactory;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.commentValidators = commentValidators;
    this.postReviewAttentionSet = postReviewAttentionSet;
    this.strictLabels = gerritConfig.getBoolean("change", "strictLabels", false);
  }

  @Override
  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input)
      throws RestApiException, UpdateException, IOException, PermissionBackendException,
          ConfigInvalidException, PatchListNotAvailableException {
    return apply(revision, input, TimeUtil.nowTs());
  }

  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input, Timestamp ts)
      throws RestApiException, UpdateException, IOException, PermissionBackendException,
          ConfigInvalidException, PatchListNotAvailableException {
    // Respect timestamp, but truncate at change created-on time.
    ts = Ordering.natural().max(ts, revision.getChange().getCreatedOn());
    if (revision.getEdit().isPresent()) {
      throw new ResourceConflictException("cannot post review on edit");
    }
    ProjectState projectState =
        projectCache.get(revision.getProject()).orElseThrow(illegalState(revision.getProject()));
    LabelTypes labelTypes = projectState.getLabelTypes(revision.getNotes());

    logger.atFine().log("strict label checking is %s", (strictLabels ? "enabled" : "disabled"));

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
      checkComments(revision, input.comments);
    }
    if (input.robotComments != null) {
      input.robotComments = cleanUpComments(input.robotComments);
      checkRobotComments(revision, input.robotComments);
    }

    if (input.notify == null) {
      input.notify = defaultNotify(revision.getChange(), input);
    }
    logger.atFine().log("notify handling = %s", input.notify);

    Map<String, AddReviewerResult> reviewerJsonResults = null;
    List<ReviewerAddition> reviewerResults = Lists.newArrayList();
    boolean hasError = false;
    boolean confirm = false;
    if (input.reviewers != null) {
      reviewerJsonResults = Maps.newHashMap();
      for (AddReviewerInput reviewerInput : input.reviewers) {
        ReviewerAddition result =
            reviewerAdder.prepare(revision.getNotes(), revision.getUser(), reviewerInput, true);
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

    try (BatchUpdate bu =
        updateFactory.create(revision.getChange().getProject(), revision.getUser(), ts)) {
      Account.Id id = revision.getUser().getAccountId();
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
        ccOrReviewer = currentReviewers.all().contains(id);
        if (ccOrReviewer) {
          logger.atFine().log("calling user is already cc/reviewer on the change");
        }
      }

      // Apply reviewer changes first. Revision emails should be sent to the
      // updated set of reviewers. Also keep track of whether the user added
      // themselves as a reviewer or to the CC list.
      logger.atFine().log("adding reviewer additions");
      for (ReviewerAddition reviewerResult : reviewerResults) {
        reviewerResult.op.suppressEmail(); // Send a single batch email below.
        bu.addOp(revision.getChange().getId(), reviewerResult.op);
        if (!ccOrReviewer && reviewerResult.reviewers.contains(id)) {
          logger.atFine().log("calling user is explicitly added as reviewer or CC");
          ccOrReviewer = true;
        }
      }

      if (!ccOrReviewer) {
        // User posting this review isn't currently in the reviewer or CC list,
        // isn't being explicitly added, and isn't voting on any label.
        // Automatically CC them on this change so they receive replies.
        logger.atFine().log("CCing calling user");
        ReviewerAddition selfAddition = reviewerAdder.ccCurrentUser(revision.getUser(), revision);
        selfAddition.op.suppressEmail();
        bu.addOp(revision.getChange().getId(), selfAddition.op);
      }

      // Add WorkInProgressOp if requested.
      if (input.ready || input.workInProgress) {
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

        logger.atFine().log("setting work-in-progress to %s", input.workInProgress);
        WorkInProgressOp wipOp =
            workInProgressOpFactory.create(input.workInProgress, new WorkInProgressOp.Input());
        wipOp.suppressEmail();
        bu.addOp(revision.getChange().getId(), wipOp);
      }

      // Add the review op.
      logger.atFine().log("posting review");
      bu.addOp(
          revision.getChange().getId(), new Op(projectState, revision.getPatchSet().id(), input));

      // Notify based on ReviewInput, ignoring the notify settings from any AddReviewerInputs.
      NotifyResolver.Result notify = notifyResolver.resolve(input.notify, input.notifyDetails);
      bu.setNotify(notify);

      // Adjust the attention set based on the input
      postReviewAttentionSet.updateAttentionSet(bu, revision, input, reviewerResults);
      bu.execute();

      // Re-read change to take into account results of the update.
      ChangeData cd = changeDataFactory.create(revision.getProject(), revision.getChange().getId());
      for (ReviewerAddition reviewerResult : reviewerResults) {
        reviewerResult.gatherResults(cd);
      }

      // Sending from AddReviewersOp was suppressed so we can send a single batch email here.
      batchEmailReviewers(revision.getUser(), revision.getChange(), reviewerResults, notify);
    }

    return Response.ok(output);
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
      List<ReviewerAddition> reviewerAdditions,
      NotifyResolver.Result notify) {
    try (TraceContext.TraceTimer ignored = newTimer("batchEmailReviewers")) {
      List<Account.Id> to = new ArrayList<>();
      List<Account.Id> cc = new ArrayList<>();
      List<Address> toByEmail = new ArrayList<>();
      List<Address> ccByEmail = new ArrayList<>();
      for (ReviewerAddition addition : reviewerAdditions) {
        Result reviewAdditionResult = addition.op.getResult();
        if (addition.state() == ReviewerState.REVIEWER
            && (!reviewAdditionResult.addedReviewers().isEmpty()
                || !reviewAdditionResult.addedReviewersByEmail().isEmpty())) {
          to.addAll(addition.reviewers);
          toByEmail.addAll(addition.reviewersByEmail);
        } else if (addition.state() == ReviewerState.CC
            && (!reviewAdditionResult.addedCCs().isEmpty()
                || !reviewAdditionResult.addedCCsByEmail().isEmpty())) {
          cc.addAll(addition.reviewers);
          ccByEmail.addAll(addition.reviewersByEmail);
        }
      }
      addReviewersEmail.emailReviewersAsync(
          user.asIdentifiedUser(), change, to, cc, toByEmail, ccByEmail, notify);
    }
  }

  private RevisionResource onBehalfOf(RevisionResource rev, LabelTypes labelTypes, ReviewInput in)
      throws BadRequestException, AuthException, UnprocessableEntityException,
          PermissionBackendException, IOException, ConfigInvalidException {
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
      LabelType type = labelTypes.byLabel(ent.getKey());
      if (type == null) {
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
            type.getName());
      } else {
        try {
          perm.check(new LabelPermission.WithValue(ON_BEHALF_OF, type, ent.getValue()));
        } catch (AuthException e) {
          throw new AuthException(
              String.format(
                  "not permitted to modify label \"%s\" on behalf of \"%s\"",
                  type.getName(), in.onBehalfOf),
              e);
        }
      }
    }
    if (in.labels.isEmpty()) {
      logger.atFine().log("labels are empty after unknown labels have been removed");
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }

    IdentifiedUser reviewer = accountResolver.resolve(in.onBehalfOf).asUniqueUserOnBehalfOf(caller);
    logger.atFine().log("on behalf of user was resolved to %s", reviewer.getLoggableName());
    try {
      permissionBackend.user(reviewer).change(rev.getNotes()).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
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
      LabelType lt = labelTypes.byLabel(ent.getKey());
      if (lt == null) {
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

      if (lt.getValue(ent.getValue()) == null) {
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
        perm.check(new LabelPermission.WithValue(lt, val));
      } catch (AuthException e) {
        throw new AuthException(
            String.format("Applying label \"%s\": %d is restricted", lt.getName(), val), e);
      }
    }
  }

  private static <T extends CommentInput> Map<String, List<T>> cleanUpComments(
      Map<String, List<T>> commentsPerPath) {
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

  private static <T extends CommentInput> List<T> cleanUpComments(List<T> comments) {
    return comments.stream()
        .filter(Objects::nonNull)
        .filter(comment -> !Strings.nullToEmpty(comment.message).trim().isEmpty())
        .collect(toList());
  }

  private TraceContext.TraceTimer newTimer(String method) {
    return TraceContext.newTimer(getClass().getSimpleName() + "#" + method, Metadata.empty());
  }

  private <T extends CommentInput> void checkComments(
      RevisionResource revision, Map<String, List<T>> commentsPerPath)
      throws BadRequestException, PatchListNotAvailableException {
    logger.atFine().log("checking comments");
    Set<String> revisionFilePaths = getAffectedFilePaths(revision);
    for (Map.Entry<String, List<T>> entry : commentsPerPath.entrySet()) {
      String path = entry.getKey();
      PatchSet.Id patchSetId = revision.getPatchSet().id();
      ensurePathRefersToAvailableOrMagicFile(path, revisionFilePaths, patchSetId);

      List<T> comments = entry.getValue();
      for (T comment : comments) {
        ensureLineIsNonNegative(comment.line, path);
        ensureCommentNotOnMagicFilesOfAutoMerge(path, comment);
        ensureRangeIsValid(path, comment.range);
        ensureValidPatchsetLevelComment(path, comment);
      }
    }
  }

  private Set<String> getAffectedFilePaths(RevisionResource revision)
      throws PatchListNotAvailableException {
    ObjectId newId = revision.getPatchSet().commitId();
    DiffSummaryKey key =
        DiffSummaryKey.fromPatchListKey(
            PatchListKey.againstDefaultBase(newId, Whitespace.IGNORE_NONE));
    DiffSummary ds = patchListCache.getDiffSummary(key, revision.getProject());
    return new HashSet<>(ds.getPaths());
  }

  private static void ensurePathRefersToAvailableOrMagicFile(
      String path, Set<String> availableFilePaths, PatchSet.Id patchSetId)
      throws BadRequestException {
    if (!availableFilePaths.contains(path) && !Patch.isMagic(path)) {
      throw new BadRequestException(
          String.format("file %s not found in revision %s", path, patchSetId));
    }
  }

  private static void ensureLineIsNonNegative(Integer line, String path)
      throws BadRequestException {
    if (line != null && line < 0) {
      throw new BadRequestException(
          String.format("negative line number %d not allowed on %s", line, path));
    }
  }

  private static <T extends CommentInput> void ensureCommentNotOnMagicFilesOfAutoMerge(
      String path, T comment) throws BadRequestException {
    if (Patch.isMagic(path) && comment.side == Side.PARENT && comment.parent == null) {
      throw new BadRequestException(String.format("cannot comment on %s on auto-merge", path));
    }
  }

  private static <T extends CommentInput> void ensureValidPatchsetLevelComment(
      String path, T comment) throws BadRequestException {
    if (path.equals(PATCHSET_LEVEL)
        && (comment.side != null || comment.range != null || comment.line != null)) {
      throw new BadRequestException("Patchset-level comments can't have side, range, or line");
    }
  }

  private void checkRobotComments(
      RevisionResource revision, Map<String, List<RobotCommentInput>> in)
      throws BadRequestException, PatchListNotAvailableException {
    logger.atFine().log("checking robot comments");
    for (Map.Entry<String, List<RobotCommentInput>> e : in.entrySet()) {
      String commentPath = e.getKey();
      for (RobotCommentInput c : e.getValue()) {
        ensureRobotIdIsSet(c.robotId, commentPath);
        ensureRobotRunIdIsSet(c.robotRunId, commentPath);
        ensureFixSuggestionsAreAddable(c.fixSuggestions, commentPath);
        // Size is validated later, in CommentLimitsValidator.
      }
    }
    checkComments(revision, in);
  }

  private static void ensureRobotIdIsSet(String robotId, String commentPath)
      throws BadRequestException {
    if (robotId == null) {
      throw new BadRequestException(
          String.format("robotId is missing for robot comment on %s", commentPath));
    }
  }

  private static void ensureRobotRunIdIsSet(String robotRunId, String commentPath)
      throws BadRequestException {
    if (robotRunId == null) {
      throw new BadRequestException(
          String.format("robotRunId is missing for robot comment on %s", commentPath));
    }
  }

  private static void ensureFixSuggestionsAreAddable(
      List<FixSuggestionInfo> fixSuggestionInfos, String commentPath) throws BadRequestException {
    if (fixSuggestionInfos == null) {
      return;
    }

    for (FixSuggestionInfo fixSuggestionInfo : fixSuggestionInfos) {
      ensureDescriptionIsSet(commentPath, fixSuggestionInfo.description);
      ensureFixReplacementsAreAddable(commentPath, fixSuggestionInfo.replacements);
    }
  }

  private static void ensureDescriptionIsSet(String commentPath, String description)
      throws BadRequestException {
    if (description == null) {
      throw new BadRequestException(
          String.format(
              "A description is required for the suggested fix of the robot comment on %s",
              commentPath));
    }
  }

  private static void ensureFixReplacementsAreAddable(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    ensureReplacementsArePresent(commentPath, fixReplacementInfos);

    for (FixReplacementInfo fixReplacementInfo : fixReplacementInfos) {
      ensureReplacementPathIsSetAndNotPatchsetLevel(commentPath, fixReplacementInfo.path);
      ensureRangeIsSet(commentPath, fixReplacementInfo.range);
      ensureRangeIsValid(commentPath, fixReplacementInfo.range);
      ensureReplacementStringIsSet(commentPath, fixReplacementInfo.replacement);
    }

    Map<String, List<FixReplacementInfo>> replacementsPerFilePath =
        fixReplacementInfos.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));
    for (List<FixReplacementInfo> sameFileReplacements : replacementsPerFilePath.values()) {
      ensureRangesDoNotOverlap(commentPath, sameFileReplacements);
    }
  }

  private static void ensureReplacementsArePresent(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    if (fixReplacementInfos == null || fixReplacementInfos.isEmpty()) {
      throw new BadRequestException(
          String.format(
              "At least one replacement is "
                  + "required for the suggested fix of the robot comment on %s",
              commentPath));
    }
  }

  private static void ensureReplacementPathIsSetAndNotPatchsetLevel(
      String commentPath, String replacementPath) throws BadRequestException {
    if (replacementPath == null) {
      throw new BadRequestException(
          String.format(
              "A file path must be given for the replacement of the robot comment on %s",
              commentPath));
    }
    if (replacementPath.equals(PATCHSET_LEVEL)) {
      throw new BadRequestException(
          String.format(
              "A file path must not be %s for the replacement of the robot comment on %s",
              PATCHSET_LEVEL, commentPath));
    }
  }

  private static void ensureRangeIsSet(String commentPath, Range range) throws BadRequestException {
    if (range == null) {
      throw new BadRequestException(
          String.format(
              "A range must be given for the replacement of the robot comment on %s", commentPath));
    }
  }

  private static void ensureRangeIsValid(String commentPath, Range range)
      throws BadRequestException {
    if (range == null) {
      return;
    }
    if (!range.isValid()) {
      throw new BadRequestException(
          String.format(
              "Range (%s:%s - %s:%s) is not valid for the comment on %s",
              range.startLine,
              range.startCharacter,
              range.endLine,
              range.endCharacter,
              commentPath));
    }
  }

  private static void ensureReplacementStringIsSet(String commentPath, String replacement)
      throws BadRequestException {
    if (replacement == null) {
      throw new BadRequestException(
          String.format(
              "A content for replacement "
                  + "must be indicated for the replacement of the robot comment on %s",
              commentPath));
    }
  }

  private static void ensureRangesDoNotOverlap(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    List<Range> sortedRanges =
        fixReplacementInfos.stream()
            .map(fixReplacementInfo -> fixReplacementInfo.range)
            .sorted()
            .collect(toList());

    int previousEndLine = 0;
    int previousOffset = -1;
    for (Range range : sortedRanges) {
      if (range.startLine < previousEndLine
          || (range.startLine == previousEndLine && range.startCharacter < previousOffset)) {
        throw new BadRequestException(
            String.format("Replacements overlap for the robot comment on %s", commentPath));
      }
      previousEndLine = range.endLine;
      previousOffset = range.endCharacter;
    }
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

  private class Op implements BatchUpdateOp {
    private final ProjectState projectState;
    private final PatchSet.Id psId;
    private final ReviewInput in;

    private IdentifiedUser user;
    private ChangeNotes notes;
    private PatchSet ps;
    private ChangeMessage message;
    private List<Comment> comments = new ArrayList<>();
    private List<LabelVote> labelDelta = new ArrayList<>();
    private Map<String, Short> approvals = new HashMap<>();
    private Map<String, Short> oldApprovals = new HashMap<>();

    private Op(ProjectState projectState, PatchSet.Id psId, ReviewInput in) {
      this.projectState = projectState;
      this.psId = psId;
      this.in = in;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceConflictException, UnprocessableEntityException, IOException,
            PatchListNotAvailableException, CommentsRejectedException {
      user = ctx.getIdentifiedUser();
      notes = ctx.getNotes();
      ps = psUtil.get(ctx.getNotes(), psId);
      List<RobotComment> newRobotComments =
          in.robotComments == null ? ImmutableList.of() : getNewRobotComments(ctx);
      boolean dirty = false;
      try (TraceContext.TraceTimer ignored = newTimer("insertComments")) {
        dirty |= insertComments(ctx, newRobotComments);
      }
      try (TraceContext.TraceTimer ignored = newTimer("insertRobotComments")) {
        dirty |= insertRobotComments(ctx, newRobotComments);
      }
      try (TraceContext.TraceTimer ignored = newTimer("updateLabels")) {
        dirty |= updateLabels(projectState, ctx);
      }
      try (TraceContext.TraceTimer ignored = newTimer("insertMessage")) {
        dirty |= insertMessage(ctx);
      }
      return dirty;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (message == null) {
        return;
      }
      NotifyResolver.Result notify = ctx.getNotify(notes.getChangeId());
      if (notify.shouldNotify()) {
        try {
          email
              .create(
                  notify,
                  notes,
                  ps,
                  user,
                  message,
                  comments,
                  in.message,
                  labelDelta,
                  ctx.getRepoView())
              .sendAsync();
        } catch (IOException ex) {
          throw new StorageException(
              String.format("Repository %s not found", ctx.getProject().get()), ex);
        }
      }
      commentAdded.fire(
          notes.getChange(),
          ps,
          user.state(),
          message.getMessage(),
          approvals,
          oldApprovals,
          ctx.getWhen());
    }

    private boolean insertComments(ChangeContext ctx, List<RobotComment> newRobotComments)
        throws UnprocessableEntityException, PatchListNotAvailableException,
            CommentsRejectedException {
      Map<String, List<CommentInput>> inputComments = in.comments;
      if (inputComments == null) {
        inputComments = Collections.emptyMap();
      }

      // HashMap instead of Collections.emptyMap() avoids warning about remove() on immutable
      // object.
      Map<String, HumanComment> drafts = new HashMap<>();
      // If there are inputComments we need the deduplication loop below, so we have to read (and
      // publish) drafts here.
      if (!inputComments.isEmpty() || in.drafts != DraftHandling.KEEP) {
        if (in.drafts == DraftHandling.PUBLISH_ALL_REVISIONS) {
          drafts = changeDrafts(ctx);
        } else {
          drafts = patchSetDrafts(ctx);
        }
      }

      // This will be populated with Comment-s created from inputComments.
      List<HumanComment> toPublish = new ArrayList<>();

      Set<CommentSetEntry> existingComments =
          in.omitDuplicateComments ? readExistingComments(ctx) : Collections.emptySet();

      // Deduplication:
      // - Ignore drafts with the same ID as an inputComment here. These are deleted later.
      // - Swallow comments that already exist.
      for (Map.Entry<String, List<CommentInput>> entry : inputComments.entrySet()) {
        String path = entry.getKey();
        for (CommentInput inputComment : entry.getValue()) {
          HumanComment comment = drafts.remove(Url.decode(inputComment.id));
          if (comment == null) {
            String parent = Url.decode(inputComment.inReplyTo);
            comment =
                commentsUtil.newHumanComment(
                    ctx,
                    path,
                    psId,
                    inputComment.side(),
                    inputComment.message,
                    inputComment.unresolved,
                    parent);
          } else {
            // In ChangeUpdate#putComment() the draft with the same ID will be deleted.
            comment.writtenOn = ctx.getWhen();
            comment.side = inputComment.side();
            comment.message = inputComment.message;
          }

          setCommentCommitId(comment, patchListCache, ctx.getChange(), ps);
          comment.setLineNbrAndRange(inputComment.line, inputComment.range);
          comment.tag = in.tag;

          if (existingComments.contains(CommentSetEntry.create(comment))) {
            continue;
          }
          toPublish.add(comment);
        }
      }

      CommentValidationContext commentValidationCtx =
          CommentValidationContext.create(
              ctx.getChange().getChangeId(), ctx.getChange().getProject().get());
      switch (in.drafts) {
        case PUBLISH:
        case PUBLISH_ALL_REVISIONS:
          validateComments(
              commentValidationCtx,
              Streams.concat(
                  drafts.values().stream(), toPublish.stream(), newRobotComments.stream()));
          publishCommentUtil.publish(ctx, ctx.getUpdate(psId), drafts.values(), in.tag);
          comments.addAll(drafts.values());
          break;
        case KEEP:
          validateComments(
              commentValidationCtx, Stream.concat(toPublish.stream(), newRobotComments.stream()));
          break;
      }
      ChangeUpdate changeUpdate = ctx.getUpdate(psId);
      commentsUtil.putHumanComments(changeUpdate, HumanComment.Status.PUBLISHED, toPublish);
      comments.addAll(toPublish);
      return !toPublish.isEmpty();
    }

    /**
     * Validates all comments and the change message in a single call to fulfill the interface
     * contract of {@link CommentValidator#validateComments(CommentValidationContext,
     * ImmutableList)}.
     */
    private void validateComments(CommentValidationContext ctx, Stream<Comment> comments)
        throws CommentsRejectedException {
      String changeMessage = Strings.nullToEmpty(in.message).trim();
      ImmutableList<CommentForValidation> draftsForValidation =
          Stream.concat(
                  comments.map(
                      comment ->
                          CommentForValidation.create(
                              comment instanceof RobotComment
                                  ? CommentForValidation.CommentSource.ROBOT
                                  : CommentForValidation.CommentSource.HUMAN,
                              comment.lineNbr > 0
                                  ? CommentForValidation.CommentType.INLINE_COMMENT
                                  : CommentForValidation.CommentType.FILE_COMMENT,
                              comment.message,
                              comment.getApproximateSize())),
                  Stream.of(
                      CommentForValidation.create(
                          CommentForValidation.CommentSource.HUMAN,
                          CommentForValidation.CommentType.CHANGE_MESSAGE,
                          changeMessage,
                          changeMessage.length())))
              .collect(toImmutableList());
      ImmutableList<CommentValidationFailure> draftValidationFailures =
          PublishCommentUtil.findInvalidComments(ctx, commentValidators, draftsForValidation);
      if (!draftValidationFailures.isEmpty()) {
        throw new CommentsRejectedException(draftValidationFailures);
      }
    }

    private boolean insertRobotComments(ChangeContext ctx, List<RobotComment> newRobotComments) {
      if (in.robotComments == null) {
        return false;
      }
      commentsUtil.putRobotComments(ctx.getUpdate(psId), newRobotComments);
      comments.addAll(newRobotComments);
      return !newRobotComments.isEmpty();
    }

    private List<RobotComment> getNewRobotComments(ChangeContext ctx)
        throws PatchListNotAvailableException {
      List<RobotComment> toAdd = new ArrayList<>(in.robotComments.size());

      Set<CommentSetEntry> existingIds =
          in.omitDuplicateComments ? readExistingRobotComments(ctx) : Collections.emptySet();

      for (Map.Entry<String, List<RobotCommentInput>> ent : in.robotComments.entrySet()) {
        String path = ent.getKey();
        for (RobotCommentInput c : ent.getValue()) {
          RobotComment e = createRobotCommentFromInput(ctx, path, c);
          if (existingIds.contains(CommentSetEntry.create(e))) {
            continue;
          }
          toAdd.add(e);
        }
      }
      return toAdd;
    }

    private RobotComment createRobotCommentFromInput(
        ChangeContext ctx, String path, RobotCommentInput robotCommentInput)
        throws PatchListNotAvailableException {
      RobotComment robotComment =
          commentsUtil.newRobotComment(
              ctx,
              path,
              psId,
              robotCommentInput.side(),
              robotCommentInput.message,
              robotCommentInput.robotId,
              robotCommentInput.robotRunId);
      robotComment.parentUuid = Url.decode(robotCommentInput.inReplyTo);
      robotComment.url = robotCommentInput.url;
      robotComment.properties = robotCommentInput.properties;
      robotComment.setLineNbrAndRange(robotCommentInput.line, robotCommentInput.range);
      robotComment.tag = in.tag;
      setCommentCommitId(robotComment, patchListCache, ctx.getChange(), ps);
      robotComment.fixSuggestions = createFixSuggestionsFromInput(robotCommentInput.fixSuggestions);
      return robotComment;
    }

    private List<FixSuggestion> createFixSuggestionsFromInput(
        List<FixSuggestionInfo> fixSuggestionInfos) {
      if (fixSuggestionInfos == null) {
        return Collections.emptyList();
      }

      List<FixSuggestion> fixSuggestions = new ArrayList<>(fixSuggestionInfos.size());
      for (FixSuggestionInfo fixSuggestionInfo : fixSuggestionInfos) {
        fixSuggestions.add(createFixSuggestionFromInput(fixSuggestionInfo));
      }
      return fixSuggestions;
    }

    private FixSuggestion createFixSuggestionFromInput(FixSuggestionInfo fixSuggestionInfo) {
      List<FixReplacement> fixReplacements = toFixReplacements(fixSuggestionInfo.replacements);
      String fixId = ChangeUtil.messageUuid();
      return new FixSuggestion(fixId, fixSuggestionInfo.description, fixReplacements);
    }

    private List<FixReplacement> toFixReplacements(List<FixReplacementInfo> fixReplacementInfos) {
      return fixReplacementInfos.stream().map(this::toFixReplacement).collect(toList());
    }

    private FixReplacement toFixReplacement(FixReplacementInfo fixReplacementInfo) {
      Comment.Range range = new Comment.Range(fixReplacementInfo.range);
      return new FixReplacement(fixReplacementInfo.path, range, fixReplacementInfo.replacement);
    }

    private Set<CommentSetEntry> readExistingComments(ChangeContext ctx) {
      return commentsUtil.publishedHumanCommentsByChange(ctx.getNotes()).stream()
          .map(CommentSetEntry::create)
          .collect(toSet());
    }

    private Set<CommentSetEntry> readExistingRobotComments(ChangeContext ctx) {
      return commentsUtil.robotCommentsByChange(ctx.getNotes()).stream()
          .map(CommentSetEntry::create)
          .collect(toSet());
    }

    private Map<String, HumanComment> changeDrafts(ChangeContext ctx) {
      return commentsUtil.draftByChangeAuthor(ctx.getNotes(), user.getAccountId()).stream()
          .collect(
              Collectors.toMap(
                  c -> c.key.uuid,
                  c -> {
                    c.tag = in.tag;
                    return c;
                  }));
    }

    private Map<String, HumanComment> patchSetDrafts(ChangeContext ctx) {
      return commentsUtil.draftByPatchSetAuthor(psId, user.getAccountId(), ctx.getNotes()).stream()
          .collect(Collectors.toMap(c -> c.key.uuid, c -> c));
    }

    private Map<String, Short> approvalsByKey(Collection<PatchSetApproval> patchsetApprovals) {
      Map<String, Short> labels = new HashMap<>();
      for (PatchSetApproval psa : patchsetApprovals) {
        labels.put(psa.label(), psa.value());
      }
      return labels;
    }

    private Map<String, Short> getAllApprovals(
        LabelTypes labelTypes, Map<String, Short> current, Map<String, Short> input) {
      Map<String, Short> allApprovals = new HashMap<>();
      for (LabelType lt : labelTypes.getLabelTypes()) {
        allApprovals.put(lt.getName(), (short) 0);
      }
      // set approvals to existing votes
      if (current != null) {
        allApprovals.putAll(current);
      }
      // set approvals to new votes
      if (input != null) {
        allApprovals.putAll(input);
      }
      return allApprovals;
    }

    private Map<String, Short> getPreviousApprovals(
        Map<String, Short> allApprovals, Map<String, Short> current) {
      Map<String, Short> previous = new HashMap<>();
      for (Map.Entry<String, Short> approval : allApprovals.entrySet()) {
        // assume vote is 0 if there is no vote
        if (!current.containsKey(approval.getKey())) {
          previous.put(approval.getKey(), (short) 0);
        } else {
          previous.put(approval.getKey(), current.get(approval.getKey()));
        }
      }
      return previous;
    }

    private boolean isReviewer(ChangeContext ctx) {
      ChangeData cd = changeDataFactory.create(ctx.getNotes());
      ReviewerSet reviewers = cd.reviewers();
      return reviewers.byState(REVIEWER).contains(ctx.getAccountId());
    }

    private boolean updateLabels(ProjectState projectState, ChangeContext ctx)
        throws ResourceConflictException, IOException {
      Map<String, Short> inLabels = firstNonNull(in.labels, Collections.emptyMap());

      // If no labels were modified and change is closed, abort early.
      // This avoids trying to record a modified label caused by a user
      // losing access to a label after the change was submitted.
      if (inLabels.isEmpty() && ctx.getChange().isClosed()) {
        return false;
      }

      List<PatchSetApproval> del = new ArrayList<>();
      List<PatchSetApproval> ups = new ArrayList<>();
      Map<String, PatchSetApproval> current = scanLabels(projectState, ctx, del);
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes());
      Map<String, Short> allApprovals =
          getAllApprovals(labelTypes, approvalsByKey(current.values()), inLabels);
      Map<String, Short> previous =
          getPreviousApprovals(allApprovals, approvalsByKey(current.values()));

      ChangeUpdate update = ctx.getUpdate(psId);
      for (Map.Entry<String, Short> ent : allApprovals.entrySet()) {
        String name = ent.getKey();
        LabelType lt = requireNonNull(labelTypes.byLabel(name), name);

        PatchSetApproval c = current.remove(lt.getName());
        String normName = lt.getName();
        approvals.put(normName, (short) 0);
        if (ent.getValue() == null || ent.getValue() == 0) {
          // User requested delete of this label.
          oldApprovals.put(normName, null);
          if (c != null) {
            if (c.value() != 0) {
              addLabelDelta(normName, (short) 0);
              oldApprovals.put(normName, previous.get(normName));
            }
            del.add(c);
            update.putApproval(normName, (short) 0);
          }
        } else if (c != null && c.value() != ent.getValue()) {
          PatchSetApproval.Builder b =
              c.toBuilder()
                  .value(ent.getValue())
                  .granted(ctx.getWhen())
                  .tag(Optional.ofNullable(in.tag));
          ctx.getUser().updateRealAccountId(b::realAccountId);
          c = b.build();
          ups.add(c);
          addLabelDelta(normName, c.value());
          oldApprovals.put(normName, previous.get(normName));
          approvals.put(normName, c.value());
          update.putApproval(normName, ent.getValue());
        } else if (c != null && c.value() == ent.getValue()) {
          current.put(normName, c);
          oldApprovals.put(normName, null);
          approvals.put(normName, c.value());
        } else if (c == null) {
          c =
              ApprovalsUtil.newApproval(psId, user, lt.getLabelId(), ent.getValue(), ctx.getWhen())
                  .tag(Optional.ofNullable(in.tag))
                  .granted(ctx.getWhen())
                  .build();
          ups.add(c);
          addLabelDelta(normName, c.value());
          oldApprovals.put(normName, previous.get(normName));
          approvals.put(normName, c.value());
          update.putReviewer(user.getAccountId(), REVIEWER);
          update.putApproval(normName, ent.getValue());
        }
      }

      validatePostSubmitLabels(ctx, labelTypes, previous, ups, del);

      // Return early if user is not a reviewer and not posting any labels.
      // This allows us to preserve their CC status.
      if (current.isEmpty() && del.isEmpty() && ups.isEmpty() && !isReviewer(ctx)) {
        return false;
      }

      return !del.isEmpty() || !ups.isEmpty();
    }

    private void validatePostSubmitLabels(
        ChangeContext ctx,
        LabelTypes labelTypes,
        Map<String, Short> previous,
        List<PatchSetApproval> ups,
        List<PatchSetApproval> del)
        throws ResourceConflictException {
      if (ctx.getChange().isNew()) {
        return; // Not closed, nothing to validate.
      } else if (del.isEmpty() && ups.isEmpty()) {
        return; // No new votes.
      } else if (!ctx.getChange().isMerged()) {
        throw new ResourceConflictException("change is closed");
      }

      // Disallow reducing votes on any labels post-submit. This assumes the
      // high values were broadly necessary to submit, so reducing them would
      // make it possible to take a merged change and make it no longer
      // submittable.
      List<PatchSetApproval> reduced = new ArrayList<>(ups.size() + del.size());
      List<String> disallowed = new ArrayList<>(labelTypes.getLabelTypes().size());

      for (PatchSetApproval psa : del) {
        LabelType lt = requireNonNull(labelTypes.byLabel(psa.label()));
        String normName = lt.getName();
        if (!lt.isAllowPostSubmit()) {
          disallowed.add(normName);
        }
        Short prev = previous.get(normName);
        if (prev != null && prev != 0) {
          reduced.add(psa);
        }
      }

      for (PatchSetApproval psa : ups) {
        LabelType lt = requireNonNull(labelTypes.byLabel(psa.label()));
        String normName = lt.getName();
        if (!lt.isAllowPostSubmit()) {
          disallowed.add(normName);
        }
        Short prev = previous.get(normName);
        if (prev == null) {
          continue;
        }
        checkState(prev != psa.value()); // Should be filtered out above.
        if (prev > psa.value()) {
          reduced.add(psa);
        }
        // No need to set postSubmit bit, which is set automatically when parsing from NoteDb.
      }

      if (!disallowed.isEmpty()) {
        throw new ResourceConflictException(
            "Voting on labels disallowed after submit: "
                + disallowed.stream().distinct().sorted().collect(joining(", ")));
      }
      if (!reduced.isEmpty()) {
        throw new ResourceConflictException(
            "Cannot reduce vote on labels for closed change: "
                + reduced.stream()
                    .map(PatchSetApproval::label)
                    .distinct()
                    .sorted()
                    .collect(joining(", ")));
      }
    }

    private Map<String, PatchSetApproval> scanLabels(
        ProjectState projectState, ChangeContext ctx, List<PatchSetApproval> del)
        throws IOException {
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes());
      Map<String, PatchSetApproval> current = new HashMap<>();

      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getNotes(),
              psId,
              user.getAccountId(),
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        if (a.isLegacySubmit()) {
          continue;
        }

        LabelType lt = labelTypes.byLabel(a.labelId());
        if (lt != null) {
          current.put(lt.getName(), a);
        } else {
          del.add(a);
        }
      }
      return current;
    }

    private boolean insertMessage(ChangeContext ctx) {
      String msg = Strings.nullToEmpty(in.message).trim();

      StringBuilder buf = new StringBuilder();
      for (LabelVote d : labelDelta) {
        buf.append(" ").append(d.format());
      }
      if (comments.size() == 1) {
        buf.append("\n\n(1 comment)");
      } else if (comments.size() > 1) {
        buf.append(String.format("\n\n(%d comments)", comments.size()));
      }
      if (!msg.isEmpty()) {
        // Message was already validated when validating comments, since validators need to see
        // everything in a single call.
        buf.append("\n\n").append(msg);
      } else if (in.ready) {
        buf.append("\n\n" + START_REVIEW_MESSAGE);
      }
      if (buf.length() == 0) {
        return false;
      }

      message =
          ChangeMessagesUtil.newMessage(
              psId, user, ctx.getWhen(), "Patch Set " + psId.get() + ":" + buf, in.tag);
      cmUtil.addChangeMessage(ctx.getUpdate(psId), message);
      return true;
    }

    private void addLabelDelta(String name, short value) {
      labelDelta.add(LabelVote.create(name, value));
    }
  }
}
