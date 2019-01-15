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

import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SetAssigneeSender extends ChangeEmail {
  public interface Factory {
    SetAssigneeSender create(Project.NameKey project, Change.Id id, Account.Id assignee);
  }

  private final Account.Id assignee;

  @Inject
  public SetAssigneeSender(
      EmailArguments ea,
      @Assisted Project.NameKey project,
      @Assisted Change.Id id,
      @Assisted Account.Id assignee)
      throws OrmException {
    super(ea, "setassignee", newChangeData(ea, project, id));
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

  public String getAssigneeName() {
    return getNameFor(assignee);
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("assigneeName", getAssigneeName());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
