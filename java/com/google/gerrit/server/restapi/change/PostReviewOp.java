// Copyright (C) 2022 The Android Open Source Project
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.change.PostReview.CommentSetEntry;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

public class PostReviewOp implements BatchUpdateOp {
  interface Factory {
    PostReviewOp create(ProjectState projectState, PatchSet.Id psId, ReviewInput in);
  }

  @VisibleForTesting
  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CommentsUtil commentsUtil;
  private final PublishCommentUtil publishCommentUtil;
  private final PatchSetUtil psUtil;
  private final EmailReviewComments.Factory email;
  private final CommentAdded commentAdded;
  private final PluginSetContext<CommentValidator> commentValidators;
  private final PluginSetContext<OnPostReview> onPostReviews;

  private final ProjectState projectState;
  private final PatchSet.Id psId;
  private final ReviewInput in;
  private final boolean publishPatchSetLevelComment;

  private IdentifiedUser user;
  private ChangeNotes notes;
  private PatchSet ps;
  private String mailMessage;
  private List<Comment> comments = new ArrayList<>();
  private List<LabelVote> labelDelta = new ArrayList<>();
  private Map<String, Short> approvals = new HashMap<>();
  private Map<String, Short> oldApprovals = new HashMap<>();

  @Inject
  PostReviewOp(
      @GerritServerConfig Config gerritConfig,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PublishCommentUtil publishCommentUtil,
      PatchSetUtil psUtil,
      EmailReviewComments.Factory email,
      CommentAdded commentAdded,
      PluginSetContext<CommentValidator> commentValidators,
      PluginSetContext<OnPostReview> onPostReviews,
      @Assisted ProjectState projectState,
      @Assisted PatchSet.Id psId,
      @Assisted ReviewInput in) {
    this.approvalsUtil = approvalsUtil;
    this.publishCommentUtil = publishCommentUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.email = email;
    this.commentAdded = commentAdded;
    this.commentValidators = commentValidators;
    this.onPostReviews = onPostReviews;
    this.publishPatchSetLevelComment =
        gerritConfig.getBoolean("event", "comment-added", "publishPatchSetLevelComment", true);

    this.projectState = projectState;
    this.psId = psId;
    this.in = in;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, UnprocessableEntityException, IOException,
          CommentsRejectedException {
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
  public void postUpdate(PostUpdateContext ctx) {
    if (mailMessage == null) {
      return;
    }
    NotifyResolver.Result notify = ctx.getNotify(notes.getChangeId());
    if (notify.shouldNotify()) {
      email
          .create(ctx, ps, notes.getMetaId(), mailMessage, comments, in.message, labelDelta)
          .sendAsync();
    }
    String comment = mailMessage;
    if (publishPatchSetLevelComment) {
      // TODO(davido): Remove this workaround when patch set level comments are exposed in comment
      // added event. For backwards compatibility, patchset level comment has a higher priority
      // than change message and should be used as comment in comment added event.
      Optional<Comment> patchSetLevelComment =
          comments.stream().filter(c -> c.key.filename.equals(PATCHSET_LEVEL)).findFirst();
      if (patchSetLevelComment.isPresent()) {
        Comment patchSetLevelComments = patchSetLevelComment.get();
        if (patchSetLevelComments != null
            && patchSetLevelComments.message != null
            && !patchSetLevelComments.message.isEmpty()) {
          String firstComment = patchSetLevelComments.message;
          if (!Strings.isNullOrEmpty(firstComment)) {
            comment = String.format("Patch Set %s:\n\n%s", psId.get(), firstComment);
          }
        }
      }
    }
    commentAdded.fire(
        ctx.getChangeData(notes),
        ps,
        user.state(),
        comment,
        approvals,
        oldApprovals,
        ctx.getWhen());
  }

  /**
   * Publishes draft and input comments. Input comments are those passed as input in the request
   * body.
   *
   * @param ctx context for performing the change update.
   * @param newRobotComments robot comments. Used only for validation in this method.
   * @return true if any input comments where published.
   */
  private boolean insertComments(ChangeContext ctx, List<RobotComment> newRobotComments)
      throws CommentsRejectedException {
    Map<String, List<CommentInput>> inputComments = in.comments;
    if (inputComments == null) {
      inputComments = Collections.emptyMap();
    }

    // Use HashMap to avoid warnings when calling remove() in resolveInputCommentsAndDrafts().
    Map<String, HumanComment> drafts = new HashMap<>();

    if (!inputComments.isEmpty() || in.drafts != DraftHandling.KEEP) {
      drafts =
          in.drafts == DraftHandling.PUBLISH_ALL_REVISIONS
              ? changeDrafts(ctx)
              : patchSetDrafts(ctx);
    }

    // Existing published comments
    Set<CommentSetEntry> existingComments =
        in.omitDuplicateComments ? readExistingComments(ctx) : Collections.emptySet();

    // Input comments should be deduplicated from existing drafts
    List<HumanComment> inputCommentsToPublish =
        resolveInputCommentsAndDrafts(inputComments, existingComments, drafts, ctx);

    switch (in.drafts) {
      case PUBLISH:
      case PUBLISH_ALL_REVISIONS:
        Collection<HumanComment> filteredDrafts =
            in.draftIdsToPublish == null
                ? drafts.values()
                : drafts.values().stream()
                    .filter(draft -> in.draftIdsToPublish.contains(draft.key.uuid))
                    .collect(Collectors.toList());

        validateComments(
            ctx,
            Streams.concat(
                drafts.values().stream(),
                inputCommentsToPublish.stream(),
                newRobotComments.stream()));
        publishCommentUtil.publish(ctx, ctx.getUpdate(psId), filteredDrafts, in.tag);
        comments.addAll(drafts.values());
        break;
      case KEEP:
        validateComments(
            ctx, Streams.concat(inputCommentsToPublish.stream(), newRobotComments.stream()));
        break;
    }
    commentsUtil.putHumanComments(
        ctx.getUpdate(psId), HumanComment.Status.PUBLISHED, inputCommentsToPublish);
    comments.addAll(inputCommentsToPublish);
    return !inputCommentsToPublish.isEmpty();
  }

  /**
   * Returns the subset of {@code inputComments} that do not have a matching comment (with same id)
   * neither in {@code existingComments} nor in {@code drafts}.
   *
   * <p>Entries in {@code drafts} that have a matching entry in {@code inputComments} will be
   * removed.
   *
   * @param inputComments new comments provided as {@link CommentInput} entries in the API.
   * @param existingComments existing published comments in the database.
   * @param drafts existing draft comments in the database. This map can be modified.
   */
  private List<HumanComment> resolveInputCommentsAndDrafts(
      Map<String, List<CommentInput>> inputComments,
      Set<CommentSetEntry> existingComments,
      Map<String, HumanComment> drafts,
      ChangeContext ctx) {
    List<HumanComment> inputCommentsToPublish = new ArrayList<>();
    for (Map.Entry<String, List<CommentInput>> entry : inputComments.entrySet()) {
      String path = entry.getKey();
      for (CommentInput inputComment : entry.getValue()) {
        HumanComment comment = drafts.remove(Url.decode(inputComment.id));
        if (comment == null) {
          String parent = Url.decode(inputComment.inReplyTo);
          comment =
              commentsUtil.newHumanComment(
                  ctx.getNotes(),
                  ctx.getUser(),
                  ctx.getWhen(),
                  path,
                  psId,
                  inputComment.side(),
                  inputComment.message,
                  inputComment.unresolved,
                  parent);
        } else {
          // In ChangeUpdate#putComment() the draft with the same ID will be deleted.
          comment.writtenOn = Timestamp.from(ctx.getWhen());
          comment.side = inputComment.side();
          comment.message = inputComment.message;
        }

        commentsUtil.setCommentCommitId(comment, ctx.getChange(), ps);
        comment.setLineNbrAndRange(inputComment.line, inputComment.range);
        comment.tag = in.tag;

        if (existingComments.contains(CommentSetEntry.create(comment))) {
          continue;
        }
        inputCommentsToPublish.add(comment);
      }
    }
    return inputCommentsToPublish;
  }

  /**
   * Validates all comments and the change message in a single call to fulfill the interface
   * contract of {@link CommentValidator#validateComments(CommentValidationContext, ImmutableList)}.
   */
  private void validateComments(ChangeContext ctx, Stream<? extends Comment> comments)
      throws CommentsRejectedException {
    CommentValidationContext commentValidationCtx =
        CommentValidationContext.create(
            ctx.getChange().getChangeId(),
            ctx.getChange().getProject().get(),
            ctx.getChange().getDest().branch());
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
        PublishCommentUtil.findInvalidComments(
            commentValidationCtx, commentValidators, draftsForValidation);
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

  private List<RobotComment> getNewRobotComments(ChangeContext ctx) {
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
      ChangeContext ctx, String path, RobotCommentInput robotCommentInput) {
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
    commentsUtil.setCommentCommitId(robotComment, ctx.getChange(), ps);
    robotComment.fixSuggestions = createFixSuggestionsFromInput(robotCommentInput.fixSuggestions);
    return robotComment;
  }

  private ImmutableList<FixSuggestion> createFixSuggestionsFromInput(
      List<FixSuggestionInfo> fixSuggestionInfos) {
    if (fixSuggestionInfos == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<FixSuggestion> fixSuggestions =
        ImmutableList.builderWithExpectedSize(fixSuggestionInfos.size());
    for (FixSuggestionInfo fixSuggestionInfo : fixSuggestionInfos) {
      fixSuggestions.add(createFixSuggestionFromInput(fixSuggestionInfo));
    }
    return fixSuggestions.build();
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
        .collect(Collectors.toMap(c -> c.key.uuid, c -> c));
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
    return approvalsUtil
        .getReviewers(ctx.getNotes())
        .byState(REVIEWER)
        .contains(ctx.getAccountId());
  }

  private boolean updateLabels(ProjectState projectState, ChangeContext ctx)
      throws ResourceConflictException {
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
      LabelType lt =
          labelTypes
              .byLabel(name)
              .orElseThrow(() -> new IllegalStateException("no label config for " + name));

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
        // Only allow voting again if the vote is copied over from a past patch-set, or the
        // values are different.
      } else if (c != null
          && (c.value() != ent.getValue()
              || (inLabels.containsKey(c.label()) && isApprovalCopiedOver(c, ctx.getNotes())))) {
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

  /** Approval is copied over if it doesn't exist in the approvals of the current patch-set. */
  private boolean isApprovalCopiedOver(PatchSetApproval patchSetApproval, ChangeNotes changeNotes) {
    return !changeNotes.getApprovals().onlyNonCopied()
        .get(changeNotes.getChange().currentPatchSetId()).stream()
        .anyMatch(p -> p.equals(patchSetApproval));
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
      LabelType lt =
          labelTypes
              .byLabel(psa.label())
              .orElseThrow(() -> new IllegalStateException("no label config for " + psa.label()));
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
      LabelType lt =
          labelTypes
              .byLabel(psa.label())
              .orElseThrow(() -> new IllegalStateException("no label config for " + psa.label()));
      String normName = lt.getName();
      if (!lt.isAllowPostSubmit()) {
        disallowed.add(normName);
      }
      Short prev = previous.get(normName);
      if (prev == null) {
        continue;
      }
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
      ProjectState projectState, ChangeContext ctx, List<PatchSetApproval> del) {
    LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes());
    Map<String, PatchSetApproval> current = new HashMap<>();

    for (PatchSetApproval a :
        approvalsUtil.byPatchSetUser(ctx.getNotes(), psId, user.getAccountId())) {
      if (a.isLegacySubmit()) {
        continue;
      }

      Optional<LabelType> lt = labelTypes.byLabel(a.labelId());
      if (lt.isPresent()) {
        current.put(lt.get().getName(), a);
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

    List<String> pluginMessages = new ArrayList<>();
    onPostReviews.runEach(
        onPostReview ->
            onPostReview
                .getChangeMessageAddOn(user, ctx.getNotes(), ps, oldApprovals, approvals)
                .ifPresent(
                    pluginMessage ->
                        pluginMessages.add(
                            !pluginMessage.endsWith("\n") ? pluginMessage + "\n" : pluginMessage)));
    if (!pluginMessages.isEmpty()) {
      buf.append("\n\n");
      buf.append(Joiner.on("\n").join(pluginMessages));
    }

    if (buf.length() == 0) {
      return false;
    }

    mailMessage =
        cmUtil.setChangeMessage(ctx.getUpdate(psId), "Patch Set " + psId.get() + ":" + buf, in.tag);
    return true;
  }

  private void addLabelDelta(String name, short value) {
    labelDelta.add(LabelVote.create(name, value));
  }

  private TraceContext.TraceTimer newTimer(String method) {
    return TraceContext.newTimer(getClass().getSimpleName() + "#" + method, Metadata.empty());
  }
}
