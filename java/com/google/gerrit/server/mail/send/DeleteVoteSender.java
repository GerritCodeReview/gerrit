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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Send notice about a vote that was removed from a change. */
public class DeleteVoteSender extends ReplyToChangeSender {
  public interface Factory extends ReplyToChangeSender.Factory<DeleteVoteSender> {
    @Override
    DeleteVoteSender create(Project.NameKey project, Change.Id changeId);
  }

  @Inject
  protected DeleteVoteSender(
      EmailArguments args, @Assisted Project.NameKey project, @Assisted Change.Id changeId) {
    super(args, "deleteVote", newChangeData(args, project, changeId));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
    removeUsersThatIgnoredTheChange();
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("DeleteVote"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("DeleteVoteHtml"));
    }
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
