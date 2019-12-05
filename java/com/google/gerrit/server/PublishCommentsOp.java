// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
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
 * A {@link BatchUpdateOp} that can be used to publish draft comments
 *
 * <p>This class uses the {@link PublishCommentUtil} to publish draft comments and fires the
 * necessary event for this.
 */
public class PublishCommentsOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PatchSetUtil psUtil;
  private final PublishCommentUtil publishCommentUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CommentAdded commentAdded;
  private final EmailReviewComments.Factory email;
  private final ProjectCache projectCache;
  private final CommentsUtil commentsUtil;
  private final List<LabelVote> labelDelta = new ArrayList<>();
  private final Map<String, Short> approvals = new HashMap<>();
  private final Map<String, Short> oldApprovals = new HashMap<>();

  private ChangeNotes changeNotes;
  private ChangeMessage message;
  private List<Comment> comments = new ArrayList<>();
  private IdentifiedUser user;
  private PatchSet ps;
  private PatchSet.Id psId;

  public interface Factory {
    PublishCommentsOp create(PatchSet.Id psId);
  }

  @Inject
  public PublishCommentsOp(
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      CommentsUtil commentsUtil,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      PublishCommentUtil publishCommentUtil,
      @Assisted PatchSet.Id psId) {
    this.cmUtil = cmUtil;
    this.commentAdded = commentAdded;
    this.commentsUtil = commentsUtil;
    this.email = email;
    this.psId = psId;
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
    publishCommentUtil.publish(ctx, psId, comments, null);
    return insertMessage(ctx);
  }

  @Override
  public void postUpdate(Context ctx) {
    if (message == null || comments.isEmpty()) {
      return;
    }
    NotifyResolver.Result notify = ctx.getNotify(changeNotes.getChangeId());
    if (notify.shouldNotify()) {
      email.create(notify, changeNotes, ps, user, message, comments, null, labelDelta).sendAsync();
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

  private boolean insertMessage(ChangeContext ctx) {
    StringBuilder buf = new StringBuilder();
    if (comments.size() == 1) {
      buf.append("\n\n(1 comment)");
    } else if (comments.size() > 1) {
      buf.append(String.format("\n\n(%d comments)", comments.size()));
    }
    if (buf.length() == 0) {
      return false;
    }
    message =
        ChangeMessagesUtil.newMessage(
            psId, user, ctx.getWhen(), "Patch Set " + psId.get() + ":" + buf, null);
    cmUtil.addChangeMessage(ctx.getUpdate(psId), message);
    return true;
  }
}
