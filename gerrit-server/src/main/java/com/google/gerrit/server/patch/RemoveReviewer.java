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


package com.google.gerrit.server.patch;

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class RemoveReviewer implements Callable<ReviewerResult> {
  private static final Logger log =
      LoggerFactory.getLogger(RemoveReviewer.class);

  public interface Factory {
    RemoveReviewer create(Change.Id changeId, Set<Account.Id> reviewerId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final AccountCache accountCache;
  private final Change.Id changeId;
  private final Set<Account.Id> ids;

  @Inject
  RemoveReviewer(ReviewDb db, ChangeControl.Factory changeControlFactory,
      AccountCache accountCache, @Assisted Change.Id changeId,
      @Assisted Set<Account.Id> ids) {
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.accountCache = accountCache;
    this.changeId = changeId;
    this.ids = ids;
  }

  @Override
  public ReviewerResult call() throws Exception {
    ReviewerResult result = new ReviewerResult();
    ChangeControl ctl = changeControlFactory.validateFor(changeId);
    Set<Account.Id> rejected = new HashSet<Account.Id>();

    List<PatchSetApproval> current = db.patchSetApprovals().byChange(changeId).toList();
    for (PatchSetApproval psa : current) {
      Account.Id who = psa.getAccountId();
      if (ids.contains(who) && !ctl.canRemoveReviewer(psa) && rejected.add(who)) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.REMOVE_NOT_PERMITTED,
            formatUser(who)));
      }
    }

    List<PatchSetApproval> toDelete = new ArrayList<PatchSetApproval>();
    for (PatchSetApproval psa : current) {
      Account.Id who = psa.getAccountId();
      if (ids.contains(who) && !rejected.contains(who)) {
        toDelete.add(psa);
      }
    }

    try {
      db.patchSetApprovals().delete(toDelete);
    } catch (OrmException err) {
      log.warn("Cannot remove reviewers from change "+changeId, err);
      Set<Account.Id> failed = new HashSet<Account.Id>();
      for (PatchSetApproval psa : toDelete) {
        failed.add(psa.getAccountId());
      }
      for (Account.Id who : failed) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.COULD_NOT_REMOVE,
            formatUser(who)));
      }
    }

    return result;
  }

  private String formatUser(Account.Id who) {
    AccountState state = accountCache.get(who);
    if (state != null) {
      return formatUser(state.getAccount(), who);
    } else {
      return who.toString();
    }
  }

  static String formatUser(Account a, Object fallback) {
    if (a.getFullName() != null && !a.getFullName().isEmpty()) {
      return a.getFullName();
    }

    if (a.getPreferredEmail() != null && !a.getPreferredEmail().isEmpty()) {
      return a.getPreferredEmail();
    }

    if (a.getUserName() != null && a.getUserName().isEmpty()) {
      return a.getUserName();
    }

    return fallback.toString();
  }
}
