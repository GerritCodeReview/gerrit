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
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Send notice about a change being abandoned by its owner. */
public class AbandonedSender extends ReplyToChangeSender {
  public interface Factory extends ReplyToChangeSender.Factory<AbandonedSender> {
    @Override
    AbandonedSender create(Project.NameKey project, Change.Id change);
  }

  @Inject
  public AbandonedSender(
      EmailArguments ea, @Assisted Project.NameKey project, @Assisted Change.Id id)
      throws OrmException {
    super(ea, "abandon", ChangeEmail.newChangeData(ea, project, id));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ABANDONED_CHANGES);
    includeWatchers(NotifyType.ALL_COMMENTS);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("Abandoned"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("AbandonedHtml"));
    }
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
