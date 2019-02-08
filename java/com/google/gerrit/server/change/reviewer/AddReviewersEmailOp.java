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

package com.google.gerrit.server.change.reviewer;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AddReviewersEmailOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    AddReviewersEmailOp create(
        @Assisted Project.NameKey project,
        @Assisted Change.Id changeId,
        @Assisted ReviewerAddition reviewerAddition);
  }

  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final Project.NameKey project;
  private final Change.Id changeId;
  private final ReviewerAddition reviewerAddition;

  @Inject
  AddReviewersEmailOp(
      AddReviewerSender.Factory addReviewerSenderFactory,
      @Assisted Project.NameKey project,
      @Assisted Change.Id changeId,
      @Assisted ReviewerAddition reviewerAddition) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.project = project;
    this.changeId = changeId;
    this.reviewerAddition = reviewerAddition;
  }

  @Override
  public void postUpdate(Context ctx) {
    NotifyResolver.Result notify = ctx.getNotify(changeId);
    if (!notify.shouldNotify() || reviewerAddition.isEmpty()) {
      return;
    }

    try {
      AddReviewerSender cm = addReviewerSenderFactory.create(project, changeId);
      cm.setNotify(notify);
      cm.setFrom(ctx.getAccountId());
      // The user knows they added themselves, don't bother emailing them.
      reviewerAddition.addReviewersToSenderExcludingCaller(cm, ctx.getAccountId());
      cm.send();
    } catch (Exception err) {
      logger.atSevere().withCause(err).log(
          "Cannot send email to new reviewers of change %s", changeId);
    }
  }
}
