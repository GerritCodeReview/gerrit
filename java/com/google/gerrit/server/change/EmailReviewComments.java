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

import static com.google.gerrit.server.CommentsUtil.COMMENT_ORDER;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class EmailReviewComments implements Runnable, RequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    // TODO(dborowitz/wyatta): Rationalize these arguments so HTML and text templates are operating
    // on the same set of inputs.
    /**
     * @param notify setting for handling notification.
     * @param notes change notes.
     * @param patchSet patch set corresponding to the top-level op
     * @param user user the email should come from.
     * @param message used by text template only: the full ChangeMessage that will go in the
     *     database. The contents of this message typically include the "Patch set N" header and "(M
     *     comments)".
     * @param comments inline comments.
     * @param patchSetComment used by HTML template only: some quasi-human-generated text. The
     *     contents should *not* include a "Patch set N" header or "(M comments)" footer, as these
     *     will be added automatically in soy in a structured way.
     * @param labels labels applied as part of this review operation.
     * @return handle for sending email.
     */
    EmailReviewComments create(
        NotifyResolver.Result notify,
        ChangeNotes notes,
        PatchSet patchSet,
        IdentifiedUser user,
        ChangeMessage message,
        List<Comment> comments,
        String patchSetComment,
        List<LabelVote> labels);
  }

  private final ExecutorService sendEmailsExecutor;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CommentSender.Factory commentSenderFactory;
  private final ThreadLocalRequestContext requestContext;

  private final NotifyResolver.Result notify;
  private final ChangeNotes notes;
  private final PatchSet patchSet;
  private final IdentifiedUser user;
  private final ChangeMessage message;
  private final List<Comment> comments;
  private final String patchSetComment;
  private final List<LabelVote> labels;

  @Inject
  EmailReviewComments(
      @SendEmailExecutor ExecutorService executor,
      PatchSetInfoFactory patchSetInfoFactory,
      CommentSender.Factory commentSenderFactory,
      ThreadLocalRequestContext requestContext,
      @Assisted NotifyResolver.Result notify,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet patchSet,
      @Assisted IdentifiedUser user,
      @Assisted ChangeMessage message,
      @Assisted List<Comment> comments,
      @Nullable @Assisted String patchSetComment,
      @Assisted List<LabelVote> labels) {
    this.sendEmailsExecutor = executor;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.requestContext = requestContext;
    this.notify = notify;
    this.notes = notes;
    this.patchSet = patchSet;
    this.user = user;
    this.message = message;
    this.comments = COMMENT_ORDER.sortedCopy(comments);
    this.patchSetComment = patchSetComment;
    this.labels = labels;
  }

  public void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(this);
  }

  @Override
  public void run() {
    RequestContext old = requestContext.setContext(this);
    try {
      CommentSender cm = commentSenderFactory.create(notes.getProjectName(), notes.getChangeId());
      cm.setFrom(user.getAccountId());
      cm.setPatchSet(patchSet, patchSetInfoFactory.get(notes.getProjectName(), patchSet));
      cm.setChangeMessage(message.getMessage(), message.getWrittenOn());
      cm.setComments(comments);
      cm.setPatchSetComment(patchSetComment);
      cm.setLabels(labels);
      cm.setNotify(notify);
      cm.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email comments for %s", patchSet.id());
    } finally {
      requestContext.setContext(old);
    }
  }

  @Override
  public String toString() {
    return "send-email comments";
  }

  @Override
  public CurrentUser getUser() {
    return user.getRealUser();
  }
}
