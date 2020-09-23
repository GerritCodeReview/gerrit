// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.AsyncPostUpdateExecutor;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class EmailMerge implements Runnable, RequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    EmailMerge create(
        Project.NameKey project,
        Change change,
        Account.Id submitter,
        NotifyResolver.Result notify,
        RepoView repoView);
  }

  private final ExecutorService asyncPostExecutorService;
  private final MergedSender.Factory mergedSenderFactory;
  private final ThreadLocalRequestContext requestContext;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final MessageIdGenerator messageIdGenerator;

  private final Project.NameKey project;
  private final Change change;
  private final Account.Id submitter;
  private final NotifyResolver.Result notify;
  private final RepoView repoView;

  @Inject
  EmailMerge(
      @AsyncPostUpdateExecutor ExecutorService executor,
      MergedSender.Factory mergedSenderFactory,
      ThreadLocalRequestContext requestContext,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      MessageIdGenerator messageIdGenerator,
      @Assisted Project.NameKey project,
      @Assisted Change change,
      @Assisted @Nullable Account.Id submitter,
      @Assisted NotifyResolver.Result notify,
      @Assisted RepoView repoView) {
    this.asyncPostExecutorService = executor;
    this.mergedSenderFactory = mergedSenderFactory;
    this.requestContext = requestContext;
    this.identifiedUserFactory = identifiedUserFactory;
    this.messageIdGenerator = messageIdGenerator;
    this.project = project;
    this.change = change;
    this.submitter = submitter;
    this.notify = notify;
    this.repoView = repoView;
  }

  void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = asyncPostExecutorService.submit(this);
  }

  @Override
  public void run() {
    RequestContext old = requestContext.setContext(this);
    try {
      MergedSender emailSender = mergedSenderFactory.create(project, change.getId());
      if (submitter != null) {
        emailSender.setFrom(submitter);
      }
      emailSender.setNotify(notify);
      emailSender.setMessageId(
          messageIdGenerator.fromChangeUpdate(repoView, change.currentPatchSetId()));
      emailSender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email merged notification for %s", change.getId());
    } finally {
      requestContext.setContext(old);
    }
  }

  @Override
  public String toString() {
    return "send-email merged";
  }

  @Override
  public CurrentUser getUser() {
    if (submitter != null) {
      return identifiedUserFactory.create(submitter).getRealUser();
    }
    throw new OutOfScopeException("No user on email thread");
  }
}
