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
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.server.mail.send.ChangeEmailNew.ChangeEmailParams;

/** Base class for Attention Set email senders */
public final class AttentionSetChangeEmail implements ChangeEmailParams {
  public enum AttentionSetChange {
    USER_ADDED,
    USER_REMOVED
  }

  private OutgoingEmailNew email;
  private ChangeEmailNew changeEmail;

  private Account.Id attentionSetUser;
  private String reason;
  private AttentionSetChange attentionSetChange;

  public void setAttentionSetUser(Account.Id attentionSetUser) {
    this.attentionSetUser = attentionSetUser;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public void setAttentionSetChange(AttentionSetChange attentionSetChange) {
    this.attentionSetChange = attentionSetChange;
  }

  @Override
  public void init(OutgoingEmailNew email, ChangeEmailNew changeEmail) throws EmailException {
    this.email = email;
    this.changeEmail = changeEmail;
  }

  @Override
  public void populateEmailContent() throws EmailException {
    email.addSoyParam("attentionSetUser", email.getNameFor(attentionSetUser));
    email.addSoyParam("reason", reason);

    changeEmail.ccAllApprovals();
    changeEmail.bccStarredBy();
    changeEmail.ccExistingReviewers();

    switch (attentionSetChange) {
      case USER_ADDED:
        email.appendText(email.textTemplate("AddToAttentionSet"));
        if (email.useHtml()) {
          email.appendHtml(email.soyHtmlTemplate("AddToAttentionSetHtml"));
        }
        break;
      case USER_REMOVED:
        email.appendText(email.textTemplate("RemoveFromAttentionSet"));
        if (email.useHtml()) {
          email.appendHtml(email.soyHtmlTemplate("RemoveFromAttentionSetHtml"));
        }
        break;
    }
  }
}
