// Copyright (C) 2023 The Android Open Source Project
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
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockUserChangeEmailDecoratorImpl implements BlockUserChangeEmailDecorator {
  private OutgoingEmail email;
  private ChangeEmail changeEmail;

  private final Set<Account.Id> blockedUsers = new HashSet<>();

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) throws EmailException {
    this.email = email;
    this.changeEmail = changeEmail;
    changeEmail.markAsReply();
  }

  @Override
  public void populateEmailContent() throws EmailException {
    email.addSoyEmailDataParam("reviewerNames", getReviewerNames());

    changeEmail.addAuthors(RecipientType.TO);
    changeEmail.ccAllApprovals();
    changeEmail.bccStarredBy();
    changeEmail.ccExistingReviewers();
    changeEmail.includeWatchers(NotifyType.ALL_COMMENTS);
    blockedUsers.stream().forEach(r -> email.addByAccountId(RecipientType.TO, r));

    email.appendText(email.textTemplate("BlockUser"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("BlockUserHtml"));
    }
  }

  @Nullable
  protected List<String> getReviewerNames() {
    if (blockedUsers.isEmpty()) {
      return null;
    }
    return blockedUsers.stream().map(id -> email.getNameFor(id)).collect(Collectors.toList());
  }

  @Override
  public void addBlockedUser(Account.Id user) {
    blockedUsers.add(user);
  }
}
