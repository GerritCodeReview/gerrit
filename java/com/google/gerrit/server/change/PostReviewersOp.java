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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostReviewersOp implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(PostReviewersOp.class);

  public interface Factory {
    PostReviewersOp create(
        ChangeResource rsrc,
        Set<Account.Id> reviewers,
        Collection<Address> reviewersByEmail,
        ReviewerState state,
        @Nullable NotifyHandling notify,
        ListMultimap<RecipientType, Account.Id> accountsToNotify);
  }

  @AutoValue
  public abstract static class Result {
    public abstract ImmutableList<PatchSetApproval> addedReviewers();

    public abstract ImmutableList<Account.Id> addedCCs();

    static Builder builder() {
      return new AutoValue_PostReviewersOp_Result.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setAddedReviewers(ImmutableList<PatchSetApproval> addedReviewers);

      abstract Builder setAddedCCs(ImmutableList<Account.Id> addedCCs);

      abstract Result build();
    }
  }

  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ReviewerAdded reviewerAdded;
  private final AccountCache accountCache;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final NotesMigration migration;
  private final Provider<IdentifiedUser> user;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeResource rsrc;
  private final Set<Account.Id> reviewers;
  private final Collection<Address> reviewersByEmail;
  private final ReviewerState state;
  private final NotifyHandling notify;
  private final ListMultimap<RecipientType, Account.Id> accountsToNotify;

  private List<PatchSetApproval> addedReviewers = new ArrayList<>();
  private Collection<Account.Id> addedCCs = new ArrayList<>();
  private Collection<Address> addedCCsByEmail = new ArrayList<>();
  private PatchSet patchSet;
  private Result opResult;

  @Inject
  PostReviewersOp(
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ReviewerAdded reviewerAdded,
      AccountCache accountCache,
      AddReviewerSender.Factory addReviewerSenderFactory,
      NotesMigration migration,
      Provider<IdentifiedUser> user,
      Provider<ReviewDb> dbProvider,
      @Assisted ChangeResource rsrc,
      @Assisted Set<Account.Id> reviewers,
      @Assisted Collection<Address> reviewersByEmail,
      @Assisted ReviewerState state,
      @Assisted @Nullable NotifyHandling notify,
      @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.reviewerAdded = reviewerAdded;
    this.accountCache = accountCache;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.migration = migration;
    this.user = user;
    this.dbProvider = dbProvider;

    this.rsrc = rsrc;
    this.reviewers = reviewers;
    this.reviewersByEmail = reviewersByEmail;
    this.state = state;
    this.notify = notify;
    this.accountsToNotify = accountsToNotify;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, OrmException, IOException {
    if (!reviewers.isEmpty()) {
      if (migration.readChanges() && state == CC) {
        addedCCs =
            approvalsUtil.addCcs(
                ctx.getNotes(), ctx.getUpdate(ctx.getChange().currentPatchSetId()), reviewers);
        if (addedCCs.isEmpty()) {
          return false;
        }
      } else {
        addedReviewers =
            approvalsUtil.addReviewers(
                ctx.getDb(),
                ctx.getNotes(),
                ctx.getUpdate(ctx.getChange().currentPatchSetId()),
                rsrc.getControl().getLabelTypes(),
                rsrc.getChange(),
                reviewers);
        if (addedReviewers.isEmpty()) {
          return false;
        }
      }
    }

    for (Address a : reviewersByEmail) {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .putReviewerByEmail(a, ReviewerStateInternal.fromReviewerState(state));
    }

    patchSet = psUtil.current(dbProvider.get(), rsrc.getNotes());
    return true;
  }

  @Override
  public void postUpdate(Context ctx) throws Exception {
    opResult =
        Result.builder()
            .setAddedReviewers(ImmutableList.copyOf(addedReviewers))
            .setAddedCCs(ImmutableList.copyOf(addedCCs))
            .build();
    emailReviewers(
        rsrc.getChange(),
        Lists.transform(addedReviewers, r -> r.getAccountId()),
        addedCCs == null ? ImmutableList.of() : addedCCs,
        reviewersByEmail,
        addedCCsByEmail,
        notify,
        accountsToNotify);
    if (!addedReviewers.isEmpty()) {
      List<Account> reviewers =
          addedReviewers
              .stream()
              .map(r -> accountCache.get(r.getAccountId()).getAccount())
              .collect(toList());
      reviewerAdded.fire(rsrc.getChange(), patchSet, reviewers, ctx.getAccount(), ctx.getWhen());
    }
  }

  public void emailReviewers(
      Change change,
      Collection<Account.Id> added,
      Collection<Account.Id> copied,
      Collection<Address> addedByEmail,
      Collection<Address> copiedByEmail,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    if (added.isEmpty() && copied.isEmpty() && addedByEmail.isEmpty() && copiedByEmail.isEmpty()) {
      return;
    }

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    List<Account.Id> toMail = Lists.newArrayListWithCapacity(added.size());
    Account.Id userId = user.get().getAccountId();
    for (Account.Id id : added) {
      if (!id.equals(userId)) {
        toMail.add(id);
      }
    }
    List<Account.Id> toCopy = Lists.newArrayListWithCapacity(copied.size());
    for (Account.Id id : copied) {
      if (!id.equals(userId)) {
        toCopy.add(id);
      }
    }
    if (toMail.isEmpty() && toCopy.isEmpty() && addedByEmail.isEmpty() && copiedByEmail.isEmpty()) {
      return;
    }

    try {
      AddReviewerSender cm = addReviewerSenderFactory.create(change.getProject(), change.getId());
      // Default to silent operation on WIP changes.
      NotifyHandling defaultNotifyHandling =
          change.isWorkInProgress() ? NotifyHandling.NONE : NotifyHandling.ALL;
      cm.setNotify(MoreObjects.firstNonNull(notify, defaultNotifyHandling));
      cm.setAccountsToNotify(accountsToNotify);
      cm.setFrom(userId);
      cm.addReviewers(toMail);
      cm.addReviewersByEmail(addedByEmail);
      cm.addExtraCC(toCopy);
      cm.addExtraCCByEmail(copiedByEmail);
      cm.send();
    } catch (Exception err) {
      log.error("Cannot send email to new reviewers of change " + change.getId(), err);
    }
  }

  public Result getResult() {
    checkState(opResult != null, "Batch update wasn't executed yet");
    return opResult;
  }
}
