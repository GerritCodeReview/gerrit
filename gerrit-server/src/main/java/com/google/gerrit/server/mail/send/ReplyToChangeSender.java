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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.mail.RecipientType;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

/** Alert a user to a reply to a change, usually commentary made during review. */
public abstract class ReplyToChangeSender extends ChangeEmail {
  public interface Factory<T extends ReplyToChangeSender> {
    T create(Project.NameKey project, Change.Id id);
  }

  protected ReplyToChangeSender(EmailArguments ea, String mc, ChangeData cd) throws OrmException {
    super(ea, mc, cd);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    final String threadId = getChangeMessageThreadId();
    setHeader("In-Reply-To", threadId);
    setHeader("References", threadId);

    rcptToAuthors(RecipientType.TO);
  }
}
