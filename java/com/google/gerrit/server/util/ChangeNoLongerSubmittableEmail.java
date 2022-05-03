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

package com.google.gerrit.server.util;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeNoLongerSubmittableOp;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.ChangeNoLongerSubmittableSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to send an email asynchronously when a change becomes unsubmittable due to a change update.
 *
 * <p>An email is only sent when the change was submittable, but due to the update it is no longer
 * submittable.
 *
 * <p>Sending the email asynchronously is important because sending the email requires to evaluate
 * the submit requirements for the pre-update change state which is expensive (see {@link
 * ChangeNoLongerSubmittableSender}) and we do not want to increase the latency of the user request.
 *
 * <p>To avoid synchronization issues between the main thread and the background thread that's
 * sending the email we only pass immutable objects to the background thread that are known to be
 * thread-safe.
 *
 * @see ChangeNoLongerSubmittableSender
 * @see ChangeNoLongerSubmittableOp
 */
public class ChangeNoLongerSubmittableEmail {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ChangeNoLongerSubmittableEmail create(
        PostUpdateContext ctx,
        Change.Id changeId,
        ChangeNoLongerSubmittableSender.UpdateKind updateKind,
        ObjectId preUpdateMetaId);
  }

  private final ExecutorService sendEmailsExecutor;
  private final AsyncSender asyncSender;

  @Inject
  ChangeNoLongerSubmittableEmail(
      @SendEmailExecutor ExecutorService sendEmailsExecutor,
      ChangeNoLongerSubmittableSender.Factory senderFactory,
      MessageIdGenerator messageIdGenerator,
      @Assisted PostUpdateContext ctx,
      @Assisted Change.Id changeId,
      @Assisted ChangeNoLongerSubmittableSender.UpdateKind updateKind,
      @Assisted ObjectId preUpdateMetaId) {
    this.sendEmailsExecutor = sendEmailsExecutor;

    // Getting the post-update change data from PostUpdateContext retrieves a cached ChangeData
    // instance. This ChangeData instance has been created when the change was (re)indexed due to
    // the update, and hence has submit requirement results already cached (since (re)indexing
    // triggers the evaluation of the submit requirements).
    ChangeData postUpdateChangeData = ctx.getChangeData(ctx.getProject(), changeId);

    try {
      this.asyncSender =
          new AsyncSender(
              senderFactory,
              ctx.getUser(),
              ctx.getNotify(changeId),
              ctx.getProject(),
              changeId,
              updateKind,
              preUpdateMetaId,
              postUpdateChangeData.submitRequirementsIncludingLegacy(),
              messageIdGenerator.fromChangeUpdate(
                  ctx.getRepoView(), postUpdateChangeData.currentPatchSet().id()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void sendIfNeededAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(asyncSender);
  }

  /**
   * {@link Runnable} that sends the email asynchonously.
   *
   * <p>Only pass objects into this class that are thread-safe (e.g. immutable) so that they can be
   * safely accessed from the background thread.
   */
  private static class AsyncSender implements Runnable, RequestContext {
    private final CurrentUser currentUser;
    private final ChangeNoLongerSubmittableSender.Factory senderFactory;
    private final Optional<Account.Id> currentUserId;
    private final NotifyResolver.Result notify;
    private final Project.NameKey projectName;
    private final Change.Id changeId;
    private final ChangeNoLongerSubmittableSender.UpdateKind updateKind;
    private final ObjectId preUpdateMetaId;
    private final Map<SubmitRequirement, SubmitRequirementResult>
        postUpdateSubmitRequirementResults;
    private final MessageId messageId;

    AsyncSender(
        ChangeNoLongerSubmittableSender.Factory senderFactory,
        CurrentUser currentUser,
        NotifyResolver.Result notify,
        Project.NameKey projectName,
        Change.Id changeId,
        ChangeNoLongerSubmittableSender.UpdateKind updateKind,
        ObjectId preUpdateMetaId,
        Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults,
        MessageId messageId) {
      this.senderFactory = senderFactory;
      this.currentUser = currentUser;
      this.currentUserId =
          currentUser.isIdentifiedUser()
              ? Optional.of(currentUser.asIdentifiedUser().getAccountId())
              : Optional.empty();
      this.notify = notify;
      this.projectName = projectName;
      this.changeId = changeId;
      this.updateKind = updateKind;
      this.preUpdateMetaId = preUpdateMetaId;
      this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
      this.messageId = messageId;
    }

    @Override
    public void run() {
      try {
        ChangeNoLongerSubmittableSender sender =
            senderFactory.create(
                projectName,
                changeId,
                updateKind,
                preUpdateMetaId,
                postUpdateSubmitRequirementResults);
        currentUserId.ifPresent(sender::setFrom);
        sender.setNotify(notify);
        sender.setMessageId(messageId);
        sender.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", changeId);
      }
    }

    @Override
    public String toString() {
      return "send-email-change-no-longer-aubmittable";
    }

    @Override
    public CurrentUser getUser() {
      return currentUser;
    }
  }
}
