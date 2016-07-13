// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.DeleteReviewer.Input;
import com.google.gerrit.server.extensions.events.ReviewerDeleted;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.DeleteReviewerSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class DeleteReviewer implements RestModifyView<ReviewerResource, Input> {
  private static final Logger log = LoggerFactory
      .getLogger(DeleteReviewer.class);

  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ReviewerDeleted reviewerDeleted;
  private final Provider<IdentifiedUser> user;
  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;

  @Inject
  DeleteReviewer(Provider<ReviewDb> dbProvider,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      IdentifiedUser.GenericFactory userFactory,
      ReviewerDeleted reviewerDeleted,
      Provider<IdentifiedUser> user,
      DeleteReviewerSender.Factory deleteReviewerSenderFactory) {
    this.dbProvider = dbProvider;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.userFactory = userFactory;
    this.reviewerDeleted = reviewerDeleted;
    this.user = user;
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
  }

  @Override
  public Response<?> apply(ReviewerResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = batchUpdateFactory.create(dbProvider.get(),
        rsrc.getChangeResource().getProject(),
        rsrc.getChangeResource().getUser(), TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getReviewerUser().getAccount());
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
    }

    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final Account reviewer;
    ChangeMessage changeMessage;
    Change currChange;
    PatchSet currPs;
    List<PatchSetApproval> del = new ArrayList<>();
    Map<String, Short> newApprovals = new HashMap<>();
    Map<String, Short> oldApprovals = new HashMap<>();

    Op(Account reviewerAccount) {
      this.reviewer = reviewerAccount;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws AuthException, ResourceNotFoundException, OrmException {
      Account.Id reviewerId = reviewer.getId();
      if (!approvalsUtil.getReviewers(ctx.getDb(), ctx.getNotes()).all()
          .contains(reviewerId)) {
        throw new ResourceNotFoundException();
      }
      currChange = ctx.getChange();
      currPs = psUtil.current(dbProvider.get(), ctx.getNotes());

      Set<String> placeholders = new HashSet<>();
      LabelTypes labelTypes = ctx.getControl().getLabelTypes();
      // removing a reviewer will remove all her votes
      for (LabelType lt : labelTypes.getLabelTypes()) {
        newApprovals.put(lt.getName(), (short) 0);
        placeholders.add(lt.getName());
      }

      StringBuilder msg = new StringBuilder();
      for (PatchSetApproval a : approvals(ctx, reviewerId)) {
        if (ctx.getControl().canRemoveReviewer(a)) {
          del.add(a);
          placeholders.remove(a.getLabel());
          if (a.getPatchSetId().equals(currPs.getId()) && a.getValue() != 0) {
            oldApprovals.put(a.getLabel(), a.getValue());
            if (msg.length() == 0) {
              msg.append("Removed reviewer ").append(reviewer.getFullName())
                  .append(" with the following votes:\n\n");
            }
            msg.append("* ").append(a.getLabel())
                .append(formatLabelValue(a.getValue())).append(" by ")
                .append(userFactory.create(a.getAccountId()).getNameEmail())
                .append("\n");
          }
        } else {
          throw new AuthException("delete reviewer not permitted");
        }
      }

      for (String label : placeholders) {
        PatchSetApproval a = new PatchSetApproval(
            new PatchSetApproval.Key(currPs.getId(), reviewerId, new LabelId(label)),
            (short) 0,
            TimeUtil.nowTs());
        del.add(a);
      }

      ctx.getDb().patchSetApprovals().delete(del);
      ChangeUpdate update = ctx.getUpdate(currPs.getId());
      update.removeReviewer(reviewerId);

      if (msg.length() > 0) {
        changeMessage = new ChangeMessage(
            new ChangeMessage.Key(currChange.getId(),
                ChangeUtil.messageUUID(ctx.getDb())),
            ctx.getAccountId(), ctx.getWhen(), currPs.getId());
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(ctx.getDb(), update, changeMessage);
      }

      return true;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (changeMessage == null) {
        return;
      }

      emailReviewers(ctx.getProject(), currChange, del, changeMessage);
      reviewerDeleted.fire(currChange, currPs, reviewer,
          ctx.getAccount(),
          changeMessage.getMessage(),
          newApprovals, oldApprovals,
          ctx.getWhen());
    }

    private Iterable<PatchSetApproval> approvals(ChangeContext ctx,
        final Account.Id accountId) throws OrmException {
      return Iterables.filter(
          approvalsUtil.byChange(ctx.getDb(), ctx.getNotes()).values(),
          new Predicate<PatchSetApproval>() {
            @Override
            public boolean apply(PatchSetApproval input) {
              return accountId.equals(input.getAccountId());
            }
          });
    }

    private String formatLabelValue(short value) {
      if (value > 0) {
        return "+" + value;
      }
      return Short.toString(value);
    }
  }

  private void emailReviewers(Project.NameKey projectName, Change change,
      List<PatchSetApproval> dels, ChangeMessage changeMessage) {

    // The user knows they removed themselves, don't bother emailing them.
    List<Account.Id> toMail = Lists.newArrayListWithCapacity(dels.size());
    Account.Id userId = user.get().getAccountId();
    for (PatchSetApproval psa : dels) {
      if (!psa.getAccountId().equals(userId)) {
        toMail.add(psa.getAccountId());
      }
    }
    if (!toMail.isEmpty()) {
      try {
        DeleteReviewerSender cm =
            deleteReviewerSenderFactory.create(projectName, change.getId());
        cm.setFrom(userId);
        cm.addReviewers(toMail);
        cm.setChangeMessage(changeMessage.getMessage(),
            changeMessage.getWrittenOn());
        cm.send();
      } catch (Exception err) {
        log.error("Cannot email update for change " + change.getId(), err);
      }
    }
  }
}
