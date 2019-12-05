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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
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

  private final Map<String, Short> approvals = new HashMap<>();
  private final Map<String, Short> oldApprovals = new HashMap<>();
  private final PatchSetUtil psUtil;
  private final int changeUpdateId;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeMessagesUtil cmUtil;
  private final CommentAdded commentAdded;
  private final CommentsUtil commentsUtil;
  private final EmailReviewComments.Factory email;
  private final List<LabelVote> labelDelta = new ArrayList<>();
  private final ProjectCache projectCache;
  private final Project.NameKey projectNameKey;
  private final PatchSet.Id psId;
  private final PublishCommentUtil publishCommentUtil;

  private List<Comment> comments = new ArrayList<>();
  private ChangeMessage message;
  private IdentifiedUser user;

  public interface Factory {
    PublishCommentsOp create(PatchSet.Id psId, int changeUpdateId, Project.NameKey projectNameKey);
  }

  @Inject
  public PublishCommentsOp(
      ChangeNotes.Factory changeNotesFactory,
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      CommentsUtil commentsUtil,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      PublishCommentUtil publishCommentUtil,
      @Assisted PatchSet.Id psId,
      @Assisted int changeUpdateId,
      @Assisted Project.NameKey projectNameKey) {
    this.cmUtil = cmUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateId = changeUpdateId;
    this.commentAdded = commentAdded;
    this.commentsUtil = commentsUtil;
    this.email = email;
    this.psId = psId;
    this.projectCache = projectCache;
    this.publishCommentUtil = publishCommentUtil;
    this.psUtil = psUtil;
    this.projectNameKey = projectNameKey;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, UnprocessableEntityException, IOException,
          PatchListNotAvailableException, CommentsRejectedException {
    user = ctx.getIdentifiedUser();
    comments = commentsUtil.draftByChangeAuthor(ctx.getNotes(), ctx.getUser().getAccountId());

    // PublishCommentsOp should update a separate ChangeUpdate Object than the one used by other ops
    // For example, with the "publish comments on PS upload" workflow,
    // There are 2 ops: ReplaceOp & PublishCommentsOp, where each updates its own ChangeUpdate
    // This is required since
    //   1. a ChangeUpdate has only 1 change message
    //   2. Each ChangeUpdate results in 1 commit in NoteDb
    // We do it this way so that the execution results in 2 different commits in NoteDb
    publishCommentUtil.publish(ctx, ctx.getUpdate(psId, changeUpdateId), comments, null);
    return insertMessage(ctx);
  }

  @Override
  public void postUpdate(Context ctx) {
    if (message == null || comments.isEmpty()) {
      return;
    }
    ChangeNotes changeNotes = changeNotesFactory.createChecked(projectNameKey, psId.changeId());
    PatchSet ps = psUtil.get(changeNotes, psId);
    NotifyResolver.Result notify = ctx.getNotify(changeNotes.getChangeId());
    if (notify.shouldNotify()) {
      email.create(notify, changeNotes, ps, user, message, comments, null, labelDelta).sendAsync();
    }
    try {
      fireCommentAddedEvent(ctx, changeNotes, ps);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("comment-added event invocation failed");
    }
  }

  private void fireCommentAddedEvent(Context ctx, ChangeNotes changeNotes, PatchSet ps)
      throws IOException {
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
    cmUtil.addChangeMessage(ctx.getUpdate(psId, changeUpdateId), message);
    return true;
  }
}
