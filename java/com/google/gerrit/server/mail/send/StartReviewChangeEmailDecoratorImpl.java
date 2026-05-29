// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.extensions.api.changes.RecipientType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends an email alerting a user to a new change for them to review. */
public class StartReviewChangeEmailDecoratorImpl implements StartReviewChangeEmailDecorator {
  protected OutgoingEmail email;
  protected ChangeEmail changeEmail;

  protected final Set<Account.Id> reviewers = new HashSet<>();
  protected final Set<Address> reviewersByEmail = new HashSet<>();
  protected final Set<Account.Id> extraCC = new HashSet<>();
  protected final Set<Address> extraCCByEmail = new HashSet<>();
  protected final Set<Account.Id> removedReviewers = new HashSet<>();
  protected final Set<Address> removedByEmailReviewers = new HashSet<>();
  protected boolean isCreateChange = false;

  @Override
  public void addReviewers(Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  @Override
  public void addReviewersByEmail(Collection<Address> cc) {
    reviewersByEmail.addAll(cc);
  }

  @Override
  public void addExtraCC(Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  public void addExtraCCByEmail(Collection<Address> cc) {
    extraCCByEmail.addAll(cc);
  }

  @Override
  public void addRemovedReviewers(Collection<Account.Id> removed) {
    removedReviewers.addAll(removed);
  }

  @Override
  public void addRemovedByEmailReviewers(Collection<Address> removed) {
    removedByEmailReviewers.addAll(removed);
  }

  @Override
  public void markAsCreateChange() {
    isCreateChange = true;
  }

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) {
    this.email = email;
    this.changeEmail = changeEmail;
  }

  @Nullable
  protected List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      names.add(email.getNameFor(id));
    }
    return names;
  }

  @Nullable
  protected List<String> getRemovedReviewerNames() {
    if (removedReviewers.isEmpty() && removedByEmailReviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : removedReviewers) {
      names.add(email.getNameFor(id));
    }
    for (Address address : removedByEmailReviewers) {
      names.add(address.toString());
    }
    return names;
  }

  @Override
  public void populateEmailContent() {
    email.addSoyParam("ownerName", email.getNameFor(changeEmail.getChange().getOwner()));
    email.addSoyEmailDataParam("reviewerNames", getReviewerNames());
    email.addSoyEmailDataParam("removedReviewerNames", getRemovedReviewerNames());

    switch (email.getNotify().handling()) {
      case NONE:
      case OWNER:
        break;
      case ALL:
      default:
        extraCC.stream().forEach(cc -> email.addByAccountId(RecipientType.CC, cc));
        extraCCByEmail.stream().forEach(cc -> email.addByEmail(RecipientType.CC, cc));
      // $FALL-THROUGH$
      case OWNER_REVIEWERS:
        reviewers.stream().forEach(r -> email.addByAccountId(RecipientType.TO, r, true));
        reviewersByEmail.stream().forEach(r -> email.addByEmail(RecipientType.TO, r, true));
        removedReviewers.stream().forEach(r -> email.addByAccountId(RecipientType.TO, r, true));
        removedByEmailReviewers.stream().forEach(r -> email.addByEmail(RecipientType.TO, r, true));
        break;
    }

    if (isCreateChange) {
      changeEmail.addAuthors(RecipientType.CC);
      changeEmail.includeWatchers(
          NotifyType.NEW_CHANGES,
          !changeEmail.getChange().isWorkInProgress() && !changeEmail.getChange().isPrivate());
      changeEmail.includeWatchers(
          NotifyType.NEW_PATCHSETS,
          !changeEmail.getChange().isWorkInProgress() && !changeEmail.getChange().isPrivate());
    }

    email.appendText(email.textTemplate("NewChange"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("NewChangeHtml"));
    }
  }
}
