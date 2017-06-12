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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.CommentsUtil.setCommentRevId;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PostReview implements RestModifyView<RevisionResource, ReviewInput> {
  private static final Logger log = LoggerFactory.getLogger(PostReview.class);

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangesCollection changes;
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

  @Inject
  PostReview(
      Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      ChangesCollection changes,
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
      NotesMigration migration) {
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changes = changes;
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
  }

  @Override
  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input)
      throws RestApiException, UpdateException, OrmException, IOException {
    return apply(revision, input, TimeUtil.nowTs());
  }

  public Response<ReviewResult> apply(RevisionResource revision, ReviewInput input, Timestamp ts)
      throws RestApiException, UpdateException, OrmException, IOException {
    // Respect timestamp, but truncate at change created-on time.
    ts = Ordering.natural().max(ts, revision.getChange().getCreatedOn());
    if (revision.getEdit().isPresent()) {
      throw new ResourceConflictException("cannot post review on edit");
    }
    if (input.onBehalfOf != null) {
      revision = onBehalfOf(revision, input);
    } else if (input.drafts == null) {
      input.drafts = DraftHandling.DELETE;
    }
    if (input.labels != null) {
      checkLabels(revision, input.strictLabels, input.labels);
    }
    if (input.comments != null) {
      checkComments(revision, input.comments);
    }
    if (input.robotComments != null) {
      if (!migration.readChanges()) {
        throw new MethodNotAllowedException("robot comments not supported");
      }
      checkRobotComments(revision, input.robotComments);
    }
    if (input.notify == null) {
      log.warn("notify = null; assuming notify = NONE");
      input.notify = NotifyHandling.NONE;
    }

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
            postReviewers.prepareApplication(revision.getChangeResource(), reviewerInput);
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
      return Response.withStatusCode(SC_BAD_REQUEST, output);
    }
    output.labels = input.labels;

    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db.get(), revision.getChange().getProject(), revision.getUser(), ts)) {
      Account.Id id = bu.getUser().getAccountId();
      boolean ccOrReviewer = input.labels != null;

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
            if (id.equals(reviewerInfo._accountId)) {
              ccOrReviewer = true;
              break;
            }
          }
        }
        if (!ccOrReviewer && reviewerResult.result.ccs != null) {
          for (AccountInfo accountInfo : reviewerResult.result.ccs) {
            if (id.equals(accountInfo._accountId)) {
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
        PostReviewers.Addition selfAddition = postReviewers.ccCurrentUser(bu.getUser(), revision);
        bu.addOp(revision.getChange().getId(), selfAddition.op);
      }

      bu.addOp(
          revision.getChange().getId(),
          new Op(revision.getPatchSet().getId(), input, reviewerResults));
      bu.execute();

      for (PostReviewers.Addition reviewerResult : reviewerResults) {
        reviewerResult.gatherResults();
      }

      emailReviewers(revision.getChange(), reviewerResults, input.notify);
    }

    return Response.ok(output);
  }

  private void emailReviewers(
      Change change, List<PostReviewers.Addition> reviewerAdditions, NotifyHandling notify) {
    List<Account.Id> to = new ArrayList<>();
    List<Account.Id> cc = new ArrayList<>();
    for (PostReviewers.Addition addition : reviewerAdditions) {
      if (addition.op.state == ReviewerState.REVIEWER) {
        to.addAll(addition.op.reviewers.keySet());
      } else if (addition.op.state == ReviewerState.CC) {
        cc.addAll(addition.op.reviewers.keySet());
      }
    }
    postReviewers.emailReviewers(change, to, cc, notify);
  }

  private RevisionResource onBehalfOf(RevisionResource rev, ReviewInput in)
      throws BadRequestException, AuthException, UnprocessableEntityException, OrmException {
    if (in.labels == null || in.labels.isEmpty()) {
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }
    if (in.drafts == null) {
      in.drafts = DraftHandling.KEEP;
    }
    if (in.drafts != DraftHandling.KEEP) {
      throw new AuthException("not allowed to modify other user's drafts");
    }

    ChangeControl caller = rev.getControl();
    Iterator<Map.Entry<String, Short>> itr = in.labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      LabelType type = caller.getLabelTypes().byLabel(ent.getKey());
      if (type == null && in.strictLabels) {
        throw new BadRequestException(
            String.format("label \"%s\" is not a configured label", ent.getKey()));
      } else if (type == null) {
        itr.remove();
        continue;
      }

      if (caller.getUser().isInternalUser()) {
        continue;
      }

      PermissionRange r = caller.getRange(Permission.forLabelAs(type.getName()));
      if (r == null || r.isEmpty() || !r.contains(ent.getValue())) {
        throw new AuthException(
            String.format(
                "not permitted to modify label \"%s\" on behalf of \"%s\"",
                ent.getKey(), in.onBehalfOf));
      }
    }
    if (in.labels.isEmpty()) {
      throw new AuthException(
          String.format("label required to post review on behalf of \"%s\"", in.onBehalfOf));
    }

    ChangeControl target =
        caller.forUser(accounts.parseOnBehalfOf(caller.getUser(), in.onBehalfOf));
    if (!target.getRefControl().isVisible()) {
      throw new UnprocessableEntityException(
          String.format(
              "on_behalf_of account %s cannot see destination ref",
              target.getUser().getAccountId()));
    }
    return new RevisionResource(changes.parse(target), rev.getPatchSet());
  }

  private void checkLabels(RevisionResource revision, boolean strict, Map<String, Short> labels)
      throws BadRequestException, AuthException {
    ChangeControl ctl = revision.getControl();
    Iterator<Map.Entry<String, Short>> itr = labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();

      LabelType lt = revision.getControl().getLabelTypes().byLabel(ent.getKey());
      if (lt == null) {
        if (strict) {
          throw new BadRequestException(
              String.format("label \"%s\" is not a configured label", ent.getKey()));
        }
        itr.remove();
        continue;
      }

      if (ent.getValue() == null || ent.getValue() == 0) {
        // Always permit 0, even if it is not within range.
        // Later null/0 will be deleted and revoke the label.
        continue;
      }

      if (lt.getValue(ent.getValue()) == null) {
        if (strict) {
          throw new BadRequestException(
              String.format("label \"%s\": %d is not a valid value", ent.getKey(), ent.getValue()));
        }
        itr.remove();
        continue;
      }

      String name = lt.getName();
      PermissionRange range = ctl.getRange(Permission.forLabel(name));
      if (range == null || !range.contains(ent.getValue())) {
        if (strict) {
          throw new AuthException(
              String.format(
                  "Applying label \"%s\": %d is restricted", ent.getKey(), ent.getValue()));
        } else if (range == null || range.isEmpty()) {
          ent.setValue((short) 0);
        } else {
          ent.setValue((short) range.squash(ent.getValue()));
        }
      }
    }
  }

  private <T extends CommentInput> void checkComments(
      RevisionResource revision, Map<String, List<T>> in) throws BadRequestException, OrmException {
    Iterator<? extends Map.Entry<String, List<T>>> mapItr = in.entrySet().iterator();
    Set<String> filePaths =
        Sets.newHashSet(
            changeDataFactory
                .create(db.get(), revision.getControl())
                .filePaths(revision.getPatchSet()));
    while (mapItr.hasNext()) {
      Map.Entry<String, List<T>> ent = mapItr.next();
      String path = ent.getKey();
      if (!filePaths.contains(path) && !Patch.isMagic(path)) {
        throw new BadRequestException(
            String.format(
                "file %s not found in revision %s",
                path, revision.getChange().currentPatchSetId()));
      }

      List<T> list = ent.getValue();
      if (list == null) {
        mapItr.remove();
        continue;
      }
      if (Patch.isMagic(path)) {
        for (T comment : list) {
          if (comment.side == Side.PARENT && comment.parent == null) {
            throw new BadRequestException(
                String.format("cannot comment on %s on auto-merge", path));
          }
        }
      }

      Iterator<T> listItr = list.iterator();
      while (listItr.hasNext()) {
        T c = listItr.next();
        if (c == null) {
          listItr.remove();
          continue;
        }
        if (c.line != null && c.line < 0) {
          throw new BadRequestException(
              String.format("negative line number %d not allowed on %s", c.line, path));
        }
        c.message = Strings.nullToEmpty(c.message).trim();
        if (c.message.isEmpty()) {
          listItr.remove();
        }
      }
      if (list.isEmpty()) {
        mapItr.remove();
      }
    }
  }

  private void checkRobotComments(
      RevisionResource revision, Map<String, List<RobotCommentInput>> in)
      throws BadRequestException, OrmException {
    for (Map.Entry<String, List<RobotCommentInput>> e : in.entrySet()) {
      String path = e.getKey();
      for (RobotCommentInput c : e.getValue()) {
        if (c.robotId == null) {
          throw new BadRequestException(
              String.format("robotId is missing for robot comment on %s", path));
        }
        if (c.robotRunId == null) {
          throw new BadRequestException(
              String.format("robotRunId is missing for robot comment on %s", path));
        }
      }
    }
    checkComments(revision, in);
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
          Hashing.sha1().hashString(comment.message, UTF_8),
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

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;
    private final ReviewInput in;
    private final List<PostReviewers.Addition> reviewerResults;

    private IdentifiedUser user;
    private ChangeNotes notes;
    private PatchSet ps;
    private ChangeMessage message;
    private List<Comment> comments = new ArrayList<>();
    private List<String> labelDelta = new ArrayList<>();
    private Map<String, Short> approvals = new HashMap<>();
    private Map<String, Short> oldApprovals = new HashMap<>();

    private Op(PatchSet.Id psId, ReviewInput in, List<PostReviewers.Addition> reviewerResults) {
      this.psId = psId;
      this.in = in;
      this.reviewerResults = reviewerResults;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException, ResourceConflictException {
      user = ctx.getIdentifiedUser();
      notes = ctx.getNotes();
      ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      boolean dirty = false;
      dirty |= insertComments(ctx);
      dirty |= insertRobotComments(ctx);
      dirty |= updateLabels(ctx);
      dirty |= insertMessage(ctx);
      return dirty;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (message == null) {
        return;
      }
      if (in.notify.compareTo(NotifyHandling.NONE) > 0) {
        email.create(in.notify, notes, ps, user, message, comments).sendAsync();
      }
      commentAdded.fire(
          notes.getChange(),
          ps,
          user.getAccount(),
          message.getMessage(),
          approvals,
          oldApprovals,
          ctx.getWhen());
    }

    private boolean insertComments(ChangeContext ctx) throws OrmException {
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

      List<Comment> toDel = new ArrayList<>();
      List<Comment> toPublish = new ArrayList<>();

      Set<CommentSetEntry> existingIds =
          in.omitDuplicateComments ? readExistingComments(ctx) : Collections.emptySet();

      for (Map.Entry<String, List<CommentInput>> ent : map.entrySet()) {
        String path = ent.getKey();
        for (CommentInput c : ent.getValue()) {
          String parent = Url.decode(c.inReplyTo);
          Comment e = drafts.remove(Url.decode(c.id));
          if (e == null) {
            e = commentsUtil.newComment(ctx, path, psId, c.side(), c.message);
          } else {
            e.writtenOn = ctx.getWhen();
            e.side = c.side();
            e.message = c.message;
          }

          if (parent != null) {
            e.parentUuid = parent;
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
        case KEEP:
        default:
          break;
        case DELETE:
          toDel.addAll(drafts.values());
          break;
        case PUBLISH:
          for (Comment e : drafts.values()) {
            toPublish.add(publishComment(ctx, e, ps));
          }
          break;
        case PUBLISH_ALL_REVISIONS:
          publishAllRevisions(ctx, drafts, toPublish);
          break;
      }
      ChangeUpdate u = ctx.getUpdate(psId);
      commentsUtil.deleteComments(ctx.getDb(), u, toDel);
      commentsUtil.putComments(ctx.getDb(), u, Status.PUBLISHED, toPublish);
      comments.addAll(toPublish);
      return !toDel.isEmpty() || !toPublish.isEmpty();
    }

    private boolean insertRobotComments(ChangeContext ctx) throws OrmException {
      if (in.robotComments == null) {
        return false;
      }

      List<RobotComment> toAdd = new ArrayList<>(in.robotComments.size());

      Set<CommentSetEntry> existingIds =
          in.omitDuplicateComments ? readExistingRobotComments(ctx) : Collections.emptySet();

      for (Map.Entry<String, List<RobotCommentInput>> ent : in.robotComments.entrySet()) {
        String path = ent.getKey();
        for (RobotCommentInput c : ent.getValue()) {
          RobotComment e =
              commentsUtil.newRobotComment(
                  ctx, path, psId, c.side(), c.message, c.robotId, c.robotRunId);
          e.parentUuid = Url.decode(c.inReplyTo);
          e.url = c.url;
          e.properties = c.properties;
          e.setLineNbrAndRange(c.line, c.range);
          e.tag = in.tag;
          setCommentRevId(e, patchListCache, ctx.getChange(), ps);

          if (existingIds.contains(CommentSetEntry.create(e))) {
            continue;
          }
          toAdd.add(e);
        }
      }

      commentsUtil.putRobotComments(ctx.getUpdate(psId), toAdd);
      comments.addAll(toAdd);
      return !toAdd.isEmpty();
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

    private Comment publishComment(ChangeContext ctx, Comment c, PatchSet ps) throws OrmException {
      c.writtenOn = ctx.getWhen();
      c.tag = in.tag;
      // Draft may have been created by a different real user; copy the current
      // real user. (Only applies to X-Gerrit-RunAs, since modifying drafts via
      // on_behalf_of is not allowed.)
      ctx.getUser().updateRealAccountId(c::setRealAuthor);
      setCommentRevId(c, patchListCache, ctx.getChange(), checkNotNull(ps));
      return c;
    }

    private void publishAllRevisions(
        ChangeContext ctx, Map<String, Comment> drafts, List<Comment> ups) throws OrmException {
      boolean needOtherPatchSets = false;
      for (Comment c : drafts.values()) {
        if (c.key.patchSetId != psId.get()) {
          needOtherPatchSets = true;
          break;
        }
      }
      Map<PatchSet.Id, PatchSet> patchSets =
          needOtherPatchSets
              ? psUtil.byChangeAsMap(ctx.getDb(), ctx.getNotes())
              : ImmutableMap.of(psId, ps);
      for (Comment e : drafts.values()) {
        ups.add(
            publishComment(
                ctx, e, patchSets.get(new PatchSet.Id(ctx.getChange().getId(), e.key.patchSetId))));
      }
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
      for (PostReviewers.Addition addition : reviewerResults) {
        if (addition.op.addedReviewers == null) {
          continue;
        }
        for (PatchSetApproval psa : addition.op.addedReviewers) {
          if (psa.getAccountId().equals(ctx.getAccountId())) {
            return true;
          }
        }
      }
      ChangeData cd = changeDataFactory.create(db.get(), ctx.getControl());
      ReviewerSet reviewers = cd.reviewers();
      if (reviewers.byState(REVIEWER).contains(ctx.getAccountId())) {
        return true;
      }
      return false;
    }

    private boolean updateLabels(ChangeContext ctx) throws OrmException, ResourceConflictException {
      Map<String, Short> inLabels =
          MoreObjects.firstNonNull(in.labels, Collections.<String, Short>emptyMap());

      // If no labels were modified and change is closed, abort early.
      // This avoids trying to record a modified label caused by a user
      // losing access to a label after the change was submitted.
      if (inLabels.isEmpty() && ctx.getChange().getStatus().isClosed()) {
        return false;
      }

      List<PatchSetApproval> del = new ArrayList<>();
      List<PatchSetApproval> ups = new ArrayList<>();
      Map<String, PatchSetApproval> current = scanLabels(ctx, del);
      LabelTypes labelTypes = ctx.getControl().getLabelTypes();
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

      forceCallerAsReviewer(ctx, current, ups, del);
      if (PrimaryStorage.of(update.getChange()) == REVIEW_DB) {
        // Avoid OrmConcurrencyException trying to delete non-existent entities.
        ctx.getDb().patchSetApprovals().delete(del);
        ctx.getDb().patchSetApprovals().upsert(ups);
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
      reduced.addAll(del);
      for (PatchSetApproval psa : ups) {
        LabelType lt = checkNotNull(labelTypes.byLabel(psa.getLabel()));
        String normName = lt.getName();
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
        ChangeContext ctx,
        Map<String, PatchSetApproval> current,
        List<PatchSetApproval> ups,
        List<PatchSetApproval> del) {
      if (current.isEmpty() && ups.isEmpty()) {
        // TODO Find another way to link reviewers to changes.
        if (del.isEmpty()) {
          // If no existing label is being set to 0, hack in the caller
          // as a reviewer by picking the first server-wide LabelType.
          LabelId labelId = ctx.getControl().getLabelTypes().getLabelTypes().get(0).getLabelId();
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

    private Map<String, PatchSetApproval> scanLabels(ChangeContext ctx, List<PatchSetApproval> del)
        throws OrmException {
      LabelTypes labelTypes = ctx.getControl().getLabelTypes();
      Map<String, PatchSetApproval> current = new HashMap<>();

      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(ctx.getDb(), ctx.getControl(), psId, user.getAccountId())) {
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
      for (String d : labelDelta) {
        buf.append(" ").append(d);
      }
      if (comments.size() == 1) {
        buf.append("\n\n(1 comment)");
      } else if (comments.size() > 1) {
        buf.append(String.format("\n\n(%d comments)", comments.size()));
      }
      if (!msg.isEmpty()) {
        buf.append("\n\n").append(msg);
      }
      if (buf.length() == 0) {
        return false;
      }

      message =
          ChangeMessagesUtil.newMessage(
              ctx.getDb(),
              psId,
              user,
              ctx.getWhen(),
              "Patch Set " + psId.get() + ":" + buf,
              in.tag);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), message);
      return true;
    }

    private void addLabelDelta(String name, short value) {
      labelDelta.add(LabelVote.create(name, value).format());
    }
  }
}
