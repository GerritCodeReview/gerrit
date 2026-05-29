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

/** Let users know that a reviewer and possibly her review have been removed. */
public class DeleteReviewerChangeEmailDecoratorImpl implements DeleteReviewerChangeEmailDecorator {
  protected OutgoingEmail email;
  protected ChangeEmail changeEmail;

  protected final Set<Account.Id> reviewers = new HashSet<>();
  protected final Set<Address> reviewersByEmail = new HashSet<>();

  @Override
  public void addReviewers(Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  @Override
  public void addReviewersByEmail(Collection<Address> cc) {
    reviewersByEmail.addAll(cc);
  }

  @Nullable
  protected List<String> getReviewerNames() {
    if (reviewers.isEmpty() && reviewersByEmail.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      names.add(email.getNameFor(id));
    }
    for (Address a : reviewersByEmail) {
      names.add(a.toString());
    }
    return names;
  }

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) {
    this.email = email;
    this.changeEmail = changeEmail;
    changeEmail.markAsReply();
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("reviewerNames", getReviewerNames());

    changeEmail.addAuthors(RecipientType.TO);
    changeEmail.ccAllApprovals();
    changeEmail.bccStarredBy();
    changeEmail.ccExistingReviewers();
    changeEmail.includeWatchers(NotifyType.ALL_COMMENTS);
    reviewers.stream().forEach(r -> email.addByAccountId(RecipientType.TO, r));
    reviewersByEmail.stream().forEach(address -> email.addByEmail(RecipientType.TO, address));

    email.appendText(email.textTemplate("DeleteReviewer"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("DeleteReviewerHtml"));
    }
  }
}
