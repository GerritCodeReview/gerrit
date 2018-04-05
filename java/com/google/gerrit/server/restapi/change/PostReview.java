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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.CommentsUtil.setCommentRevId;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.reviewdb.client.FixSuggestion;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.OptionalInt;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class PostReview
    extends RetryingRestModifyView<RevisionResource, ReviewInput, Response<ReviewResult>> {
  public static final String ERROR_ADDING_REVIEWER = "error adding reviewer";
  public static final String ERROR_ONLY_OWNER_CAN_MODIFY_WORK_IN_PROGRESS =
      "only change owner can specify work_in_progress or ready";
  public static final String ERROR_WIP_READY_MUTUALLY_EXCLUSIVE =
      "work_in_progress and ready are mutually exclusive";

  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();
  private static final int DEFAULT_ROBOT_COMMENT_SIZE_LIMIT_IN_BYTES = 1024 * 1024;

  private final Provider<ReviewDb> db;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final PatchListCache patchListCache;
  private final AccountsCollection accounts;
  private final EmailReviewComments.Factory email;
  private final CommentAdded commentAdded;
  private final PostReviewers postReviewers;
  private final NotesMigration migration;
  private final NotifyUtil notifyUtil;
  private final Config gerritConfig;
  private final WorkInProgressOp.Factory workInProgressOpFactory;
  private final ProjectCache projectCache;

  @Inject
  PostReview(
      Provider<ReviewDb> db,
      RetryHelper retryHelper,
      ChangeResource.Factory changeResourceFactory,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      AccountsCollection accounts,
      EmailReviewComments.Factory email,
      CommentAdded commentAdded,
      PostReviewers postReviewers,
      NotesMigration migration,
      NotifyUtil notifyUtil,
      @GerritServerConfig Config gerritConfig,
      WorkInProgressOp.Factory workInProgressOpFactory,
      ProjectCache projectCache) {
    super(retryHelper);
    this.db = db;
    this.changeResourceFactory = changeResourceFactory;
    this.changeDataFactory = changeDataFactory;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.accounts = accounts;
    this.email = email;
    this.commentAdded = commentAdded;
    this.postReviewers = postReviewers;
    this.migration = migration;
    this.notifyUtil = notifyUtil;
    this.gerritConfig = gerritConfig;
    this.workInProgressOpFactory = workInProgressOpFactory;
    this.projectCache = projectCache;
  }

  @Override
  protected Response<ReviewResult> applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource revision, ReviewInput input)
      throws RestApiException, UpdateException, OrmException, IOException,
          PermissionBackendException, ConfigInvalidException, PatchListNotAvailableException {
    return apply(updateFactory, revision, input, TimeUtil.nowTs());
  }

  public Response<ReviewResult> apply(
      BatchUpdate.Factory updateFactory, RevisionResource revision, ReviewInput input, Timestamp ts)
      throws RestApiException, UpdateException, OrmException, IOException,
          PermissionBackendException, ConfigInvalidException, PatchListNotAvailableException {
    // Respect timestamp, but truncate at change created-on time.
    ts = Ordering.natural().max(ts, revision.getChange().getCreatedOn());
    if (revision.getEdit().isPresent()) {
      throw new ResourceConflictException("cannot post review on edit");
    }
    ProjectState projectState = projectCache.checkedGet(revision.getProject());
    LabelTypes labelTypes = projectState.getLabelTypes(revision.getNotes(), revision.getUser());
    input.drafts = firstNonNull(input.drafts, DraftHandling.KEEP);
    if (input.onBehalfOf != null) {
      revision = onBehalfOf(revision, labelTypes, input);
    }
    if (input.labels != null) {
      checkLabels(revision, labelTypes, input.labels);
    }
    if (input.comments != null) {
      cleanUpComments(input.comments);
      checkComments(revision, input.comments);
    }
    if (input.robotComments != null) {
      if (!migration.readChanges()) {
        throw new MethodNotAllowedException("robot comments not supported");
      }
      checkRobotComments(revision, input.robotComments);
    }

    NotifyHandling reviewerNotify = input.notify;
    if (input.notify == null) {
      input.notify = defaultNotify(revision.getChange(), input);
    }

    ListMultimap<RecipientType, Account.Id> accountsToNotify =
        notifyUtil.resolveAccounts(input.notifyDetails);

    Map<String, AddReviewerResult> reviewerJsonResults = null;
    List<PostReviewers.Addition> reviewerResults = Lists.newArrayList();
    boolean hasError = false;
    boolean confirm = false;
    if (input.reviewers != null) {
      reviewerJsonResults = Maps.newHashMap();
      for (AddReviewerInput reviewerInput : input.reviewers) {
        // Prevent notifications because setting reviewers is batched.
        reviewerInput.notify = NotifyHandling.NONE;

        PostReviewers.Addition result =
            postReviewers.prepareApplication(revision.getChangeResource(), reviewerInput, true);
        reviewerJsonResults.put(reviewerInput.reviewer, result.result);
        if (result.result.error != null) {
          hasError = true;
          continue;
        }
        if (result.result.confirm != null) {
          confirm = true;
          continue;
        }
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
        updateFactory.create(db.get(), revision.getChange().getProject(), revision.getUser(), ts)) {
      Account.Id id = revision.getUser().getAccountId();
      boolean ccOrReviewer = false;
      if (input.labels != null && !input.labels.isEmpty()) {
        ccOrReviewer = input.labels.values().stream().filter(v -> v != 0).findFirst().isPresent();
      }

      if (!ccOrReviewer) {
        // Check if user was already CCed or reviewing prior to this review.
        ReviewerSet currentReviewers =
            approvalsUtil.getReviewers(db.get(), revision.getChangeResource().getNotes());
        ccOrReviewer = currentReviewers.all().contains(id);
      }

      // Apply reviewer changes first. Revision emails should be sent to the
      // updated set of reviewers. Also keep track of whether the user added
      // themselves as a reviewer or to the CC list.
      for (PostReviewers.Addition reviewerResult : reviewerResults) {
        bu.addOp(revision.getChange().getId(), reviewerResult.op);
        if (!ccOrReviewer && reviewerResult.result.reviewers != null) {
          for (ReviewerInfo reviewerInfo : reviewerResult.result.reviewers) {
            if (Objects.equals(id.get(), reviewerInfo._accountId)) {
              ccOrReviewer = true;
              break;
            }
          }
        }
        if (!ccOrReviewer && reviewerResult.result.ccs != null) {
          for (AccountInfo accountInfo : reviewerResult.result.ccs) {
            if (Objects.equals(id.get(), accountInfo._accountId)) {
              ccOrReviewer = true;
              break;
            }
          }
        }
      }

      if (!ccOrReviewer) {
        // User posting this review isn't currently in the reviewer or CC list,
        // isn't being explicitly added, and isn't voting on any label.
        // Automatically CC them on this change so they receive replies.
        PostReviewers.Addition selfAddition =
            postReviewers.ccCurrentUser(revision.getUser(), revision);
        bu.addOp(revision.getChange().getId(), selfAddition.op);
      }

      // Add WorkInProgressOp if requested.
      if (input.ready || input.workInProgress) {
        if (input.ready && input.workInProgress) {
          output.error = ERROR_WIP_READY_MUTUALLY_EXCLUSIVE;
          return Response.withStatusCode(SC_BAD_REQUEST, output);
        }
        if (!revision.getChange().getOwner().equals(revision.getUser().getAccountId())) {
          output.error = ERROR_ONLY_OWNER_CAN_MODIFY_WORK_IN_PROGRESS;
          return Response.withStatusCode(SC_BAD_REQUEST, output);
        }
        if (input.ready) {
          output.ready = true;
        }

        // Suppress notifications in WorkInProgressOp, we'll take care of
        // them in this endpoint.
        WorkInProgressOp.Input wipIn = new WorkInProgressOp.Input();
        wipIn.notify = NotifyHandling.NONE;
        bu.addOp(
            revision.getChange().getId(),
            workInProgressOpFactory.create(input.workInProgress, wipIn));
      }

      // Add the review op.
      bu.addOp(
          revision.getChange().getId(),
          new Op(projectState, revision.getPatchSet().getId(), input, accountsToNotify));

      bu.execute();

      for (PostReviewers.Addition reviewerResult : reviewerResults) {
        reviewerResult.gatherResults();
      }

      boolean readyForReview =
          (output.ready != null && output.ready) || !revision.getChange().isWorkInProgress();
      emailReviewers(revision.getChange(), reviewerResults, reviewerNotify, accountsToNotify,
          readyForReview);
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
      // If review hasn't started we want to minimize recipients, no matter who
      // the author is.
      return NotifyHandling.OWNER;
    }

    return NotifyHandling.ALL;
  }

  private void emailReviewers(
      Change change,
      List<PostReviewers.Addition> reviewerAdditions,
      @Nullable NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify, boolean readyForReview) {
    List<Account.Id> to = new ArrayList<>();
    List<Account.Id> cc = new ArrayList<>();
    List<Address> toByEmail = new ArrayList<>();
    List<Address> ccByEmail = new ArrayList<>();
    for (PostReviewers.Addition addition : reviewerAdditions) {
      if (addition.state == ReviewerState.REVIEWER) {
        to.addAll(addition.reviewers);
        toByEmail.addAll(addition.reviewersByEmail);
      } else if (addition.state == ReviewerState.CC) {
        cc.addAll(addition.reviewers);
        ccByEmail.addAll(addition.reviewersByEmail);
      }
    }
    if (reviewerAdditions.size() > 0) {
      reviewerAdditions
          .get(0)
          .op
          .emailReviewers(change, to, cc, toByEmail, ccByEmail, notify, accountsToNotify,
              readyForReview);
    }
  }

  private RevisionResource onBehalfOf(RevisionResource rev, LabelTypes labelTypes, ReviewInput in)
      throws BadRequestException, AuthException, UnprocessableEntityException, OrmException,
          PermissionBackendException, IOException, ConfigInvalidException {
    if (in.labels == null || in.labels.isEmpty()) {
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }
    if (in.drafts != DraftHandling.KEEP) {
      throw new AuthException("not allowed to modify other user's drafts");
    }

    CurrentUser caller = rev.getUser();
    PermissionBackend.ForChange perm = rev.permissions().database(db);
    Iterator<Map.Entry<String, Short>> itr = in.labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      LabelType type = labelTypes.byLabel(ent.getKey());
      if (type == null) {
        throw new BadRequestException(
            String.format("label \"%s\" is not a configured label", ent.getKey()));
      }

      if (!caller.isInternalUser()) {
        try {
          perm.check(new LabelPermission.WithValue(ON_BEHALF_OF, type, ent.getValue()));
        } catch (AuthException e) {
          throw new AuthException(
              String.format(
                  "not permitted to modify label \"%s\" on behalf of \"%s\"",
                  type.getName(), in.onBehalfOf));
        }
      }
    }
    if (in.labels.isEmpty()) {
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }

    IdentifiedUser reviewer = accounts.parseOnBehalfOf(caller, in.onBehalfOf);
    try {
      perm.user(reviewer).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException(
          String.format("on_behalf_of account %s cannot see change", reviewer.getAccountId()));
    }

    return new RevisionResource(
        changeResourceFactory.create(rev.getNotes(), reviewer), rev.getPatchSet());
  }

  private void checkLabels(RevisionResource rsrc, LabelTypes labelTypes, Map<String, Short> labels)
      throws BadRequestException, AuthException, PermissionBackendException {
    PermissionBackend.ForChange perm = rsrc.permissions();
    Iterator<Map.Entry<String, Short>> itr = labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      LabelType lt = labelTypes.byLabel(ent.getKey());
      if (lt == null) {
        throw new BadRequestException(
            String.format("label \"%s\" is not a configured label", ent.getKey()));
      }

      if (ent.getValue() == null || ent.getValue() == 0) {
        // Always permit 0, even if it is not within range.
        // Later null/0 will be deleted and revoke the label.
        continue;
      }

      if (lt.getValue(ent.getValue()) == null) {
        throw new BadRequestException(
            String.format("label \"%s\": %d is not a valid value", ent.getKey(), ent.getValue()));
      }

      short val = ent.getValue();
      try {
        perm.check(new LabelPermission.WithValue(lt, val));
      } catch (AuthException e) {
        throw new AuthException(
            String.format("Applying label \"%s\": %d is restricted", lt.getName(), val));
      }
    }
  }

  private static <T extends CommentInput> void cleanUpComments(
      Map<String, List<T>> commentsPerPath) {
    Iterator<List<T>> mapValueIterator = commentsPerPath.values().iterator();
    while (mapValueIterator.hasNext()) {
      List<T> comments = mapValueIterator.next();
      if (comments == null) {
        mapValueIterator.remove();
        continue;
      }

      cleanUpComments(comments);
      if (comments.isEmpty()) {
        mapValueIterator.remove();
      }
    }
  }

  private static <T extends CommentInput> void cleanUpComments(List<T> comments) {
    Iterator<T> commentsIterator = comments.iterator();
    while (commentsIterator.hasNext()) {
      T comment = commentsIterator.next();
      if (comment == null) {
        commentsIterator.remove();
        continue;
      }

      comment.message = Strings.nullToEmpty(comment.message).trim();
      if (comment.message.isEmpty()) {
        commentsIterator.remove();
      }
    }
  }

  private <T extends CommentInput> void checkComments(
      RevisionResource revision, Map<String, List<T>> commentsPerPath)
      throws BadRequestException, PatchListNotAvailableException {
    Set<String> revisionFilePaths = getAffectedFilePaths(revision);
    for (Map.Entry<String, List<T>> entry : commentsPerPath.entrySet()) {
      String path = entry.getKey();
      PatchSet.Id patchSetId = revision.getChange().currentPatchSetId();
      ensurePathRefersToAvailableOrMagicFile(path, revisionFilePaths, patchSetId);

      List<T> comments = entry.getValue();
      for (T comment : comments) {
        ensureLineIsNonNegative(comment.line, path);
        ensureCommentNotOnMagicFilesOfAutoMerge(path, comment);
        ensureRangeIsValid(path, comment.range);
      }
    }
  }

  private Set<String> getAffectedFilePaths(RevisionResource revision)
      throws PatchListNotAvailableException {
    ObjectId newId = ObjectId.fromString(revision.getPatchSet().getRevision().get());
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

  private void checkRobotComments(
      RevisionResource revision, Map<String, List<RobotCommentInput>> in)
      throws BadRequestException, PatchListNotAvailableException {
    cleanUpComments(in);
    for (Map.Entry<String, List<RobotCommentInput>> e : in.entrySet()) {
      String commentPath = e.getKey();
      for (RobotCommentInput c : e.getValue()) {
        ensureSizeOfJsonInputIsWithinBounds(c);
        ensureRobotIdIsSet(c.robotId, commentPath);
        ensureRobotRunIdIsSet(c.robotRunId, commentPath);
        ensureFixSuggestionsAreAddable(c.fixSuggestions, commentPath);
      }
    }
    checkComments(revision, in);
  }

  private void ensureSizeOfJsonInputIsWithinBounds(RobotCommentInput robotCommentInput)
      throws BadRequestException {
    OptionalInt robotCommentSizeLimit = getRobotCommentSizeLimit();
    if (robotCommentSizeLimit.isPresent()) {
      int sizeLimit = robotCommentSizeLimit.getAsInt();
      byte[] robotCommentBytes = GSON.toJson(robotCommentInput).getBytes(StandardCharsets.UTF_8);
      int robotCommentSize = robotCommentBytes.length;
      if (robotCommentSize > sizeLimit) {
        throw new BadRequestException(
            String.format(
                "Size %d (bytes) of robot comment is greater than limit %d (bytes)",
                robotCommentSize, sizeLimit));
      }
    }
  }

  private OptionalInt getRobotCommentSizeLimit() {
    int robotCommentSizeLimit =
        gerritConfig.getInt(
            "change", "robotCommentSizeLimit", DEFAULT_ROBOT_COMMENT_SIZE_LIMIT_IN_BYTES);
    if (robotCommentSizeLimit <= 0) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(robotCommentSizeLimit);
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
      ensureReplacementPathIsSet(commentPath, fixReplacementInfo.path);
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

  private static void ensureReplacementPathIsSet(String commentPath, String replacementPath)
      throws BadRequestException {
    if (replacementPath == null) {
      throw new BadRequestException(
          String.format(
              "A file path must be given for the replacement of the robot comment on %s",
              commentPath));
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
        fixReplacementInfos
            .stream()
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

  /** Used to compare Comments with CommentInput comments. */
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
    private final ListMultimap<RecipientType, Account.Id> accountsToNotify;

    private IdentifiedUser user;
    private ChangeNotes notes;
    private PatchSet ps;
    private ChangeMessage message;
    private List<Comment> comments = new ArrayList<>();
    private List<LabelVote> labelDelta = new ArrayList<>();
    private Map<String, Short> approvals = new HashMap<>();
    private Map<String, Short> oldApprovals = new HashMap<>();

    private Op(
        ProjectState projectState,
        PatchSet.Id psId,
        ReviewInput in,
        ListMultimap<RecipientType, Account.Id> accountsToNotify) {
      this.projectState = projectState;
      this.psId = psId;
      this.in = in;
      this.accountsToNotify = checkNotNull(accountsToNotify);
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, ResourceConflictException, UnprocessableEntityException, IOException,
            PatchListNotAvailableException {
      user = ctx.getIdentifiedUser();
      notes = ctx.getNotes();
      ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      boolean dirty = false;
      dirty |= insertComments(ctx);
      dirty |= insertRobotComments(ctx);
      dirty |= updateLabels(projectState, ctx);
      dirty |= insertMessage(ctx);
      return dirty;
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      if (message == null) {
        return;
      }
      if (in.notify.compareTo(NotifyHandling.NONE) > 0 || !accountsToNotify.isEmpty()) {
        email
            .create(
                in.notify,
                accountsToNotify,
                notes,
                ps,
                user,
                message,
                comments,
                in.message,
                labelDelta)
            .sendAsync();
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

    private boolean insertComments(ChangeContext ctx)
        throws OrmException, UnprocessableEntityException, PatchListNotAvailableException {
      Map<String, List<CommentInput>> map = in.comments;
      if (map == null) {
        map = Collections.emptyMap();
      }

      Map<String, Comment> drafts = Collections.emptyMap();
      if (!map.isEmpty() || in.drafts != DraftHandling.KEEP) {
        if (in.drafts == DraftHandling.PUBLISH_ALL_REVISIONS) {
          drafts = changeDrafts(ctx);
        } else {
          drafts = patchSetDrafts(ctx);
        }
      }

      List<Comment> toPublish = new ArrayList<>();

      Set<CommentSetEntry> existingIds =
          in.omitDuplicateComments ? readExistingComments(ctx) : Collections.emptySet();

      for (Map.Entry<String, List<CommentInput>> ent : map.entrySet()) {
        String path = ent.getKey();
        for (CommentInput c : ent.getValue()) {
          String parent = Url.decode(c.inReplyTo);
          Comment e = drafts.remove(Url.decode(c.id));
          if (e == null) {
            e = commentsUtil.newComment(ctx, path, psId, c.side(), c.message, c.unresolved, parent);
          } else {
            e.writtenOn = ctx.getWhen();
            e.side = c.side();
            e.message = c.message;
          }

          setCommentRevId(e, patchListCache, ctx.getChange(), ps);
          e.setLineNbrAndRange(c.line, c.range);
          e.tag = in.tag;

          if (existingIds.contains(CommentSetEntry.create(e))) {
            continue;
          }
          toPublish.add(e);
        }
      }

      switch (in.drafts) {
        case PUBLISH:
        case PUBLISH_ALL_REVISIONS:
          commentsUtil.publish(ctx, psId, drafts.values(), in.tag);
          comments.addAll(drafts.values());
          break;
        case KEEP:
        default:
          break;
      }
      ChangeUpdate u = ctx.getUpdate(psId);
      commentsUtil.putComments(ctx.getDb(), u, Status.PUBLISHED, toPublish);
      comments.addAll(toPublish);
      return !toPublish.isEmpty();
    }

    private boolean insertRobotComments(ChangeContext ctx)
        throws OrmException, PatchListNotAvailableException {
      if (in.robotComments == null) {
        return false;
      }

      List<RobotComment> newRobotComments = getNewRobotComments(ctx);
      commentsUtil.putRobotComments(ctx.getUpdate(psId), newRobotComments);
      comments.addAll(newRobotComments);
      return !newRobotComments.isEmpty();
    }

    private List<RobotComment> getNewRobotComments(ChangeContext ctx)
        throws OrmException, PatchListNotAvailableException {
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
      setCommentRevId(robotComment, patchListCache, ctx.getChange(), ps);
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

    private Set<CommentSetEntry> readExistingComments(ChangeContext ctx) throws OrmException {
      return commentsUtil
          .publishedByChange(ctx.getDb(), ctx.getNotes())
          .stream()
          .map(CommentSetEntry::create)
          .collect(toSet());
    }

    private Set<CommentSetEntry> readExistingRobotComments(ChangeContext ctx) throws OrmException {
      return commentsUtil
          .robotCommentsByChange(ctx.getNotes())
          .stream()
          .map(CommentSetEntry::create)
          .collect(toSet());
    }

    private Map<String, Comment> changeDrafts(ChangeContext ctx) throws OrmException {
      Map<String, Comment> drafts = new HashMap<>();
      for (Comment c :
          commentsUtil.draftByChangeAuthor(ctx.getDb(), ctx.getNotes(), user.getAccountId())) {
        c.tag = in.tag;
        drafts.put(c.key.uuid, c);
      }
      return drafts;
    }

    private Map<String, Comment> patchSetDrafts(ChangeContext ctx) throws OrmException {
      Map<String, Comment> drafts = new HashMap<>();
      for (Comment c :
          commentsUtil.draftByPatchSetAuthor(
              ctx.getDb(), psId, user.getAccountId(), ctx.getNotes())) {
        drafts.put(c.key.uuid, c);
      }
      return drafts;
    }

    private Map<String, Short> approvalsByKey(Collection<PatchSetApproval> patchsetApprovals) {
      Map<String, Short> labels = new HashMap<>();
      for (PatchSetApproval psa : patchsetApprovals) {
        labels.put(psa.getLabel(), psa.getValue());
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

    private boolean isReviewer(ChangeContext ctx) throws OrmException {
      if (ctx.getAccountId().equals(ctx.getChange().getOwner())) {
        return true;
      }
      ChangeData cd = changeDataFactory.create(db.get(), ctx.getNotes());
      ReviewerSet reviewers = cd.reviewers();
      if (reviewers.byState(REVIEWER).contains(ctx.getAccountId())) {
        return true;
      }
      return false;
    }

    private boolean updateLabels(ProjectState projectState, ChangeContext ctx)
        throws OrmException, ResourceConflictException, IOException {
      Map<String, Short> inLabels = firstNonNull(in.labels, Collections.emptyMap());

      // If no labels were modified and change is closed, abort early.
      // This avoids trying to record a modified label caused by a user
      // losing access to a label after the change was submitted.
      if (inLabels.isEmpty() && ctx.getChange().getStatus().isClosed()) {
        return false;
      }

      List<PatchSetApproval> del = new ArrayList<>();
      List<PatchSetApproval> ups = new ArrayList<>();
      Map<String, PatchSetApproval> current = scanLabels(projectState, ctx, del);
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes(), ctx.getUser());
      Map<String, Short> allApprovals =
          getAllApprovals(labelTypes, approvalsByKey(current.values()), inLabels);
      Map<String, Short> previous =
          getPreviousApprovals(allApprovals, approvalsByKey(current.values()));

      ChangeUpdate update = ctx.getUpdate(psId);
      for (Map.Entry<String, Short> ent : allApprovals.entrySet()) {
        String name = ent.getKey();
        LabelType lt = checkNotNull(labelTypes.byLabel(name), name);

        PatchSetApproval c = current.remove(lt.getName());
        String normName = lt.getName();
        approvals.put(normName, (short) 0);
        if (ent.getValue() == null || ent.getValue() == 0) {
          // User requested delete of this label.
          oldApprovals.put(normName, null);
          if (c != null) {
            if (c.getValue() != 0) {
              addLabelDelta(normName, (short) 0);
              oldApprovals.put(normName, previous.get(normName));
            }
            del.add(c);
            update.putApproval(normName, (short) 0);
          }
        } else if (c != null && c.getValue() != ent.getValue()) {
          c.setValue(ent.getValue());
          c.setGranted(ctx.getWhen());
          c.setTag(in.tag);
          ctx.getUser().updateRealAccountId(c::setRealAccountId);
          ups.add(c);
          addLabelDelta(normName, c.getValue());
          oldApprovals.put(normName, previous.get(normName));
          approvals.put(normName, c.getValue());
          update.putApproval(normName, ent.getValue());
        } else if (c != null && c.getValue() == ent.getValue()) {
          current.put(normName, c);
          oldApprovals.put(normName, null);
          approvals.put(normName, c.getValue());
        } else if (c == null) {
          c = ApprovalsUtil.newApproval(psId, user, lt.getLabelId(), ent.getValue(), ctx.getWhen());
          c.setTag(in.tag);
          c.setGranted(ctx.getWhen());
          ups.add(c);
          addLabelDelta(normName, c.getValue());
          oldApprovals.put(normName, previous.get(normName));
          approvals.put(normName, c.getValue());
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

      forceCallerAsReviewer(projectState, ctx, current, ups, del);
      ctx.getDb().patchSetApprovals().delete(del);
      ctx.getDb().patchSetApprovals().upsert(ups);
      return !del.isEmpty() || !ups.isEmpty();
    }

    private void validatePostSubmitLabels(
        ChangeContext ctx,
        LabelTypes labelTypes,
        Map<String, Short> previous,
        List<PatchSetApproval> ups,
        List<PatchSetApproval> del)
        throws ResourceConflictException {
      if (ctx.getChange().getStatus().isOpen()) {
        return; // Not closed, nothing to validate.
      } else if (del.isEmpty() && ups.isEmpty()) {
        return; // No new votes.
      } else if (ctx.getChange().getStatus() != Change.Status.MERGED) {
        throw new ResourceConflictException("change is closed");
      }

      // Disallow reducing votes on any labels post-submit. This assumes the
      // high values were broadly necessary to submit, so reducing them would
      // make it possible to take a merged change and make it no longer
      // submittable.
      List<PatchSetApproval> reduced = new ArrayList<>(ups.size() + del.size());
      List<String> disallowed = new ArrayList<>(labelTypes.getLabelTypes().size());

      for (PatchSetApproval psa : del) {
        LabelType lt = checkNotNull(labelTypes.byLabel(psa.getLabel()));
        String normName = lt.getName();
        if (!lt.allowPostSubmit()) {
          disallowed.add(normName);
        }
        Short prev = previous.get(normName);
        if (prev != null && prev != 0) {
          reduced.add(psa);
        }
      }

      for (PatchSetApproval psa : ups) {
        LabelType lt = checkNotNull(labelTypes.byLabel(psa.getLabel()));
        String normName = lt.getName();
        if (!lt.allowPostSubmit()) {
          disallowed.add(normName);
        }
        Short prev = previous.get(normName);
        if (prev == null) {
          continue;
        }
        checkState(prev != psa.getValue()); // Should be filtered out above.
        if (prev > psa.getValue()) {
          reduced.add(psa);
        } else {
          // Set postSubmit bit in ReviewDb; not required for NoteDb, which sets
          // it automatically.
          psa.setPostSubmit(true);
        }
      }

      if (!disallowed.isEmpty()) {
        throw new ResourceConflictException(
            "Voting on labels disallowed after submit: "
                + disallowed.stream().distinct().sorted().collect(joining(", ")));
      }
      if (!reduced.isEmpty()) {
        throw new ResourceConflictException(
            "Cannot reduce vote on labels for closed change: "
                + reduced
                    .stream()
                    .map(p -> p.getLabel())
                    .distinct()
                    .sorted()
                    .collect(joining(", ")));
      }
    }

    private void forceCallerAsReviewer(
        ProjectState projectState,
        ChangeContext ctx,
        Map<String, PatchSetApproval> current,
        List<PatchSetApproval> ups,
        List<PatchSetApproval> del) {
      if (current.isEmpty() && ups.isEmpty()) {
        // TODO Find another way to link reviewers to changes.
        if (del.isEmpty()) {
          // If no existing label is being set to 0, hack in the caller
          // as a reviewer by picking the first server-wide LabelType.
          LabelId labelId =
              projectState
                  .getLabelTypes(ctx.getNotes(), ctx.getUser())
                  .getLabelTypes()
                  .get(0)
                  .getLabelId();
          PatchSetApproval c = ApprovalsUtil.newApproval(psId, user, labelId, 0, ctx.getWhen());
          c.setTag(in.tag);
          c.setGranted(ctx.getWhen());
          ups.add(c);
        } else {
          // Pick a random label that is about to be deleted and keep it.
          Iterator<PatchSetApproval> i = del.iterator();
          PatchSetApproval c = i.next();
          c.setValue((short) 0);
          c.setGranted(ctx.getWhen());
          i.remove();
          ups.add(c);
        }
      }
      ctx.getUpdate(ctx.getChange().currentPatchSetId()).putReviewer(user.getAccountId(), REVIEWER);
    }

    private Map<String, PatchSetApproval> scanLabels(
        ProjectState projectState, ChangeContext ctx, List<PatchSetApproval> del)
        throws OrmException, IOException {
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes(), ctx.getUser());
      Map<String, PatchSetApproval> current = new HashMap<>();

      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getDb(),
              ctx.getNotes(),
              ctx.getUser(),
              psId,
              user.getAccountId(),
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        if (a.isLegacySubmit()) {
          continue;
        }

        LabelType lt = labelTypes.byLabel(a.getLabelId());
        if (lt != null) {
          current.put(lt.getName(), a);
        } else {
          del.add(a);
        }
      }
      return current;
    }

    private boolean insertMessage(ChangeContext ctx) throws OrmException {
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
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), message);
      return true;
    }

    private void addLabelDelta(String name, short value) {
      labelDelta.add(LabelVote.create(name, value));
    }
  }
}
