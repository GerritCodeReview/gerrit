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
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.ChangeNoLongerSubmittableSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Class to send an email asynchronously when a change becomes unsubmittable due to a change update.
 *
 * <p>An email is only sent when the change was submittable, but due to the update it is no longer
 * submittable.
 *
 * <p>Checking whether the change was submittable before/after the update and fetching the old/new
 * submit requirement results to include them into the email requires to run the submit requirements
 * evaluator (if the evaluator hasn't run before and hence no results were cached yet). Running the
 * submit requirements evaluator is expensive which is the reason why sending the email (and
 * checking whether it needs to be sent) is done asynchronously. This is important because the
 * provided {@link #preUpdateChangeData} usually doesn't have submit requirement results cached yet.
 *
 * <p>The {@link #postUpdateChangeData} is retrieved from {@link
 * PostUpdateContext#getChangeData(com.google.gerrit.entities.Change)} since this method is expected
 * to return a cached {@link ChangeData} instance that already has the the submit requirement
 * results populated (the returned {@link ChangeData} instance was created and populated during the
 * (re)indexing that was triggered by the change update).
 */
public class ChangeNoLongerSubmittableEmail implements Runnable, RequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ChangeNoLongerSubmittableEmail create(
        PostUpdateContext ctx,
        ChangeNoLongerSubmittableSender.UpdateKind updateKind,
        ChangeData preUpdateChangeData);
  }

  private final ExecutorService sendEmailsExecutor;
  private final ChangeNoLongerSubmittableSender.Factory senderFactory;
  private final MessageIdGenerator messageIdGenerator;
  private final Context ctx;
  private final ChangeNoLongerSubmittableSender.UpdateKind updateKind;
  private final ChangeData preUpdateChangeData;
  private final ChangeData postUpdateChangeData;

  @Inject
  ChangeNoLongerSubmittableEmail(
      @SendEmailExecutor ExecutorService sendEmailsExecutor,
      ChangeNoLongerSubmittableSender.Factory senderFactory,
      MessageIdGenerator messageIdGenerator,
      @Assisted PostUpdateContext ctx,
      @Assisted ChangeNoLongerSubmittableSender.UpdateKind updateKind,
      @Assisted ChangeData preUpdateChangeData) {
    this.sendEmailsExecutor = sendEmailsExecutor;
    this.senderFactory = senderFactory;
    this.messageIdGenerator = messageIdGenerator;
    this.ctx = ctx;
    this.updateKind = updateKind;
    this.preUpdateChangeData = preUpdateChangeData;
    this.postUpdateChangeData = ctx.getChangeData(preUpdateChangeData.change());
  }

  public void sendIfNeededAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(this);
  }

  @Override
  public void run() {
    try {
      if (!isChangeNoLongerSubmittable()) {
        logger.atFine().log("skip sending email for change %s", postUpdateChangeData.getId());
        return;
      }

      ChangeNoLongerSubmittableSender sender =
          senderFactory.create(updateKind, preUpdateChangeData, postUpdateChangeData);
      Optional<Account.Id> accountId =
          ctx.getUser().isIdentifiedUser()
              ? Optional.of(ctx.getUser().asIdentifiedUser().getAccountId())
              : Optional.empty();
      if (accountId.isPresent()) {
        sender.setFrom(accountId.get());
      }
      sender.setNotify(ctx.getNotify(postUpdateChangeData.getId()));
      sender.setMessageId(
          messageIdGenerator.fromChangeUpdate(
              ctx.getRepoView(), postUpdateChangeData.currentPatchSet().id()));
      sender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Cannot email update for change %s", postUpdateChangeData.getId());
    }
  }

  /**
   * Checks whether the change is no longer submittable.
   *
   * @return {@code true} if the change has been submittable before the update and is no longer
   *     submittable after the update has been applied, otherwise {@code false}
   */
  private boolean isChangeNoLongerSubmittable() {
    boolean isSubmittablePreUpdate =
        preUpdateChangeData.submitRequirementsIncludingLegacy().values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s before the update is %s",
        postUpdateChangeData.getId(), isSubmittablePreUpdate);
    if (!isSubmittablePreUpdate) {
      return false;
    }

    boolean isSubmittablePostUpdate =
        postUpdateChangeData.submitRequirementsIncludingLegacy().values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s after the update is %s",
        postUpdateChangeData.getId(), isSubmittablePostUpdate);
    return !isSubmittablePostUpdate;
  }

  @Override
  public String toString() {
    return "send-email-change-no-longer-aubmittable";
  }

  @Override
  public CurrentUser getUser() {
    return ctx.getUser();
  }
}
