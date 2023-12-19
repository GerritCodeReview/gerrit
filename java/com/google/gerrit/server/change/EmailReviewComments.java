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
import static com.google.gerrit.server.mail.EmailFactories.COMMENTS_ADDED;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.CommentChangeEmailDecorator;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;

public class EmailReviewComments {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    // TODO(dborowitz/wyatta): Rationalize these arguments so HTML and text templates are operating
    // on the same set of inputs.
    /**
     * Creates handle for sending email
     *
     * @param postUpdateContext the post update context from the calling BatchUpdateOp
     * @param patchSet patch set corresponding to the top-level op
     * @param preUpdateMetaId the SHA1 to which the notes branch pointed before the update
     * @param message used by text template only. The contents of this message typically include the
     *     "Patch set N" header and "(M comments)".
     * @param comments inline comments.
     * @param patchSetComment used by HTML template only: some quasi-human-generated text. The
     *     contents should *not* include a "Patch set N" header or "(M comments)" footer, as these
     *     will be added automatically in soy in a structured way.
     * @param labels labels applied as part of this review operation.
     */
    EmailReviewComments create(
        PostUpdateContext postUpdateContext,
        PatchSet patchSet,
        ObjectId preUpdateMetaId,
        @Assisted("message") String message,
        List<? extends Comment> comments,
        @Nullable @Assisted("patchSetComment") String patchSetComment,
        List<LabelVote> labels);
  }

  private final ExecutorService sendEmailsExecutor;
  private final AsyncSender asyncSender;

  @Inject
  EmailReviewComments(
      @SendEmailExecutor ExecutorService executor,
      PatchSetInfoFactory patchSetInfoFactory,
      EmailFactories emailFactories,
      ThreadLocalRequestContext requestContext,
      MessageIdGenerator messageIdGenerator,
      @Assisted PostUpdateContext postUpdateContext,
      @Assisted PatchSet patchSet,
      @Assisted ObjectId preUpdateMetaId,
      @Assisted("message") String message,
      @Assisted List<? extends Comment> comments,
      @Nullable @Assisted("patchSetComment") String patchSetComment,
      @Assisted List<LabelVote> labels) {
    this.sendEmailsExecutor = executor;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              postUpdateContext.getRepoView(), patchSet.id(), "EmailReviewComments");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    Change.Id changeId = patchSet.id().changeId();

    // Getting the change data from PostUpdateContext retrieves a cached ChangeData
    // instance. This ChangeData instance has been created when the change was (re)indexed
    // due to the update, and hence has submit requirement results already cached (since
    // (re)indexing triggers the evaluation of the submit requirements).
    Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults =
        postUpdateContext
            .getChangeData(postUpdateContext.getProject(), changeId)
            .submitRequirementsIncludingLegacy();
    this.asyncSender =
        new AsyncSender(
            requestContext,
            emailFactories,
            patchSetInfoFactory,
            postUpdateContext.getUser().asIdentifiedUser(),
            messageId,
            postUpdateContext.getNotify(changeId),
            postUpdateContext.getProject(),
            changeId,
            patchSet,
            preUpdateMetaId,
            message,
            postUpdateContext.getWhen(),
            ImmutableList.copyOf(COMMENT_ORDER.sortedCopy(comments)),
            patchSetComment,
            ImmutableList.copyOf(labels),
            postUpdateSubmitRequirementResults);
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
    private final EmailFactories emailFactories;
    private final PatchSetInfoFactory patchSetInfoFactory;
    private final IdentifiedUser user;
    private final MessageId messageId;
    private final NotifyResolver.Result notify;
    private final Project.NameKey projectName;
    private final Change.Id changeId;
    private final PatchSet patchSet;
    private final ObjectId preUpdateMetaId;
    private final String message;
    private final Instant timestamp;
    private final ImmutableList<? extends Comment> comments;
    @Nullable private final String patchSetComment;
    private final ImmutableList<LabelVote> labels;
    private final Map<SubmitRequirement, SubmitRequirementResult>
        postUpdateSubmitRequirementResults;

    AsyncSender(
        ThreadLocalRequestContext requestContext,
        EmailFactories emailFactories,
        PatchSetInfoFactory patchSetInfoFactory,
        IdentifiedUser user,
        MessageId messageId,
        NotifyResolver.Result notify,
        Project.NameKey projectName,
        Change.Id changeId,
        PatchSet patchSet,
        ObjectId preUpdateMetaId,
        String message,
        Instant timestamp,
        ImmutableList<? extends Comment> comments,
        @Nullable String patchSetComment,
        ImmutableList<LabelVote> labels,
        Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
      this.requestContext = requestContext;
      this.emailFactories = emailFactories;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.user = user;
      this.messageId = messageId;
      this.notify = notify;
      this.projectName = projectName;
      this.changeId = changeId;
      this.patchSet = patchSet;
      this.preUpdateMetaId = preUpdateMetaId;
      this.message = message;
      this.timestamp = timestamp;
      this.comments = comments;
      this.patchSetComment = patchSetComment;
      this.labels = labels;
      this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
    }

    @Override
    public void run() {
      RequestContext old = requestContext.setContext(this);
      try {
        CommentChangeEmailDecorator commentChangeEmail =
            emailFactories.createCommentChangeEmail(
                projectName, changeId, preUpdateMetaId, postUpdateSubmitRequirementResults);
        commentChangeEmail.setComments(comments);
        commentChangeEmail.setPatchSetComment(patchSetComment);
        commentChangeEmail.setLabels(labels);
        ChangeEmail changeEmail =
            emailFactories.createChangeEmail(projectName, changeId, commentChangeEmail);
        changeEmail.setPatchSet(patchSet, patchSetInfoFactory.get(projectName, patchSet));
        changeEmail.setChangeMessage(message, timestamp);
        OutgoingEmail outgoingEmail =
            emailFactories.createOutgoingEmail(COMMENTS_ADDED, changeEmail);
        outgoingEmail.setFrom(user.getAccountId());
        outgoingEmail.setNotify(notify);
        outgoingEmail.setMessageId(messageId);
        outgoingEmail.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email comments for %s", patchSet.id());
      } finally {
        @SuppressWarnings("unused")
        var unused = requestContext.setContext(old);
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
