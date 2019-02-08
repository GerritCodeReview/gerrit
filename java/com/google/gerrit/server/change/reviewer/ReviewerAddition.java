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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.reviewer.ReviewerAdder.Result;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.mail.send.ReviewerSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

public class ReviewerAddition implements BatchUpdateOp {
  interface Factory {
    ReviewerAddition create(ImmutableList<Result.Builder> resultBuilders);
  }

  private final AccountCache accountCache;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final ReviewerAdded reviewerAdded;

  private final boolean hasError;

  private ImmutableList<Result.Builder> resultBuilders;
  private ImmutableList<Result> results;

  private Change change;
  private PatchSet patchSet;

  @Inject
  ReviewerAddition(
      AccountCache accountCache,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      ReviewerAdded reviewerAdded,
      @Assisted ImmutableList<Result.Builder> resultBuilders) {
    this.accountCache = accountCache;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.reviewerAdded = reviewerAdded;

    this.hasError = resultBuilders.stream().anyMatch(Result.Builder::errorOrConfirm);
    if (this.hasError) {
      this.results = build(resultBuilders);
    } else {
      this.resultBuilders = resultBuilders;
    }
  }

  public boolean isEmpty() {
    return getResults().stream().allMatch(Result::isEmpty);
  }

  public boolean hasError() {
    return hasError;
  }

  public Optional<String> getError() {
    if (!hasError) {
      return Optional.empty();
    }
    return getResults().stream()
        .map(ReviewerAdder.Result::error)
        .flatMap(Streams::stream)
        .collect(collectingAndThen(joining("\n"), Optional::of));
  }

  public ImmutableList<Result> getResults() {
    checkState(results != null, "BatchUpdate not yet executed");
    return results;
  }

  void addReviewersToSenderExcludingCaller(ReviewerSender sender, Account.Id caller) {
    addReviewersToSender(sender, id -> !id.equals(caller));
  }

  public void addReviewersToSender(ReviewerSender sender) {
    addReviewersToSender(sender, id -> true);
  }

  private void addReviewersToSender(ReviewerSender sender, Predicate<Id> predicate) {
    for (Result r : getResults()) {
      if (r.errorOrConfirm()) {
        continue;
      }
      sender.addReviewers(r.reviewers().stream().filter(predicate).collect(toImmutableList()));
      sender.addReviewersByEmail(r.reviewersByEmail());
      sender.addExtraCC(r.ccs().stream().filter(predicate).collect(toImmutableList()));
      sender.addExtraCCByEmail(r.ccsByEmail());
    }
  }

  public ReviewerAddition setPatchSet(PatchSet patchSet) {
    this.patchSet = requireNonNull(patchSet);
    return this;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws IOException {
    if (hasError) {
      return false;
    }
    checkState(resultBuilders != null, "BatchUpdate already executed");
    boolean dirty = false;
    for (Result.Builder resultBuilder : resultBuilders) {
      dirty |= updateOne(ctx, resultBuilder);
    }
    results = build(resultBuilders);
    resultBuilders = null;
    return dirty;
  }

  private boolean updateOne(ChangeContext ctx, Result.Builder resultBuilder) throws IOException {
    if (resultBuilder.errorOrConfirm() || resultBuilder.isEmpty()) {
      return false;
    }

    change = ctx.getChange();
    if (patchSet == null) {
      patchSet = psUtil.current(ctx.getNotes());
      checkState(patchSet != null, "no patch set for change; caller must set manually");
    }
    maybeFilterChangeOwner(resultBuilder, change);
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    resultBuilder.reviewers(
        approvalsUtil
            .addReviewers(
                ctx.getNotes(),
                update,
                projectCache.checkedGet(change.getProject()).getLabelTypes(change.getDest()),
                change,
                resultBuilder.reviewers())
            .stream()
            .map(PatchSetApproval::getAccountId)
            .collect(toImmutableList()));
    resultBuilder.ccs(approvalsUtil.addCcs(ctx.getNotes(), update, resultBuilder.ccs()));

    resultBuilder.reviewersByEmail(
        filterExisting(
            resultBuilder.reviewersByEmail(),
            ctx.getNotes().getReviewersByEmail().byState(REVIEWER)));
    resultBuilder.reviewersByEmail().forEach(a -> update.putReviewerByEmail(a, REVIEWER));
    resultBuilder.ccsByEmail(
        filterExisting(
            resultBuilder.ccsByEmail(), ctx.getNotes().getReviewersByEmail().byState(CC)));
    resultBuilder.ccsByEmail().forEach(a -> update.putReviewerByEmail(a, CC));

    return !resultBuilder.isEmpty();
  }

  @Override
  public void postUpdate(Context ctx) {
    if (hasError) {
      return;
    }
    reviewerAdded.fire(
        change,
        patchSet,
        results.stream()
            .flatMap(r -> r.reviewers().stream())
            .distinct()
            .map(accountCache::get)
            .flatMap(Streams::stream)
            .collect(toImmutableList()),
        ctx.getAccount(),
        ctx.getWhen());
  }

  private static ImmutableList<Result> build(ImmutableList<Result.Builder> resultBuilders) {
    return resultBuilders.stream().map(Result.Builder::build).collect(toImmutableList());
  }

  private void maybeFilterChangeOwner(Result.Builder resultBuilder, Change change) {
    if (!resultBuilder.input().options().ignoreIfChangeOwner()) {
      return;
    }
    resultBuilder.reviewers(filterOwner(resultBuilder.reviewers(), change));
    resultBuilder.ccs(filterOwner(resultBuilder.ccs(), change));
  }

  private static ImmutableList<Id> filterOwner(ImmutableList<Id> accounts, Change change) {
    return accounts.stream().filter(id -> !id.equals(change.getOwner())).collect(toImmutableList());
  }

  private ImmutableList<Address> filterExisting(
      ImmutableList<Address> reviewersByEmail, ImmutableSet<Address> existing) {
    return reviewersByEmail.stream().filter(a -> !existing.contains(a)).collect(toImmutableList());
  }
}
