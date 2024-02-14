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
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class AddReviewersOp extends ReviewerOp {
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
     * @param forGroup whether this reviewer addition adds accounts for a group
     * @return batch update operation.
     */
    AddReviewersOp create(
        Set<Account.Id> accountIds,
        Collection<Address> addresses,
        ReviewerState state,
        boolean forGroup);
  }

  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ReviewerAdded reviewerAdded;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final ModifyReviewersEmail modifyReviewersEmail;
  private final Set<Account.Id> accountIds;
  private final Collection<Address> addresses;
  private final ReviewerState state;
  private final boolean forGroup;

  // Unlike addedCCs, addedReviewers is a PatchSetApproval because the ReviewerResult returned
  // via the REST API is supposed to include vote information.
  private List<PatchSetApproval> addedReviewers = ImmutableList.of();
  private ImmutableList<Address> addedReviewersByEmail = ImmutableList.of();
  private Collection<Account.Id> addedCCs = ImmutableList.of();
  private ImmutableList<Address> addedCCsByEmail = ImmutableList.of();

  private Change change;

  @Inject
  AddReviewersOp(
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ReviewerAdded reviewerAdded,
      AccountCache accountCache,
      ProjectCache projectCache,
      ModifyReviewersEmail modifyReviewersEmail,
      @Assisted Set<Account.Id> accountIds,
      @Assisted Collection<Address> addresses,
      @Assisted ReviewerState state,
      @Assisted boolean forGroup) {
    checkArgument(state == REVIEWER || state == CC, "must be %s or %s: %s", REVIEWER, CC, state);
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.reviewerAdded = reviewerAdded;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.modifyReviewersEmail = modifyReviewersEmail;

    this.accountIds = accountIds;
    this.addresses = addresses;
    this.state = state;
    this.forGroup = forGroup;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException, IOException {
    change = ctx.getChange();
    if (!accountIds.isEmpty()) {
      if (state == CC) {
        addedCCs =
            approvalsUtil.addCcs(
                ctx.getNotes(), ctx.getUpdate(change.currentPatchSetId()), accountIds, forGroup);
      } else {
        addedReviewers =
            approvalsUtil.addReviewers(
                ctx.getNotes(),
                ctx.getUpdate(change.currentPatchSetId()),
                projectCache
                    .get(change.getProject())
                    .orElseThrow(illegalState(change.getProject()))
                    .getLabelTypes(change.getDest()),
                change,
                accountIds);
      }
    }

    ReviewerStateInternal internalState = ReviewerStateInternal.fromReviewerState(state);

    // TODO(dborowitz): This behavior should live in ApprovalsUtil or something, like addCcs does.
    ImmutableSet<Address> existing = ctx.getNotes().getReviewersByEmail().byState(internalState);
    ImmutableList<Address> addressesToAdd =
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
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    opResult =
        Result.builder()
            .setAddedReviewers(addedReviewers)
            .setAddedReviewersByEmail(addedReviewersByEmail)
            .setAddedCCs(addedCCs)
            .setAddedCCsByEmail(addedCCsByEmail)
            .build();
    if (sendEmail) {
      modifyReviewersEmail.emailReviewersAsync(
          ctx.getUser().asIdentifiedUser(),
          change,
          Lists.transform(addedReviewers, PatchSetApproval::accountId),
          addedCCs,
          ImmutableSet.of(),
          addedReviewersByEmail,
          addedCCsByEmail,
          ImmutableSet.of(),
          ctx.getNotify(change.getId()));
    }
    if (!addedReviewers.isEmpty()) {
      List<AccountState> reviewers =
          addedReviewers.stream()
              .map(r -> accountCache.get(r.accountId()))
              .flatMap(Streams::stream)
              .collect(toList());
      eventSender =
          () ->
              reviewerAdded.fire(
                  ctx.getChangeData(change), patchSet, reviewers, ctx.getAccount(), ctx.getWhen());
      if (sendEvent) {
        sendEvent();
      }
    }
  }
}
