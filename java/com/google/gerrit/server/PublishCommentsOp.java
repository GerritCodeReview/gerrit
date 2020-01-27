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
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
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
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeMessagesUtil cmUtil;
  private final CommentAdded commentAdded;
  private final CommentsUtil commentsUtil;
  private final EmailReviewComments.Factory email;
  private final List<LabelVote> labelDelta = new ArrayList<>();
  private final Project.NameKey projectNameKey;
  private final PatchSet.Id psId;
  private final PublishCommentUtil publishCommentUtil;

  private List<Comment> comments = new ArrayList<>();
  private ChangeMessage message;
  private IdentifiedUser user;

  public interface Factory {
    PublishCommentsOp create(PatchSet.Id psId, Project.NameKey projectNameKey);
  }

  @Inject
  public PublishCommentsOp(
      ChangeNotes.Factory changeNotesFactory,
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      CommentsUtil commentsUtil,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      PublishCommentUtil publishCommentUtil,
      @Assisted PatchSet.Id psId,
      @Assisted Project.NameKey projectNameKey) {
    this.cmUtil = cmUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.commentAdded = commentAdded;
    this.commentsUtil = commentsUtil;
    this.email = email;
    this.psId = psId;
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
    ChangeUpdate changeUpdate = ctx.getDistinctUpdate(psId);
    publishCommentUtil.publish(ctx, changeUpdate, comments, null);
    return insertMessage(ctx, changeUpdate);
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
    commentAdded.fire(
        changeNotes.getChange(),
        ps,
        ctx.getAccount(),
        message.getMessage(),
        null,
        null,
        ctx.getWhen());
  }

  private boolean insertMessage(ChangeContext ctx, ChangeUpdate changeUpdate) {
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
    cmUtil.addChangeMessage(changeUpdate, message);
    return true;
  }
}
