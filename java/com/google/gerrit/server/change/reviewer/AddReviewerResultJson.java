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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ReviewerJson;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
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

  public AddReviewerResult reloadAndFormat(ReviewerAdder.Result adderResult, ChangeNotes oldNotes)
      throws PermissionBackendException {
    return format(
        adderResult, changeDataFactory.create(oldNotes.getProjectName(), oldNotes.getChangeId()));
  }

  public AddReviewerResult format(ReviewerAdder.Result adderResult, ChangeData cd)
      throws PermissionBackendException {
    AddReviewerResult result = new AddReviewerResult(adderResult.input().input());
    result.error = adderResult.error().orElse(null);
    result.confirm = adderResult.confirm() ? true : null;
    if (adderResult.errorOrConfirm()) {
      checkState(adderResult.isEmpty(), "error result should be empty: %s", adderResult);
      return result;
    }

    AccountLoader accountLoader = accountLoaderFactory.create(true);
    ReviewerStateInternal inputState = adderResult.input().state();
    List<ReviewerInfo> reviewerInfos = new ArrayList<>();
    addReviewerInfos(reviewerJson, cd, reviewerInfos, adderResult.reviewers());
    accountLoader.fill(reviewerInfos); // Fill non-by-email only.
    addReviewerInfos(reviewerInfos, adderResult.reviewersByEmail());

    // The generics don't work out if this is ImmutableList.Builder<AccountInfo>.
    List<ReviewerInfo> ccInfos = new ArrayList<>();
    addReviewerInfos(reviewerJson, cd, ccInfos, adderResult.ccs());
    accountLoader.fill(ccInfos); // Fill non-by-email only.
    addReviewerInfos(ccInfos, adderResult.ccsByEmail());

    // Prefer empty list to null for the field that corresponds to the requested state in the input.
    if (!reviewerInfos.isEmpty() || inputState == REVIEWER) {
      result.reviewers = ImmutableList.copyOf(reviewerInfos);
    }
    if (!ccInfos.isEmpty() || inputState == CC) {
      result.ccs = ImmutableList.copyOf(ccInfos);
    }

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
      throws PermissionBackendException {
    for (Account.Id id : ids) {
      builder.add(reviewerJson.format(new ReviewerInfo(id.get()), id, cd));
    }
  }
}
