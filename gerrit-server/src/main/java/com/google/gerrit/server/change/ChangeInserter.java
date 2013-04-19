// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Collections;
import java.util.Set;

public class ChangeInserter {
  private final GitReferenceUpdated gitRefUpdated;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final TrackingFooters trackingFooters;

  @Inject
  public ChangeInserter(final GitReferenceUpdated gitRefUpdated,
      ChangeHooks hooks, ApprovalsUtil approvalsUtil,
      TrackingFooters trackingFooters) {
    this.gitRefUpdated = gitRefUpdated;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.trackingFooters = trackingFooters;
  }

  public void insertChange(ReviewDb db, Change change, PatchSet ps,
      RevCommit commit, LabelTypes labelTypes, PatchSetInfo info,
      Set<Account.Id> reviewers) throws OrmException {
    insertChange(db, change, null, ps, commit, labelTypes, info, reviewers);
  }

  public void insertChange(ReviewDb db, Change change,
      ChangeMessage changeMessage, PatchSet ps, RevCommit commit,
      LabelTypes labelTypes, PatchSetInfo info, Set<Account.Id> reviewers)
      throws OrmException {

    db.changes().beginTransaction(change.getId());
    try {
      ChangeUtil.insertAncestors(db, ps.getId(), commit);
      db.patchSets().insert(Collections.singleton(ps));
      db.changes().insert(Collections.singleton(change));
      ChangeUtil.updateTrackingIds(db, change, trackingFooters, commit.getFooterLines());
      approvalsUtil.addReviewers(db, labelTypes, change, ps, info, reviewers,
          Collections.<Account.Id> emptySet());
      if (changeMessage != null) {
        db.changeMessages().insert(Collections.singleton(changeMessage));
      }
      db.commit();
    } finally {
      db.rollback();
    }

    gitRefUpdated.fire(change.getProject(), ps.getRefName(), ObjectId.zeroId(),
        commit);
    hooks.doPatchsetCreatedHook(change, ps, db);
  }
}
