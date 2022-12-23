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
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Streams;
import com.google.common.collect.Table.Cell;
import com.google.gerrit.entities.Account;
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
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.EmailReviewComments;
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

  /**
   * Update of a copied label that has been performed on a follow-up patch set after a vote has been
   * applied on an outdated patch set (follow-up patch sets = all patch sets that are newer than the
   * outdated patch set on which the user voted).
   */
  @AutoValue
  abstract static class CopiedLabelUpdate {
    /**
     * Type of the update that has been performed for a copied vote on a follow-up patch set.
     *
     * <p>Whether the copied vote has been added
     *
     * <ul>
     *   <li>added to
     *   <li>updated on
     *   <li>removed from
     * </ul>
     *
     * a follow-up patch set.
     */
    enum Type {
      /** A copied vote was added. No copied vote existed for this label yet. */
      ADDED,

      /** An existing copied vote has been updated. */
      UPDATED,

      /** An existing copied vote has been removed. */
      REMOVED;
    }

    /** The ID of the (follow-up) patch set on which the copied label update has been performed. */
    abstract PatchSet.Id patchSetId();

    /**
     * The old copied label vote that has been updated or that has been removed.
     *
     * <p>Not set if {@link #type()} is {@link Type#ADDED}.
     */
    abstract Optional<LabelVote> oldLabelVote();

    /**
     * The type of the update that has been performed for the copied vote on the (follow-up) patch
     * set.
     */
    abstract Type type();

    /** Returns a string with the patch set number and if present the old label vote. */
    private String formatPatchSetWithOldLabelVote() {
      StringBuilder b = new StringBuilder();
      b.append(patchSetId().get());
      if (oldLabelVote().isPresent()) {
        b.append(" (was ").append(oldLabelVote().get().format()).append(")");
      }
      return b.toString();
    }

    private static CopiedLabelUpdate added(PatchSet.Id patchSetId) {
      return create(patchSetId, Optional.empty(), Type.ADDED);
    }

    private static CopiedLabelUpdate updated(PatchSet.Id patchSetId, LabelVote oldLabelVote) {
      return create(patchSetId, Optional.of(oldLabelVote), Type.UPDATED);
    }

    private static CopiedLabelUpdate removed(PatchSet.Id patchSetId, LabelVote oldLabelVote) {
      return create(patchSetId, Optional.of(oldLabelVote), Type.REMOVED);
    }

    private static CopiedLabelUpdate create(
        PatchSet.Id patchSetId, Optional<LabelVote> oldLabelVote, Type type) {
      return new AutoValue_PostReviewOp_CopiedLabelUpdate(patchSetId, oldLabelVote, type);
    }
  }

  @VisibleForTesting
  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  private final ApprovalCopier approvalCopier;
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
  private SortedSetMultimap<LabelVote, CopiedLabelUpdate> labelUpdatesOnFollowUpPatchSets =
      MultimapBuilder.hashKeys().treeSetValues(comparing(CopiedLabelUpdate::patchSetId)).build();
  private Map<String, Short> approvals = new HashMap<>();
  private Map<String, Short> oldApprovals = new HashMap<>();

  @Inject
  PostReviewOp(
      @GerritServerConfig Config gerritConfig,
      ApprovalCopier approvalCopier,
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
    this.approvalCopier = approvalCopier;
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
    try (TraceContext.TraceTimer ignored = newTimer("updateCopiedApprovals")) {
      dirty |= updateCopiedApprovalsOnFollowUpPatchSets(ctx);
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
    email
        .create(ctx, ps, notes.getMetaId(), mailMessage, comments, in.message, labelDelta)
        .sendAsync();
    String comment = mailMessage;
    if (publishPatchSetLevelComment) {
      // TODO(davido): Remove this workaround when patch set level comments are exposed in comment
      // added event. For backwards compatibility, patchset level comment has a higher priority
      // than change message and should be used as comment in comment added event.
      if (in.comments != null && in.comments.containsKey(PATCHSET_LEVEL)) {
        List<CommentInput> patchSetLevelComments = in.comments.get(PATCHSET_LEVEL);
        if (patchSetLevelComments != null && !patchSetLevelComments.isEmpty()) {
          CommentInput firstComment = patchSetLevelComments.get(0);
          if (!Strings.isNullOrEmpty(firstComment.message)) {
            comment = String.format("Patch Set %s:\n\n%s", psId.get(), firstComment.message);
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

  /**
   * Copies approvals that have been newly applied on outdated patch sets to the follow-up patch
   * sets if they are copyable and no non-copied approvals prevent the copying.
   *
   * <p>Must be invoked after the new approvals on outdated patch sets have been applied (e.g. after
   * {@link #updateLabels(ProjectState, ChangeContext)}.
   *
   * @param ctx the change context
   * @return {@code true} if an update was done, otherwise {@code false}
   */
  private boolean updateCopiedApprovalsOnFollowUpPatchSets(ChangeContext ctx) throws IOException {
    if (ctx.getNotes().getCurrentPatchSet().id().equals(psId)) {
      // the updated patch set is the current patch, there a no follow-up patch set to which new
      // approvals could be copied
      return false;
    }

    // compute follow-up patch sets (sorted by patch set ID)
    ImmutableList<PatchSet.Id> followUpPatchSets =
        ctx.getNotes().getPatchSets().keySet().stream()
            .filter(patchSetId -> patchSetId.get() > psId.get())
            .collect(toImmutableList());

    boolean dirty = false;
    ImmutableTable<String, Account.Id, Optional<PatchSetApproval>> newApprovals =
        ctx.getUpdate(psId).getApprovals();
    for (Cell<String, Account.Id, Optional<PatchSetApproval>> cell : newApprovals.cellSet()) {
      PatchSetApproval psaOrig = cell.getValue().get();

      if (isRemoval(cell)) {
        if (removeCopies(ctx, followUpPatchSets, psaOrig)) {
          dirty = true;
        }
        continue;
      }

      PatchSet patchSet = psUtil.get(ctx.getNotes(), psId);

      // Target patch sets to which the approval is copyable.
      ImmutableList<PatchSet.Id> targetPatchSets =
          approvalCopier.forApproval(
              ctx.getNotes(), patchSet, psaOrig.accountId(), psaOrig.label(), psaOrig.value());

      // Iterate over all follow-up patch sets, in patch set order.
      for (PatchSet.Id followUpPatchSetId : followUpPatchSets) {
        if (hasOverrideOf(ctx, followUpPatchSetId, psaOrig.key())) {
          // a non-copied approval exists that overrides any copied approval
          // -> do not copy the approval to this patch set nor to any follow-up patch sets
          break;
        }

        if (targetPatchSets.contains(followUpPatchSetId)) {
          // The approval is copyable to the new patch set.

          if (hasCopyOfWithValue(ctx, followUpPatchSetId, psaOrig)) {
            // a copy approval with the exact value already exists
            continue;
          }

          // add/update the copied approval on the target patch set
          Optional<PatchSetApproval> copiedPsa = getCopyOf(ctx, followUpPatchSetId, psaOrig.key());
          PatchSetApproval copiedPatchSetApproval = psaOrig.copyWithPatchSet(followUpPatchSetId);
          ctx.getUpdate(followUpPatchSetId).putCopiedApproval(copiedPatchSetApproval);
          labelUpdatesOnFollowUpPatchSets.put(
              LabelVote.createFrom(psaOrig),
              copiedPsa.isPresent()
                  ? CopiedLabelUpdate.updated(
                      followUpPatchSetId, LabelVote.createFrom(copiedPsa.get()))
                  : CopiedLabelUpdate.added(followUpPatchSetId));
          dirty = true;
        } else {
          // The approval is not copyable to the new patch set.
          Optional<PatchSetApproval> copiedPsa = getCopyOf(ctx, followUpPatchSetId, psaOrig.key());
          if (copiedPsa.isPresent()) {
            // a copy approval exists and should be removed
            removeCopy(ctx, psaOrig, copiedPsa.get());
            dirty = true;
          }
        }
      }
    }

    return dirty;
  }

  /**
   * Whether the given cell entry from the approval table represents the removal of an approval.
   *
   * @param cell cell entry from the approval table
   * @return {@code true} if the approval is not set or the approval has {@code 0} as the value,
   *     otherwise {@code false}
   */
  private boolean isRemoval(Cell<String, Account.Id, Optional<PatchSetApproval>> cell) {
    return cell.getValue().isEmpty() || cell.getValue().get().value() == 0;
  }

  /**
   * Removes copies of the given approval from all follow-up patch sets.
   *
   * @param ctx the change context
   * @param followUpPatchSets the follow-up patch sets of the patch set on which the review is
   *     posted
   * @param psaOrig the original patch set approval for which copies should be removed from all
   *     follow-up patch sets
   * @return whether any copy approval has been removed
   */
  private boolean removeCopies(
      ChangeContext ctx, ImmutableList<PatchSet.Id> followUpPatchSets, PatchSetApproval psaOrig) {
    boolean dirty = false;
    for (PatchSet.Id followUpPatchSet : followUpPatchSets) {
      Optional<PatchSetApproval> copiedPsa = getCopyOf(ctx, followUpPatchSet, psaOrig.key());
      if (copiedPsa.isPresent()) {
        removeCopy(ctx, psaOrig, copiedPsa.get());
      } else {
        // Do not remove copy from this follow-up patch sets and also not from any further follow-up
        // patch sets (if the further follow-up patch sets have copies they are copies of a
        // non-copied approval on this follow-up patch set and hence those should not be removed).
        break;
      }
    }
    return dirty;
  }

  /**
   * Removes the copy approval with the given key from the given patch set.
   *
   * @param ctx the change context
   * @param psaOrig the original patch set approval for which copies should be removed from the
   *     given patch set
   * @param copiedPsa the copied patch set approval that should be removed
   */
  private void removeCopy(ChangeContext ctx, PatchSetApproval psaOrig, PatchSetApproval copiedPsa) {
    ctx.getUpdate(copiedPsa.patchSetId())
        .removeCopiedApprovalFor(
            ctx.getIdentifiedUser().getRealUser().isIdentifiedUser()
                ? ctx.getIdentifiedUser().getRealUser().getAccountId()
                : null,
            copiedPsa.accountId(),
            copiedPsa.labelId().get());
    labelUpdatesOnFollowUpPatchSets.put(
        LabelVote.createFrom(psaOrig),
        CopiedLabelUpdate.removed(copiedPsa.patchSetId(), LabelVote.createFrom(copiedPsa)));
  }

  /**
   * Retrieves the copy of the given approval from the given patch set if it exists.
   *
   * @param ctx the change context
   * @param patchSetId the ID of the patch from which it the copied approval should be returned
   * @param psaKey the key of the patch set approval for which the copied approval should be
   *     returned
   * @return the copy of the given approval from the given patch set if it exists
   */
  private Optional<PatchSetApproval> getCopyOf(
      ChangeContext ctx, PatchSet.Id patchSetId, PatchSetApproval.Key psaKey) {
    return ctx.getNotes().getApprovals().onlyCopied().get(patchSetId).stream()
        .filter(psa -> areAccountAndLabelTheSame(psa.key(), psaKey))
        .findAny();
  }

  /**
   * Whether the given patch set has a copy approval with the given key and value.
   *
   * @param ctx the change context
   * @param patchSetId the ID of the patch for which it should be checked whether it has a copy
   *     approval with the given key and value
   * @param psaOrig the original patch set approval
   */
  private boolean hasCopyOfWithValue(
      ChangeContext ctx, PatchSet.Id patchSetId, PatchSetApproval psaOrig) {
    return ctx.getNotes().getApprovals().onlyCopied().get(patchSetId).stream()
        .anyMatch(
            psa ->
                areAccountAndLabelTheSame(psa.key(), psaOrig.key())
                    && psa.value() == psaOrig.value());
  }

  /**
   * Whether the given patch set has a normal approval with the given key that overrides copy
   * approvals with that key.
   *
   * @param ctx the change context
   * @param patchSetId the ID of the patch for which it should be checked whether it has a normal
   *     approval with the given key that overrides copy approvals with that key
   * @param psaKey the key of the patch set approval
   */
  private boolean hasOverrideOf(
      ChangeContext ctx, PatchSet.Id patchSetId, PatchSetApproval.Key psaKey) {
    return ctx.getNotes().getApprovals().onlyNonCopied().get(patchSetId).stream()
        .anyMatch(psa -> areAccountAndLabelTheSame(psa.key(), psaKey));
  }

  private boolean areAccountAndLabelTheSame(
      PatchSetApproval.Key psaKey1, PatchSetApproval.Key psaKey2) {
    return psaKey1.accountId().equals(psaKey2.accountId())
        && psaKey1.labelId().equals(psaKey2.labelId());
  }

  private boolean insertMessage(ChangeContext ctx) {
    String msg = Strings.nullToEmpty(in.message).trim();

    StringBuilder buf = new StringBuilder();
    for (String formattedLabelVote :
        labelDelta.stream().map(LabelVote::format).sorted().collect(toImmutableList())) {
      buf.append(" ").append(formattedLabelVote);
    }
    if (!labelUpdatesOnFollowUpPatchSets.isEmpty()) {
      buf.append("\n\nCopied votes on follow-up patch sets have been updated:");
      for (Map.Entry<LabelVote, Collection<CopiedLabelUpdate>> e :
          labelUpdatesOnFollowUpPatchSets.asMap().entrySet().stream()
              .sorted(Map.Entry.comparingByKey(comparing(LabelVote::label)))
              .collect(toImmutableList())) {
        Optional<String> copyCondition =
            projectState
                .getLabelTypes(ctx.getNotes())
                .byLabel(e.getKey().label())
                .map(LabelType::getCopyCondition)
                .map(Optional::get);
        buf.append(formatVotesCopiedToFollowUpPatchSets(e.getKey(), e.getValue(), copyCondition));
      }
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

  /**
   * Given a label vote that has been applied on an outdated patch set, this method formats the
   * updates to the copied labels on the follow-up patch sets that have been performed for that
   * label vote.
   *
   * <p>If label votes have been copied to follow-up patch sets the formatted message is
   * "<label-vote> has been copied to patch sets: 3, 4 (copy condition: "<copy-condition>").".
   *
   * <p>If existing copied votes on follow-up patch sets have been updated, the old copied votes are
   * included into the message: "<label-vote> has been copied to patch sets: 3 (was
   * <old-label-vote>), 4 (was <old-label-vote>) (copy condition: "<copy-condition>").".
   *
   * <p>If existing copied votes on follow-up patch sets have been removed (because the new vote is
   * not copyable) the message is: "Copied <label> vote has been removed from patch set 3 (was
   * <old-label-vote>), 4 (was <old-label-vote>) (copy condition: "<copy-condition>").".
   *
   * <p>If copied votes have been both added/updated and removed, 2 messages are returned.
   *
   * <p>Each returned message is formatted as a list item (prefixed with '* ').
   *
   * <p>Passing atoms in copy conditions are not highlighted. This is because the passing atoms can
   * be different for different follow-up patch sets (e.g. 'changekind:TRIVIAL_REBASE OR
   * changekind:NO_CODE_CHANGE' can have 'changekind:TRIVIAL_REBASE' passing for one follow-up patch
   * set and 'changekind:NO_CODE_CHANGE' passing for another follow-up patch set). Including the
   * copy condition once per follow-up patch set with differently highlighted passing atoms would
   * make the message unreadable. Hence we don't highlight passing atoms here.
   *
   * @param labelVote the label vote that has been applied on an outdated patch set
   * @param followUpPatchSetUpdates updates to copied votes on follow-up patch sets that have been
   *     done by copying the label vote on the outdated patch set to follow-up patch sets
   * @param copyCondition the copy condition of the label for which a vote was applied on an
   *     outdated patch set
   * @return formatted string to be included into a change message
   */
  private String formatVotesCopiedToFollowUpPatchSets(
      LabelVote labelVote,
      Collection<CopiedLabelUpdate> followUpPatchSetUpdates,
      Optional<String> copyCondition) {
    StringBuilder b = new StringBuilder();

    // Add line for added/updated copied approvals.
    ImmutableList<CopiedLabelUpdate> additionsAndUpdates =
        followUpPatchSetUpdates.stream()
            .filter(
                copiedLabelUpdate ->
                    copiedLabelUpdate.type() == CopiedLabelUpdate.Type.ADDED
                        || copiedLabelUpdate.type() == CopiedLabelUpdate.Type.UPDATED)
            .collect(toImmutableList());
    if (!additionsAndUpdates.isEmpty()) {
      b.append("\n* ");
      b.append(labelVote.format());
      b.append(" has been copied to patch set ");
      b.append(
          additionsAndUpdates.stream()
              .map(CopiedLabelUpdate::formatPatchSetWithOldLabelVote)
              .collect(joining(", ")));
      copyCondition.ifPresent(cc -> b.append(" (copy condition: \"" + cc + "\")"));
      b.append(".");
    }

    // Add line for removed copied approvals.
    ImmutableList<CopiedLabelUpdate> removals =
        followUpPatchSetUpdates.stream()
            .filter(copiedLabelUpdate -> copiedLabelUpdate.type() == CopiedLabelUpdate.Type.REMOVED)
            .collect(toImmutableList());
    if (!removals.isEmpty()) {
      b.append("\n* Copied ");
      b.append(labelVote.label());
      b.append(" vote has been removed from patch set ");
      b.append(
          removals.stream()
              .map(CopiedLabelUpdate::formatPatchSetWithOldLabelVote)
              .collect(joining(", ")));
      b.append(" since the new ");
      b.append(labelVote.value() != 0 ? labelVote.format() : labelVote.formatWithEquals());
      b.append(" vote is not copyable");
      copyCondition.ifPresent(cc -> b.append(" (copy condition: \"" + cc + "\")"));
      b.append(".");
    }
    return b.toString();
  }

  private void addLabelDelta(String name, short value) {
    labelDelta.add(LabelVote.create(name, value));
  }

  private TraceContext.TraceTimer newTimer(String method) {
    return TraceContext.newTimer(getClass().getSimpleName() + "#" + method, Metadata.empty());
  }
}
