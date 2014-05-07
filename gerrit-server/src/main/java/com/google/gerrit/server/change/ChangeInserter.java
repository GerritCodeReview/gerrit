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

import static com.google.gerrit.reviewdb.client.Change.INITIAL_PATCH_SET_ID;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ChangeInserter {
  public static interface Factory {
    ChangeInserter create(RefControl ctl, Change c, RevCommit rc);
  }

  private static final Logger log =
      LoggerFactory.getLogger(ChangeInserter.class);

  private final Provider<ReviewDb> dbProvider;
  private final ChangeUpdate.Factory updateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final MergeabilityChecker mergeabilityChecker;
  private final CreateChangeSender.Factory createChangeSenderFactory;

  private final RefControl refControl;
  private final Change change;
  private final PatchSet patchSet;
  private final RevCommit commit;
  private final PatchSetInfo patchSetInfo;

  private ChangeMessage changeMessage;
  private Set<Account.Id> reviewers;
  private Set<Account.Id> extraCC;
  private Map<String, Short> approvals;
  private boolean runHooks;
  private boolean sendMail;

  @Inject
  ChangeInserter(Provider<ReviewDb> dbProvider,
      ChangeUpdate.Factory updateFactory,
      Provider<ApprovalsUtil> approvals,
      PatchSetInfoFactory patchSetInfoFactory,
      GitReferenceUpdated gitRefUpdated,
      ChangeHooks hooks,
      ApprovalsUtil approvalsUtil,
      MergeabilityChecker mergeabilityChecker,
      CreateChangeSender.Factory createChangeSenderFactory,
      @Assisted RefControl refControl,
      @Assisted Change change,
      @Assisted RevCommit commit) {
    this.dbProvider = dbProvider;
    this.updateFactory = updateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.mergeabilityChecker = mergeabilityChecker;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.refControl = refControl;
    this.change = change;
    this.commit = commit;
    this.reviewers = Collections.emptySet();
    this.extraCC = Collections.emptySet();
    this.approvals = Collections.emptyMap();
    this.runHooks = true;
    this.sendMail = true;

    patchSet =
        new PatchSet(new PatchSet.Id(change.getId(), INITIAL_PATCH_SET_ID));
    patchSet.setCreatedOn(change.getCreatedOn());
    patchSet.setUploader(change.getOwner());
    patchSet.setRevision(new RevId(commit.name()));
    patchSetInfo = patchSetInfoFactory.get(commit, patchSet.getId());
    change.setCurrentPatchSet(patchSetInfo);
    ChangeUtil.computeSortKey(change);
  }

  public Change getChange() {
    return change;
  }

  public ChangeInserter setMessage(ChangeMessage changeMessage) {
    this.changeMessage = changeMessage;
    return this;
  }

  public ChangeInserter setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
    return this;
  }

  public ChangeInserter setExtraCC(Set<Account.Id> extraCC) {
    this.extraCC = extraCC;
    return this;
  }

  public ChangeInserter setDraft(boolean draft) {
    change.setStatus(draft ? Change.Status.DRAFT : Change.Status.NEW);
    patchSet.setDraft(draft);
    return this;
  }

  public ChangeInserter setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
    return this;
  }

  public ChangeInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
    return this;
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public ChangeInserter setApprovals(Map<String, Short> approvals) {
    this.approvals = approvals;
    return this;
  }

  public PatchSetInfo getPatchSetInfo() {
    return patchSetInfo;
  }

  public Change insert() throws OrmException, IOException {
    ReviewDb db = dbProvider.get();
    ChangeControl ctl = refControl.getProjectControl().controlFor(change);
    ChangeUpdate update = updateFactory.create(
        ctl,
        change.getCreatedOn());
    db.changes().beginTransaction(change.getId());
    try {
      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));
      db.changes().insert(Collections.singleton(change));
      LabelTypes labelTypes = refControl.getProjectControl().getLabelTypes();
      approvalsUtil.addReviewers(db, update, labelTypes, change,
          patchSet, patchSetInfo, reviewers, Collections.<Account.Id> emptySet());
      approvalsUtil.addApprovals(db, update, labelTypes, patchSet, patchSetInfo,
          change, ctl, approvals);
      if (messageIsForChange()) {
        insertMessage(db);
      }
      db.commit();
    } finally {
      db.rollback();
    }

    update.commit();
    CheckedFuture<?, IOException> f = mergeabilityChecker.newCheck()
        .addChange(change)
        .reindex()
        .runAsync();
    if (!messageIsForChange()) {
      insertMessage(db);
    }
    gitRefUpdated.fire(change.getProject(), patchSet.getRefName(),
        ObjectId.zeroId(), commit);

    if (runHooks) {
      hooks.doPatchsetCreatedHook(change, patchSet, db);
    }

    if (sendMail) {
      try {
        CreateChangeSender cm =
            createChangeSenderFactory.create(change);
        cm.setFrom(change.getOwner());
        cm.setPatchSet(patchSet, patchSetInfo);
        cm.addReviewers(reviewers);
        cm.addExtraCC(extraCC);
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for new change " + change.getId(), err);
      }
    }
    f.checkedGet();
    return change;
  }

  private boolean messageIsForChange() {
    return changeMessage != null
        && changeMessage.getKey().getParentKey().equals(change.getKey());
  }

  private void insertMessage(ReviewDb db) throws OrmException {
    if (changeMessage != null) {
      db.changeMessages().insert(Collections.singleton(changeMessage));
    }
  }
}
