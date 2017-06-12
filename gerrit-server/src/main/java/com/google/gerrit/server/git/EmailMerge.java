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

package com.google.gerrit.server.git;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailMerge implements Runnable, RequestContext {
  private static final Logger log = LoggerFactory.getLogger(EmailMerge.class);

  public interface Factory {
    EmailMerge create(
        Project.NameKey project,
        Change.Id changeId,
        Account.Id submitter,
        NotifyHandling notifyHandling);
  }

  private final ExecutorService sendEmailsExecutor;
  private final MergedSender.Factory mergedSenderFactory;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ThreadLocalRequestContext requestContext;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  private final Project.NameKey project;
  private final Change.Id changeId;
  private final Account.Id submitter;
  private final NotifyHandling notifyHandling;
  private ReviewDb db;

  @Inject
  EmailMerge(
      @SendEmailExecutor ExecutorService executor,
      MergedSender.Factory mergedSenderFactory,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext requestContext,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @Assisted Project.NameKey project,
      @Assisted Change.Id changeId,
      @Assisted @Nullable Account.Id submitter,
      @Assisted NotifyHandling notifyHandling) {
    this.sendEmailsExecutor = executor;
    this.mergedSenderFactory = mergedSenderFactory;
    this.schemaFactory = schemaFactory;
    this.requestContext = requestContext;
    this.identifiedUserFactory = identifiedUserFactory;
    this.project = project;
    this.changeId = changeId;
    this.submitter = submitter;
    this.notifyHandling = notifyHandling;
  }

  public void sendAsync() {
    sendEmailsExecutor.submit(this);
  }

  @Override
  public void run() {
    RequestContext old = requestContext.setContext(this);
    try {
      MergedSender cm = mergedSenderFactory.create(project, changeId);
      if (submitter != null) {
        cm.setFrom(submitter);
      }
      cm.setNotify(notifyHandling);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot email merged notification for " + changeId, e);
    } finally {
      requestContext.setContext(old);
      if (db != null) {
        db.close();
        db = null;
      }
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

  @Override
  public Provider<ReviewDb> getReviewDbProvider() {
    return new Provider<ReviewDb>() {
      @Override
      public ReviewDb get() {
        if (db == null) {
          try {
            db = schemaFactory.open();
          } catch (OrmException e) {
            throw new ProvisionException("Cannot open ReviewDb", e);
          }
        }
        return db;
      }
    };
  }
}
