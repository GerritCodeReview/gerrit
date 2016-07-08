// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.events.ChangeEvent;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.events.ChangeRevertedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.VoteDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MailSenderApiListener implements
    ChangeAbandonedListener,
    ChangeRestoredListener,
    ChangeRevertedListener,
    ReviewerAddedListener,
    VoteDeletedListener {
  private static final Logger log =
      LoggerFactory.getLogger(MailSenderApiListener.class);

  private final AbandonedSender.Factory abandonedSenderFactory;
  private final RestoredSender.Factory restoredSenderFactory;
  private final RevertedSender.Factory revertedSenderFactory;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final DeleteVoteSender.Factory deleteVoteSenderFactory;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), ChangeAbandonedListener.class)
        .to(MailSenderApiListener.class);
      DynamicSet.bind(binder(), ChangeRestoredListener.class)
        .to(MailSenderApiListener.class);
      DynamicSet.bind(binder(), ChangeRevertedListener.class)
        .to(MailSenderApiListener.class);
      DynamicSet.bind(binder(), ReviewerAddedListener.class)
        .to(MailSenderApiListener.class);
      DynamicSet.bind(binder(), VoteDeletedListener.class)
        .to(MailSenderApiListener.class);
    }
  }

  @Inject
  MailSenderApiListener(AbandonedSender.Factory abandonedSenderFactory,
      RestoredSender.Factory restoredSenderFactory,
      RevertedSender.Factory revertedSenderFactory,
      AddReviewerSender.Factory addReviewerSenderFactory,
      DeleteVoteSender.Factory deleteVoteSenderFactory) {
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.restoredSenderFactory = restoredSenderFactory;
    this.revertedSenderFactory = revertedSenderFactory;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.deleteVoteSenderFactory = deleteVoteSenderFactory;
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event event) {
    Change.Id changeId = new Change.Id(event.getChange()._number);
    AbandonedSender sender = abandonedSenderFactory.create(
            new Project.NameKey(event.getChange().project),
            changeId);
    setFrom(sender, event);
    sender.setChangeMessage(event.getReason(), event.getWhen());
    try {
      sender.send();
    } catch (EmailException e) {
      log.error(
          "Cannot send abandoned email notification for change " + changeId, e);
    }
  }

  @Override
  public void onChangeRestored(ChangeRestoredListener.Event event) {
    Change.Id changeId = new Change.Id(event.getChange()._number);
    RestoredSender sender = restoredSenderFactory.create(
        new Project.NameKey(event.getChange().project),
        changeId);
    setFrom(sender, event);
    sender.setChangeMessage(event.getReason(), event.getWhen());
    try {
      sender.send();
    } catch (EmailException e) {
      log.error(
          "Cannot send restored email notification for change " + changeId, e);
    }
  }

  @Override
  public void onChangeReverted(ChangeRevertedListener.Event event) {
    Change.Id changeId = new Change.Id(event.getChange()._number);
    RevertedSender sender = revertedSenderFactory.create(
        new Project.NameKey(event.getChange().project),
        changeId);
    setFrom(sender, event);
    sender.setChangeMessage(event.getRevertChange().subject, event.getWhen());
    try {
      sender.send();
    } catch (EmailException e) {
      log.error(
          "Cannot send reverted email notification for change " + changeId, e);
    }
  }

  @Override
  public void onReviewersAdded(ReviewerAddedListener.Event event) {
    if (event.getReviewers().isEmpty()) {
      return;
    }
    Change.Id changeId = new Change.Id(event.getChange()._number);
    AddReviewerSender sender = addReviewerSenderFactory.create(
        new Project.NameKey(event.getChange().project),
        changeId);
    setFrom(sender, event);
    List<Account.Id> reviewers = Lists.transform(event.getReviewers(),
        new Function<AccountInfo, Account.Id>() {
          @Override
          public Account.Id apply(AccountInfo input) {
            return new Account.Id(input._accountId.intValue());
          }
        });
    sender.addReviewers(reviewers);
    try {
      sender.send();
    } catch (EmailException e) {
      log.error("Cannot send reviewers added email notification for change "
          + changeId, e);
    }
  }

  @Override
  public void onVoteDeleted(VoteDeletedListener.Event event) {
    if (event.getNotify().compareTo(NotifyHandling.NONE) > 0) {
      Change.Id changeId = new Change.Id(event.getChange()._number);
      DeleteVoteSender sender = deleteVoteSenderFactory.create(
          new Project.NameKey(event.getChange().project),
          changeId);
      setFrom(sender, event);
      sender.setChangeMessage(event.getMessage(), event.getWhen());
      sender.setNotify(event.getNotify());
      try {
        sender.send();
      } catch (EmailException e) {
        log.error("Cannot send vote deleted email notification for change "
            + changeId, e);
      }
    }
  }

  private void setFrom(ChangeEmail sender, ChangeEvent event) {
    AccountInfo who = event.getWho();
    if (who != null) {
      Account.Id accountId = new Account.Id(who._accountId.intValue());
      sender.setFrom(accountId);
    }
  }
}
