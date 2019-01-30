// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.change.reviewer;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ReviewerJson;
import com.google.gerrit.server.change.reviewer.ReviewerAdder.Result;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AddReviewerResultJson {
  private final AccountLoader.Factory accountLoaderFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ReviewerJson reviewerJson;

  @Inject
  AddReviewerResultJson(
      AccountLoader.Factory accountLoaderFactory,
      ChangeData.Factory changeDataFactory,
      ReviewerJson reviewerJson) {
    this.accountLoaderFactory = accountLoaderFactory;
    this.changeDataFactory = changeDataFactory;
    this.reviewerJson = reviewerJson;
  }

  public AddReviewerResult reloadAndFormat(Result adderResult, ChangeNotes oldNotes)
      throws PermissionBackendException, OrmException {
    return format(
        adderResult, changeDataFactory.create(oldNotes.getProjectName(), oldNotes.getChangeId()));
  }

  public AddReviewerResult format(Result adderResult, ChangeData cd)
      throws PermissionBackendException, OrmException {
    AddReviewerResult result = new AddReviewerResult(adderResult.input().input());
    result.error = adderResult.error().orElse(null);
    result.confirm = adderResult.confirm() ? true : null;

    List<ReviewerInfo> reviewerInfos = new ArrayList<>();
    addReviewerInfos(reviewerJson, cd, reviewerInfos, adderResult.reviewers());
    addReviewerInfos(reviewerInfos, adderResult.reviewersByEmail());
    result.reviewers = ImmutableList.copyOf(reviewerInfos);

    // The generics don't work out if this is ImmutableList.Builder<AccountInfo>.
    List<ReviewerInfo> ccInfos = new ArrayList<>();
    addReviewerInfos(reviewerJson, cd, ccInfos, adderResult.ccs());
    addReviewerInfos(ccInfos, adderResult.ccsByEmail());
    result.ccs = ImmutableList.copyOf(ccInfos);

    AccountLoader accountLoader = accountLoaderFactory.create(true);
    accountLoader.fill(result.reviewers);
    accountLoader.fill(result.ccs);
    return result;
  }

  private static void addReviewerInfos(
      List<ReviewerInfo> builder, ImmutableList<Address> addresses) {
    addresses.forEach(a -> builder.add(ReviewerInfo.byEmail(a.getName(), a.getEmail())));
  }

  private static void addReviewerInfos(
      ReviewerJson reviewerJson,
      ChangeData cd,
      List<ReviewerInfo> builder,
      ImmutableList<Account.Id> ids)
      throws OrmException, PermissionBackendException {
    for (Account.Id id : ids) {
      builder.add(reviewerJson.format(new ReviewerInfo(id.get()), id, cd));
    }
  }
}
