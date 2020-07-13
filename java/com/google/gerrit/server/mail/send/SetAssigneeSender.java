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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * Sender that informs a user by email that they were set as assignee on a change.
 *
 * <p>In contrast to other change emails this email is not sent to the change authors (owner, patch
 * set uploader, author). This is why this class extends {@link ChangeEmail} directly, instead of
 * extending {@link ReplyToChangeSender}.
 */
public class SetAssigneeSender extends ChangeEmail {
  public interface Factory {
    SetAssigneeSender create(Project.NameKey project, Change.Id changeId, Account.Id assignee);
  }

  private final Account.Id assignee;

  @Inject
  public SetAssigneeSender(
      EmailArguments args,
      @Assisted Project.NameKey project,
      @Assisted Change.Id changeId,
      @Assisted Account.Id assignee) {
    super(args, "setassignee", newChangeData(args, project, changeId));
    this.assignee = assignee;
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    add(RecipientType.TO, assignee);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("SetAssignee"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("SetAssigneeHtml"));
    }
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("assigneeName", getNameFor(assignee));
  }
}
