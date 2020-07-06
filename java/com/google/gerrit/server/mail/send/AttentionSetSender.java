// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;

/** Base class for Attention Set email senders */
public abstract class AttentionSetSender extends ReplyToChangeSender {
  private Account.Id attentionSetUser;
  private String reason;

  public AttentionSetSender(EmailArguments args, Project.NameKey project, Change.Id changeId) {
    super(args, "addToAttentionSet", ChangeEmail.newChangeData(args, project, changeId));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    ccExistingReviewers();
    removeUsersThatIgnoredTheChange();
  }

  public void setAttentionSetUser(Account.Id attentionSetUser) {
    this.attentionSetUser = attentionSetUser;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContext.put("attentionSetUser", getNameFor(attentionSetUser));
    soyContext.put("reason", reason);
  }
}
