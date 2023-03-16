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
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends an email alerting a user to a new change for them to review. */
public abstract class NewChangeSender extends ChangeEmail {
  private final Set<Account.Id> reviewers = new HashSet<>();
  private final Set<Address> reviewersByEmail = new HashSet<>();
  private final Set<Account.Id> extraCC = new HashSet<>();
  private final Set<Address> extraCCByEmail = new HashSet<>();
  private final Set<Account.Id> removedReviewers = new HashSet<>();
  private final Set<Address> removedByEmailReviewers = new HashSet<>();

  protected NewChangeSender(EmailArguments args, ChangeData changeData) {
    super(args, "newchange", changeData);
  }

  public void addReviewers(Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addReviewersByEmail(Collection<Address> cc) {
    reviewersByEmail.addAll(cc);
  }

  public void addExtraCC(Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  public void addExtraCCByEmail(Collection<Address> cc) {
    extraCCByEmail.addAll(cc);
  }

  public void addRemovedReviewers(Collection<Account.Id> removed) {
    removedReviewers.addAll(removed);
  }

  public void addRemovedByEmailReviewers(Collection<Address> removed) {
    removedByEmailReviewers.addAll(removed);
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    String threadId = getChangeMessageThreadId();
    setHeader("References", threadId);

    switch (notify.handling()) {
      case NONE:
      case OWNER:
        break;
      case ALL:
      default:
        extraCC.stream().forEach(cc -> addByAccountId(RecipientType.CC, cc));
        extraCCByEmail.stream().forEach(cc -> addByEmail(RecipientType.CC, cc));
        // $FALL-THROUGH$
      case OWNER_REVIEWERS:
        reviewers.stream().forEach(r -> addByAccountId(RecipientType.TO, r, true));
        reviewersByEmail.stream().forEach(r -> addByEmail(RecipientType.TO, r, true));
        removedReviewers.stream().forEach(r -> addByAccountId(RecipientType.TO, r, true));
        removedByEmailReviewers.stream().forEach(r -> addByEmail(RecipientType.TO, r, true));
        break;
    }

    addAuthors(RecipientType.CC);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("NewChange"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("NewChangeHtml"));
    }
  }

  @Nullable
  private List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  @Nullable
  private List<String> getRemovedReviewerNames() {
    if (removedReviewers.isEmpty() && removedByEmailReviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : removedReviewers) {
      names.add(getNameFor(id));
    }
    for (Address address : removedByEmailReviewers) {
      names.add(address.toString());
    }
    return names;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContext.put("ownerName", getNameFor(change.getOwner()));
    soyContextEmailData.put("reviewerNames", getReviewerNames());
    soyContextEmailData.put("removedReviewerNames", getRemovedReviewerNames());
  }
}
