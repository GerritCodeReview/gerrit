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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;

public class EmailNewPatchSet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    EmailNewPatchSet create(
        PostUpdateContext postUpdateContext,
        PatchSet patchSet,
        @Nullable String message,
        ImmutableSet<PatchSetApproval> outdatedApprovals,
        @Assisted("reviewers") ImmutableSet<Account.Id> reviewers,
        @Assisted("extraCcs") ImmutableSet<Account.Id> extraCcs,
        ChangeKind changeKind,
        ObjectId preUpdateMetaId);
  }

  private final ExecutorService sendEmailExecutor;
  private final ThreadLocalRequestContext threadLocalRequestContext;
  private final AsyncSender asyncSender;

  private RequestScopePropagator requestScopePropagator;

  @Inject
  EmailNewPatchSet(
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      ThreadLocalRequestContext threadLocalRequestContext,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      MessageIdGenerator messageIdGenerator,
      @Assisted PostUpdateContext postUpdateContext,
      @Assisted PatchSet patchSet,
      @Nullable @Assisted String message,
      @Assisted ImmutableSet<PatchSetApproval> outdatedApprovals,
      @Assisted("reviewers") ImmutableSet<Account.Id> reviewers,
      @Assisted("extraCcs") ImmutableSet<Account.Id> extraCcs,
      @Assisted ChangeKind changeKind,
      @Assisted ObjectId preUpdateMetaId) {
    this.sendEmailExecutor = sendEmailExecutor;
    this.threadLocalRequestContext = threadLocalRequestContext;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              postUpdateContext.getRepoView(), patchSet.id(), "EmailReplacePatchSet");
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
            postUpdateContext.getIdentifiedUser(),
            replacePatchSetFactory,
            patchSetInfoFactory,
            messageId,
            postUpdateContext.getNotify(changeId),
            postUpdateContext.getProject(),
            changeId,
            patchSet,
            message,
            postUpdateContext.getWhen(),
            outdatedApprovals,
            reviewers,
            extraCcs,
            changeKind,
            preUpdateMetaId,
            postUpdateSubmitRequirementResults);
  }

  public EmailNewPatchSet setRequestScopePropagator(RequestScopePropagator requestScopePropagator) {
    this.requestScopePropagator = requestScopePropagator;
    return this;
  }

  public void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        sendEmailExecutor.submit(
            requestScopePropagator != null
                ? requestScopePropagator.wrap(asyncSender)
                : () -> {
                  RequestContext old = threadLocalRequestContext.setContext(asyncSender);
                  try {
                    asyncSender.run();
                  } finally {
                    threadLocalRequestContext.setContext(old);
                  }
                });
  }

  /**
   * {@link Runnable} that sends the email asynchonously.
   *
   * <p>Only pass objects into this class that are thread-safe (e.g. immutable) so that they can be
   * safely accessed from the background thread.
   */
  private static class AsyncSender implements Runnable, RequestContext {
    private final IdentifiedUser user;
    private final ReplacePatchSetSender.Factory replacePatchSetFactory;
    private final PatchSetInfoFactory patchSetInfoFactory;
    private final MessageId messageId;
    private final NotifyResolver.Result notify;
    private final Project.NameKey projectName;
    private final Change.Id changeId;
    private final PatchSet patchSet;
    private final String message;
    private final Instant timestamp;
    private final ImmutableSet<PatchSetApproval> outdatedApprovals;
    private final ImmutableSet<Account.Id> reviewers;
    private final ImmutableSet<Account.Id> extraCcs;
    private final ChangeKind changeKind;
    private final ObjectId preUpdateMetaId;
    private final Map<SubmitRequirement, SubmitRequirementResult>
        postUpdateSubmitRequirementResults;

    AsyncSender(
        IdentifiedUser user,
        ReplacePatchSetSender.Factory replacePatchSetFactory,
        PatchSetInfoFactory patchSetInfoFactory,
        MessageId messageId,
        NotifyResolver.Result notify,
        Project.NameKey projectName,
        Change.Id changeId,
        PatchSet patchSet,
        String message,
        Instant timestamp,
        ImmutableSet<PatchSetApproval> outdatedApprovals,
        ImmutableSet<Account.Id> reviewers,
        ImmutableSet<Account.Id> extraCcs,
        ChangeKind changeKind,
        ObjectId preUpdateMetaId,
        Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
      this.user = user;
      this.replacePatchSetFactory = replacePatchSetFactory;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.messageId = messageId;
      this.notify = notify;
      this.projectName = projectName;
      this.changeId = changeId;
      this.patchSet = patchSet;
      this.message = message;
      this.timestamp = timestamp;
      this.outdatedApprovals = outdatedApprovals;
      this.reviewers = reviewers;
      this.extraCcs = extraCcs;
      this.changeKind = changeKind;
      this.preUpdateMetaId = preUpdateMetaId;
      this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
    }

    @Override
    public void run() {
      try {
        ReplacePatchSetSender emailSender =
            replacePatchSetFactory.create(
                projectName,
                changeId,
                changeKind,
                preUpdateMetaId,
                postUpdateSubmitRequirementResults);
        emailSender.setFrom(user.getAccountId());
        emailSender.setPatchSet(patchSet, patchSetInfoFactory.get(projectName, patchSet));
        emailSender.setChangeMessage(message, timestamp);
        emailSender.setNotify(notify);
        emailSender.addReviewers(reviewers);
        emailSender.addExtraCC(extraCcs);
        emailSender.addOutdatedApproval(outdatedApprovals);
        emailSender.setMessageId(messageId);
        emailSender.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot send email for new patch set %s", patchSet.id());
      }
    }

    @Override
    public String toString() {
      return "send-email newpatchset";
    }

    @Override
    public CurrentUser getUser() {
      return user.getRealUser();
    }
  }
}
