// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class AddReviewersOp implements BatchUpdateOp {
  public interface Factory {

    /**
     * Create a new op.
     *
     * <p>Users may be added by account or by email addresses, as determined by {@code accountIds}
     * and {@code addresses}. The reviewer state for both accounts and email addresses is determined
     * by {@code state}.
     *
     * @param accountIds account IDs to add.
     * @param addresses email addresses to add.
     * @param state resulting reviewer state.
     * @return batch update operation.
     */
    AddReviewersOp create(
        Set<Account.Id> accountIds, Collection<Address> addresses, ReviewerState state);
  }

  @AutoValue
  public abstract static class Result {
    public abstract ImmutableList<PatchSetApproval> addedReviewers();

    public abstract ImmutableList<Address> addedReviewersByEmail();

    public abstract ImmutableList<Account.Id> addedCCs();

    public abstract ImmutableList<Address> addedCCsByEmail();

    static Builder builder() {
      return new AutoValue_AddReviewersOp_Result.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setAddedReviewers(Iterable<PatchSetApproval> addedReviewers);

      abstract Builder setAddedReviewersByEmail(Iterable<Address> addedReviewersByEmail);

      abstract Builder setAddedCCs(Iterable<Account.Id> addedCCs);

      abstract Builder setAddedCCsByEmail(Iterable<Address> addedCCsByEmail);

      abstract Result build();
    }
  }

  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ReviewerAdded reviewerAdded;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final AddReviewersEmail addReviewersEmail;
  private final Set<Account.Id> accountIds;
  private final Collection<Address> addresses;
  private final ReviewerState state;

  // Unlike addedCCs, addedReviewers is a PatchSetApproval because the AddReviewerResult returned
  // via the REST API is supposed to include vote information.
  private List<PatchSetApproval> addedReviewers = ImmutableList.of();
  private Collection<Address> addedReviewersByEmail = ImmutableList.of();
  private Collection<Account.Id> addedCCs = ImmutableList.of();
  private Collection<Address> addedCCsByEmail = ImmutableList.of();

  private boolean sendEmail = true;
  private Change change;
  private PatchSet patchSet;
  private Result opResult;

  @Inject
  AddReviewersOp(
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ReviewerAdded reviewerAdded,
      AccountCache accountCache,
      ProjectCache projectCache,
      AddReviewersEmail addReviewersEmail,
      @Assisted Set<Account.Id> accountIds,
      @Assisted Collection<Address> addresses,
      @Assisted ReviewerState state) {
    checkArgument(state == REVIEWER || state == CC, "must be %s or %s: %s", REVIEWER, CC, state);
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.reviewerAdded = reviewerAdded;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.addReviewersEmail = addReviewersEmail;

    this.accountIds = accountIds;
    this.addresses = addresses;
    this.state = state;
  }

  // TODO(dborowitz): This mutable setter is ugly, but a) it's less ugly than adding boolean args
  // all the way through the constructor stack, and b) this class is slated to be completely
  // rewritten.
  public void suppressEmail() {
    this.sendEmail = false;
  }

  void setPatchSet(PatchSet patchSet) {
    this.patchSet = requireNonNull(patchSet);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, StorageException, IOException {
    change = ctx.getChange();
    if (!accountIds.isEmpty()) {
      if (state == CC) {
        addedCCs =
            approvalsUtil.addCcs(
                ctx.getNotes(), ctx.getUpdate(change.currentPatchSetId()), accountIds);
      } else {
        addedReviewers =
            approvalsUtil.addReviewers(
                ctx.getNotes(),
                ctx.getUpdate(change.currentPatchSetId()),
                projectCache.checkedGet(change.getProject()).getLabelTypes(change.getDest()),
                change,
                accountIds);
      }
    }

    ImmutableList<Address> addressesToAdd = ImmutableList.of();
    ReviewerStateInternal internalState = ReviewerStateInternal.fromReviewerState(state);

    // TODO(dborowitz): This behavior should live in ApprovalsUtil or something, like addCcs does.
    ImmutableSet<Address> existing = ctx.getNotes().getReviewersByEmail().byState(internalState);
    addressesToAdd =
        addresses.stream().filter(a -> !existing.contains(a)).collect(toImmutableList());

    if (state == CC) {
      addedCCsByEmail = addressesToAdd;
    } else {
      addedReviewersByEmail = addressesToAdd;
    }
    for (Address a : addressesToAdd) {
      ctx.getUpdate(change.currentPatchSetId()).putReviewerByEmail(a, internalState);
    }

    if (addedCCs.isEmpty() && addedReviewers.isEmpty() && addressesToAdd.isEmpty()) {
      return false;
    }

    checkAdded();

    if (patchSet == null) {
      patchSet = requireNonNull(psUtil.current(ctx.getNotes()));
    }
    return true;
  }

  private void checkAdded() {
    // Should only affect either reviewers or CCs, not both. But the logic in updateChange is
    // complex, so programmer error is conceivable.
    boolean addedAnyReviewers = !addedReviewers.isEmpty() || !addedReviewersByEmail.isEmpty();
    boolean addedAnyCCs = !addedCCs.isEmpty() || !addedCCsByEmail.isEmpty();
    checkState(
        !(addedAnyReviewers && addedAnyCCs),
        "should not have added both reviewers and CCs:\n"
            + "Arguments:\n"
            + "  accountIds=%s\n"
            + "  addresses=%s\n"
            + "Results:\n"
            + "  addedReviewers=%s\n"
            + "  addedReviewersByEmail=%s\n"
            + "  addedCCs=%s\n"
            + "  addedCCsByEmail=%s",
        accountIds,
        addresses,
        addedReviewers,
        addedReviewersByEmail,
        addedCCs,
        addedCCsByEmail);
  }

  @Override
  public void postUpdate(Context ctx) throws Exception {
    opResult =
        Result.builder()
            .setAddedReviewers(addedReviewers)
            .setAddedReviewersByEmail(addedReviewersByEmail)
            .setAddedCCs(addedCCs)
            .setAddedCCsByEmail(addedCCsByEmail)
            .build();
    if (sendEmail) {
      addReviewersEmail.emailReviewers(
          ctx.getUser().asIdentifiedUser(),
          change,
          Lists.transform(addedReviewers, PatchSetApproval::getAccountId),
          addedCCs,
          addedReviewersByEmail,
          addedCCsByEmail,
          ctx.getNotify(change.getId()));
    }
    if (!addedReviewers.isEmpty()) {
      List<AccountState> reviewers =
          addedReviewers.stream()
              .map(r -> accountCache.get(r.getAccountId()))
              .flatMap(Streams::stream)
              .collect(toList());
      reviewerAdded.fire(change, patchSet, reviewers, ctx.getAccount(), ctx.getWhen());
    }
  }

  public Result getResult() {
    checkState(opResult != null, "Batch update wasn't executed yet");
    return opResult;
  }
}
