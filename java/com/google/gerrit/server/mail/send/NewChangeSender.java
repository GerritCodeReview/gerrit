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

import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.mail.Address;
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

  @Override
  protected void init() throws EmailException {
    super.init();

    String threadId = getChangeMessageThreadId();
    setHeader("Message-ID", threadId);
    setHeader("References", threadId);

    switch (notify.handling()) {
      case NONE:
      case OWNER:
        break;
      case ALL:
      default:
        add(RecipientType.CC, extraCC);
        extraCCByEmail.stream().forEach(cc -> add(RecipientType.CC, cc));
        // $FALL-THROUGH$
      case OWNER_REVIEWERS:
        add(RecipientType.TO, reviewers, true);
        addByEmail(RecipientType.TO, reviewersByEmail, true);
        break;
    }

    rcptToAuthors(RecipientType.CC);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("NewChange"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("NewChangeHtml"));
    }
  }

  public List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContext.put("ownerName", getNameFor(change.getOwner()));
    soyContextEmailData.put("reviewerNames", getReviewerNames());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
