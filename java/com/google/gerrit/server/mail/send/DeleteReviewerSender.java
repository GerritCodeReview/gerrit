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
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Let users know that a reviewer and possibly her review have been removed. */
public class DeleteReviewerSender extends ReplyToChangeSender {
  private final Set<Account.Id> reviewers = new HashSet<>();
  private final Set<Address> reviewersByEmail = new HashSet<>();

  public interface Factory extends ReplyToChangeSender.Factory<DeleteReviewerSender> {
    @Override
    DeleteReviewerSender create(Project.NameKey project, Change.Id change);
  }

  @Inject
  public DeleteReviewerSender(
      EmailArguments ea, @Assisted Project.NameKey project, @Assisted Change.Id id) {
    super(ea, "deleteReviewer", newChangeData(ea, project, id));
  }

  public void addReviewers(Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addReviewersByEmail(Collection<Address> cc) {
    reviewersByEmail.addAll(cc);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    ccExistingReviewers();
    includeWatchers(NotifyType.ALL_COMMENTS);
    add(RecipientType.TO, reviewers);
    addByEmail(RecipientType.TO, reviewersByEmail);
    removeUsersThatIgnoredTheChange();
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("DeleteReviewer"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("DeleteReviewerHtml"));
    }
  }

  public List<String> getReviewerNames() {
    if (reviewers.isEmpty() && reviewersByEmail.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    for (Address a : reviewersByEmail) {
      names.add(a.toString());
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
