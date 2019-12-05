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

package com.google.gerrit.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An Op that can be used to publish draft comments
 *
 * <p>This class uses the PublishCommentUtil to publish draft comments and fires the necessary event
 * for this.
 */
public class PublishCommentsOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  private PatchSetUtil psUtil;
  private PublishCommentUtil publishCommentUtil;

  protected ChangeNotes changeNotes;
  protected ChangeMessage message;
  protected List<Comment> comments = new ArrayList<>();
  protected IdentifiedUser user;
  protected PatchSet ps;
  protected PatchSet.Id psId;
  protected ReviewInput in;
  protected List<LabelVote> labelDelta = new ArrayList<>();
  protected Map<String, Short> approvals = new HashMap<>();
  protected Map<String, Short> oldApprovals = new HashMap<>();

  private ChangeMessagesUtil cmUtil;
  private CommentAdded commentAdded;
  private EmailReviewComments.Factory email;
  private PluginSetContext<CommentValidator> commentValidators;

  private ProjectCache projectCache;
  private CommentsUtil commentsUtil;

  public interface Factory {
    PublishCommentsOp create(PatchSet.Id psId, ReviewInput in);
  }

  @Inject
  public PublishCommentsOp(
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      CommentsUtil commentsUtil,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      PluginSetContext<CommentValidator> commentValidators,
      ProjectCache projectCache,
      PublishCommentUtil publishCommentUtil,
      @Assisted PatchSet.Id psId,
      @Assisted ReviewInput in) {
    this.cmUtil = cmUtil;
    this.commentAdded = commentAdded;
    this.commentsUtil = commentsUtil;
    this.email = email;
    this.commentValidators = commentValidators;
    this.psId = psId;
    this.in = in;
    this.projectCache = projectCache;
    this.publishCommentUtil = publishCommentUtil;
    this.psUtil = psUtil;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, UnprocessableEntityException, IOException,
          PatchListNotAvailableException, CommentsRejectedException {
    user = ctx.getIdentifiedUser();
    changeNotes = ctx.getNotes();
    ps = psUtil.get(ctx.getNotes(), psId);
    comments = commentsUtil.draftByChangeAuthor(ctx.getNotes(), ctx.getUser().getAccountId());
    publishCommentUtil.publish(ctx, psId, comments, in.tag);
    boolean dirty = insertMessage(ctx);
    return dirty;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (message == null) {
      return;
    }
    NotifyResolver.Result notify = ctx.getNotify(changeNotes.getChangeId());
    if (notify.shouldNotify()) {
      email
          .create(notify, changeNotes, ps, user, message, comments, in.message, labelDelta)
          .sendAsync();
    }
    try {
      fireCommentAddedEvent(ctx);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("comment-added event invocation failed");
    }
  }

  private void fireCommentAddedEvent(Context ctx) throws IOException {
    if (approvals.isEmpty()) {
      return;
    }

    Map<String, Short> allApprovals = new HashMap<>();
    Map<String, Short> oldApprovals = new HashMap<>();

    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    List<LabelType> labels =
        projectCache.checkedGet(ctx.getProject()).getLabelTypes(changeNotes).getLabelTypes();
    for (LabelType lt : labels) {
      allApprovals.put(lt.getName(), (short) 0);
      oldApprovals.put(lt.getName(), null);
    }
    for (Map.Entry<String, Short> entry : approvals.entrySet()) {
      if (entry.getValue() != 0) {
        allApprovals.put(entry.getKey(), entry.getValue());
        oldApprovals.put(entry.getKey(), (short) 0);
      }
    }

    commentAdded.fire(
        changeNotes.getChange(),
        ps,
        ctx.getAccount(),
        message.getMessage(),
        allApprovals,
        oldApprovals,
        ctx.getWhen());
  }

  protected boolean insertMessage(ChangeContext ctx) throws CommentsRejectedException {
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
      ImmutableList<CommentValidationFailure> messageValidationFailure =
          PublishCommentUtil.findInvalidComments(
              commentValidators,
              ImmutableList.of(
                  CommentForValidation.create(
                      CommentForValidation.CommentType.CHANGE_MESSAGE, msg)));
      if (!messageValidationFailure.isEmpty()) {
        throw new CommentsRejectedException(messageValidationFailure);
      }
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
}
