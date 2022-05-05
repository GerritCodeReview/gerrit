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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class EmailReviewComments {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    // TODO(dborowitz/wyatta): Rationalize these arguments so HTML and text templates are operating
    // on the same set of inputs.
    /**
     * Creates handle for sending email
     *
     * @param notify setting for handling notification.
     * @param notes change notes.
     * @param patchSet patch set corresponding to the top-level op
     * @param user user the email should come from.
     * @param message used by text template only. The contents of this message typically include the
     *     "Patch set N" header and "(M comments)".
     * @param timestamp timestamp when the comments were added.
     * @param comments inline comments.
     * @param patchSetComment used by HTML template only: some quasi-human-generated text. The
     *     contents should *not* include a "Patch set N" header or "(M comments)" footer, as these
     *     will be added automatically in soy in a structured way.
     * @param labels labels applied as part of this review operation.
     */
    EmailReviewComments create(
        NotifyResolver.Result notify,
        ChangeNotes notes,
        PatchSet patchSet,
        IdentifiedUser user,
        @Assisted("message") String message,
        Instant timestamp,
        List<? extends Comment> comments,
        @Nullable @Assisted("patchSetComment") String patchSetComment,
        List<LabelVote> labels,
        RepoView repoView);
  }

  private final ExecutorService sendEmailsExecutor;
  private final AsyncSender asyncSender;

  @Inject
  EmailReviewComments(
      @SendEmailExecutor ExecutorService executor,
      PatchSetInfoFactory patchSetInfoFactory,
      CommentSender.Factory commentSenderFactory,
      ThreadLocalRequestContext requestContext,
      MessageIdGenerator messageIdGenerator,
      @Assisted NotifyResolver.Result notify,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet patchSet,
      @Assisted IdentifiedUser user,
      @Assisted("message") String message,
      @Assisted Instant timestamp,
      @Assisted List<? extends Comment> comments,
      @Nullable @Assisted("patchSetComment") String patchSetComment,
      @Assisted List<LabelVote> labels,
      @Assisted RepoView repoView) {
    this.sendEmailsExecutor = executor;
    this.asyncSender =
        new AsyncSender(
            requestContext,
            commentSenderFactory,
            patchSetInfoFactory,
            user,
            messageIdGenerator.fromChangeUpdateAndReason(
                repoView, patchSet.id(), "EmailReviewComments"),
            notify,
            notes.getProjectName(),
            notes.getChangeId(),
            patchSet,
            message,
            timestamp,
            ImmutableList.copyOf(COMMENT_ORDER.sortedCopy(comments)),
            patchSetComment,
            ImmutableList.copyOf(labels));
  }

  public void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(asyncSender);
  }

  /**
   * {@link Runnable} that sends the email asynchonously.
   *
   * <p>Only pass objects into this class that are thread-safe (e.g. immutable) so that they can be
   * safely accessed from the background thread.
   */
  // TODO: The passed in Comment class is not thread-safe, replace it with an AutoValue type.
  private static class AsyncSender implements Runnable, RequestContext {
    private final ThreadLocalRequestContext requestContext;
    private final CommentSender.Factory commentSenderFactory;
    private final PatchSetInfoFactory patchSetInfoFactory;
    private final IdentifiedUser user;
    private final MessageId messageId;
    private final NotifyResolver.Result notify;
    private final Project.NameKey projectName;
    private final Change.Id changeId;
    private final PatchSet patchSet;
    private final String message;
    private final Instant timestamp;
    private final ImmutableList<? extends Comment> comments;
    @Nullable private final String patchSetComment;
    private final ImmutableList<LabelVote> labels;

    AsyncSender(
        ThreadLocalRequestContext requestContext,
        CommentSender.Factory commentSenderFactory,
        PatchSetInfoFactory patchSetInfoFactory,
        IdentifiedUser user,
        MessageId messageId,
        NotifyResolver.Result notify,
        Project.NameKey projectName,
        Change.Id changeId,
        PatchSet patchSet,
        String message,
        Instant timestamp,
        ImmutableList<? extends Comment> comments,
        @Nullable String patchSetComment,
        ImmutableList<LabelVote> labels) {
      this.requestContext = requestContext;
      this.commentSenderFactory = commentSenderFactory;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.user = user;
      this.messageId = messageId;
      this.notify = notify;
      this.projectName = projectName;
      this.changeId = changeId;
      this.patchSet = patchSet;
      this.message = message;
      this.timestamp = timestamp;
      this.comments = comments;
      this.patchSetComment = patchSetComment;
      this.labels = labels;
    }

    @Override
    public void run() {
      RequestContext old = requestContext.setContext(this);
      try {
        CommentSender emailSender = commentSenderFactory.create(projectName, changeId);
        emailSender.setFrom(user.getAccountId());
        emailSender.setPatchSet(patchSet, patchSetInfoFactory.get(projectName, patchSet));
        emailSender.setChangeMessage(message, timestamp);
        emailSender.setComments(comments);
        emailSender.setPatchSetComment(patchSetComment);
        emailSender.setLabels(labels);
        emailSender.setNotify(notify);
        emailSender.setMessageId(messageId);
        emailSender.send();
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
}
