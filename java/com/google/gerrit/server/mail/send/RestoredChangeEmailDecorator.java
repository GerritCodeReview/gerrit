// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;

/** Send notice about a change being restored by its owner. */
public class RestoredChangeEmailDecorator implements ChangeEmailDecorator {
  protected OutgoingEmail email;
  protected ChangeEmail changeEmail;

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) {
    this.email = email;
    this.changeEmail = changeEmail;
    changeEmail.markAsReply();
  }

  @Override
  public void populateEmailContent() {
    changeEmail.addAuthors(RecipientType.TO);

    changeEmail.ccAllApprovals();
    changeEmail.bccStarredBy();
    changeEmail.includeWatchers(NotifyType.ALL_COMMENTS);

    email.appendText(email.textTemplate("Restored"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("RestoredHtml"));
    }
  }
}
