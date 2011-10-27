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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.Change;

/** Alert a user to a reply to a change, usually commentary made during review. */
public abstract class ReplyToChangeSender extends ChangeEmail {
  public static interface Factory<T extends ReplyToChangeSender> {
    public T create(Change change);
  }

  protected ReplyToChangeSender(EmailArguments ea, String anonymousCowardName,
      Change c, String mc) {
    super(ea, anonymousCowardName, c, mc);
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
