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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailSenderApiListener implements
    ChangeAbandonedListener {
  private static final Logger log =
      LoggerFactory.getLogger(MailSenderApiListener.class);
  private final AbandonedSender.Factory abandonedSenderFactory;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), ChangeAbandonedListener.class)
        .to(MailSenderApiListener.class);
    }
  }

  @Inject
  MailSenderApiListener(AbandonedSender.Factory abandonedSenderFactory) {
    this.abandonedSenderFactory = abandonedSenderFactory;
  }

  @Override
  public void onChangeAbandoned(ChangeAbandonedListener.Event event) {
    Change.Id changeId = new Change.Id(event.getChange()._number);
    AbandonedSender cm = abandonedSenderFactory.create(
            new Project.NameKey(event.getChange().project),
            changeId);
    AccountInfo account = event.getAbandoner();
    if (account != null) {
      cm.setFrom(new Account.Id(account._accountId.intValue()));
    }
    //cm.setChangeMessage(event.getReason()); //TODO
    try {
      cm.send();
    } catch (EmailException e) {
      log.error(
          "Cannot send abandoned email notificationfor change " + changeId, e);
    }
  }
}
