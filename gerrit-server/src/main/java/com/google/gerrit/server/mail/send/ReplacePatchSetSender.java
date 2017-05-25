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
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Send notice of new patch sets for reviewers. */
public class ReplacePatchSetSender extends ReplyToChangeSender {
  public interface Factory {
    ReplacePatchSetSender create(Project.NameKey project, Change.Id id);
  }

  private final Set<Account.Id> reviewers = new HashSet<>();
  private final Set<Account.Id> extraCC = new HashSet<>();

  @Inject
  public ReplacePatchSetSender(
      EmailArguments ea, @Assisted Project.NameKey project, @Assisted Change.Id id)
      throws OrmException {
    super(ea, "newpatchset", newChangeData(ea, project, id));
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    if (fromId != null) {
      // Don't call yourself a reviewer of your own patch set.
      //
      reviewers.remove(fromId);
    }
    if (notify == NotifyHandling.ALL || notify == NotifyHandling.OWNER_REVIEWERS) {
      add(RecipientType.TO, reviewers);
      add(RecipientType.CC, extraCC);
    }
    rcptToAuthors(RecipientType.CC);
    bccStarredBy();
    includeWatchers(
        NotifyType.NEW_PATCHSETS,
        !patchSet.isDraft() && !change.isWorkInProgress() && !change.isPrivate());
    removeUsersThatIgnoredTheChange();
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("ReplacePatchSet"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("ReplacePatchSetHtml"));
    }
  }

  public List<String> getReviewerNames() {
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      if (id.equals(fromId)) {
        continue;
      }
      names.add(getNameFor(id));
    }
    if (names.isEmpty()) {
      return null;
    }
    return names;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("reviewerNames", getReviewerNames());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
