// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;

@Singleton
public class AddReviewersEmail {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AddReviewerSender.Factory addReviewerSenderFactory;

  @Inject
  AddReviewersEmail(AddReviewerSender.Factory addReviewerSenderFactory) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
  }

  public void emailReviewers(
      IdentifiedUser user,
      Change change,
      Collection<Account.Id> added,
      Collection<Account.Id> copied,
      Collection<Address> addedByEmail,
      Collection<Address> copiedByEmail,
      NotifyResolver.Result notify) {
    // The user knows they added themselves, don't bother emailing them.
    Account.Id userId = user.getAccountId();
    ImmutableList<Account.Id> toMail =
        added.stream().filter(id -> !id.equals(userId)).collect(toImmutableList());
    ImmutableList<Account.Id> toCopy =
        copied.stream().filter(id -> !id.equals(userId)).collect(toImmutableList());
    if (toMail.isEmpty() && toCopy.isEmpty() && addedByEmail.isEmpty() && copiedByEmail.isEmpty()) {
      return;
    }

    try {
      AddReviewerSender cm = addReviewerSenderFactory.create(change.getProject(), change.getId());
      cm.setNotify(notify);
      cm.setFrom(userId);
      cm.addReviewers(toMail);
      cm.addReviewersByEmail(addedByEmail);
      cm.addExtraCC(toCopy);
      cm.addExtraCCByEmail(copiedByEmail);
      cm.send();
    } catch (Exception err) {
      logger.atSevere().withCause(err).log(
          "Cannot send email to new reviewers of change %s", change.getId());
    }
  }
}
