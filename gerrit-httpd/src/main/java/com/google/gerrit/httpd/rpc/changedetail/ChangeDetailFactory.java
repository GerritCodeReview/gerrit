// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetAncestor;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a {@link ChangeDetail} from a {@link Change}. */
public class ChangeDetailFactory extends Handler<ChangeDetail> {
  public interface Factory {
    ChangeDetailFactory create(Change.Id id);
  }

  private final ApprovalTypes approvalTypes;
  private final ChangeControl.Factory changeControlFactory;
  private final FunctionState.Factory functionState;
  private final PatchSetDetailFactory.Factory patchSetDetail;
  private final AccountInfoCacheFactory aic;
  private final AnonymousUser anonymousUser;
  private final ReviewDb db;

  private final Change.Id changeId;

  private ChangeDetail detail;
  private ChangeControl control;
  private Map<PatchSet.Id, PatchSet> patchsetsById;

  @Inject
  ChangeDetailFactory(final ApprovalTypes approvalTypes,
      final FunctionState.Factory functionState,
      final PatchSetDetailFactory.Factory patchSetDetail, final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AnonymousUser anonymousUser,
      @Assisted final Change.Id id) {
    this.approvalTypes = approvalTypes;
    this.functionState = functionState;
    this.patchSetDetail = patchSetDetail;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.anonymousUser = anonymousUser;
    this.aic = accountInfoCacheFactory.create();

    this.changeId = id;
  }

  @Override
  public ChangeDetail call() throws OrmException, NoSuchEntityException,
      PatchSetInfoNotAvailableException, NoSuchChangeException {
    control = changeControlFactory.validateFor(changeId);
    final Change change = control.getChange();
    final PatchSet patch = db.patchSets().get(change.currentPatchSetId());
    if (patch == null) {
      throw new NoSuchEntityException();
    }

    aic.want(change.getOwner());

    detail = new ChangeDetail();
    detail.setChange(change);
    detail.setAllowsAnonymous(control.forUser(anonymousUser).isVisible(db));

    detail.setCanAbandon(change.getStatus() != Change.Status.DRAFT && change.getStatus().isOpen() && control.canAbandon());
    detail.setCanRestore(change.getStatus() == Change.Status.ABANDONED && control.canRestore());
    detail.setCanDeleteDraft(change.getStatus() == Change.Status.DRAFT && control.isOwner());
    detail.setStarred(control.getCurrentUser().getStarredChanges().contains(
        changeId));

    detail.setCanRevert(change.getStatus() == Change.Status.MERGED && control.canAddPatchSet());

    if (detail.getChange().getStatus().isOpen()) {
      List<SubmitRecord> submitRecords = control.canSubmit(db, patch.getId());
      for (SubmitRecord rec : submitRecords) {
        if (rec.labels != null) {
          for (SubmitRecord.Label lbl : rec.labels) {
            aic.want(lbl.appliedBy);
          }
        }
        if (rec.status == SubmitRecord.Status.OK && control.getRefControl().canSubmit()) {
          detail.setCanSubmit(true);
        }
      }
      detail.setSubmitRecords(submitRecords);
    }

    patchsetsById = new HashMap<PatchSet.Id, PatchSet>();
    loadPatchSets();
    loadMessages();
    if (change.currentPatchSetId() != null) {
      loadCurrentPatchSet();
    }
    load();
    detail.setAccounts(aic.create());
    return detail;
  }

  private void loadPatchSets() throws OrmException {
    ResultSet<PatchSet> source = db.patchSets().byChange(changeId);
    List<PatchSet> patches = new ArrayList<PatchSet>();
    CurrentUser user = control.getCurrentUser();
    for (PatchSet ps : source) {
      if (control.isPatchVisible(ps, db)) {
        patches.add(ps);
      }
      patchsetsById.put(ps.getId(), ps);
    }
    detail.setPatchSets(patches);
  }

  private void loadMessages() throws OrmException {
    ResultSet<ChangeMessage> source = db.changeMessages().byChange(changeId);
    List<ChangeMessage> msgList = new ArrayList<ChangeMessage>();
    for (ChangeMessage msg : source) {
      PatchSet.Id id = msg.getPatchSetId();
      if (id != null) {
        PatchSet ps = patchsetsById.get(msg.getPatchSetId());
        if (control.isPatchVisible(ps, db)) {
          msgList.add(msg);
        }
      } else {
        // Not guaranteed to have a non-null patchset id, so just display it.
        msgList.add(msg);
      }
    }
    detail.setMessages(msgList);
    for (final ChangeMessage m : detail.getMessages()) {
      aic.want(m.getAuthor());
    }
  }

  private void load() throws OrmException {
    final PatchSet.Id psId = detail.getChange().currentPatchSetId();
    final List<PatchSetApproval> allApprovals =
        db.patchSetApprovals().byChange(changeId).toList();

    if (detail.getChange().getStatus().isOpen()) {
      final FunctionState fs = functionState.create(control, psId, allApprovals);

      for (final ApprovalType at : approvalTypes.getApprovalTypes()) {
        CategoryFunction.forCategory(at.getCategory()).run(at, fs);
      }
    }

    final boolean canRemoveReviewers = detail.getChange().getStatus().isOpen() //
        && control.getCurrentUser() instanceof IdentifiedUser;
    final HashMap<Account.Id, ApprovalDetail> ad =
        new HashMap<Account.Id, ApprovalDetail>();
    for (PatchSetApproval ca : allApprovals) {
      ApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ApprovalDetail(ca.getAccountId());
        d.setCanRemove(canRemoveReviewers);
        ad.put(d.getAccount(), d);
      }
      if (d.canRemove()) {
        d.setCanRemove(control.canRemoveReviewer(ca));
      }
      if (ca.getPatchSetId().equals(psId)) {
        d.add(ca);
      }
    }

    final Account.Id owner = detail.getChange().getOwner();
    if (ad.containsKey(owner)) {
      // Ensure the owner always sorts to the top of the table
      //
      ad.get(owner).sortFirst();
    }

    aic.want(ad.keySet());
    detail.setApprovals(ad.values());
  }

  private void loadCurrentPatchSet() throws OrmException,
      NoSuchEntityException, PatchSetInfoNotAvailableException,
      NoSuchChangeException {
    final PatchSet currentPatch = findCurrentOrLatestPatchSet();
    final PatchSet.Id psId = currentPatch.getId();
    final PatchSetDetailFactory loader = patchSetDetail.create(null, psId, null);
    loader.patchSet = currentPatch;
    loader.control = control;
    detail.setCurrentPatchSetDetail(loader.call());
    detail.setCurrentPatchSetId(psId);

    final HashSet<Change.Id> changesToGet = new HashSet<Change.Id>();
    final HashMap<Change.Id,PatchSet.Id> ancestorPatchIds =
        new HashMap<Change.Id,PatchSet.Id>();
    final List<Change.Id> ancestorOrder = new ArrayList<Change.Id>();
    for (PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(psId)) {
      for (PatchSet p : db.patchSets().byRevision(a.getAncestorRevision())) {
        final Change.Id ck = p.getId().getParentKey();
        if (changesToGet.add(ck)) {
          ancestorPatchIds.put(ck, p.getId());
          ancestorOrder.add(ck);
        }
      }
    }

    final RevId cprev = loader.patchSet.getRevision();
    final Set<Change.Id> descendants = new HashSet<Change.Id>();
    if (cprev != null) {
      for (PatchSetAncestor a : db.patchSetAncestors().descendantsOf(cprev)) {
        final Change.Id ck = a.getPatchSet().getParentKey();
        if (descendants.add(ck)) {
          changesToGet.add(a.getPatchSet().getParentKey());
        }
      }
    }
    final Map<Change.Id, Change> m =
        db.changes().toMap(db.changes().get(changesToGet));

    final ArrayList<ChangeInfo> dependsOn = new ArrayList<ChangeInfo>();
    for (final Change.Id a : ancestorOrder) {
      final Change ac = m.get(a);
      if (ac != null) {
        dependsOn.add(newChangeInfo(ac, ancestorPatchIds));
      }
    }

    final ArrayList<ChangeInfo> neededBy = new ArrayList<ChangeInfo>();
    for (final Change.Id a : descendants) {
      final Change ac = m.get(a);
      if (ac != null) {
        neededBy.add(newChangeInfo(ac, null));
      }
    }

    Collections.sort(neededBy, new Comparator<ChangeInfo>() {
      public int compare(final ChangeInfo o1, final ChangeInfo o2) {
        return o1.getId().get() - o2.getId().get();
      }
    });

    detail.setDependsOn(dependsOn);
    detail.setNeededBy(neededBy);
  }

  private PatchSet findCurrentOrLatestPatchSet() {
    PatchSet currentPatch = detail.getCurrentPatchSet();
    // If the current patch set is a draft and user can't see it, set the
    // current patch set to whatever the latest one is
    if (currentPatch == null) {
      List<PatchSet> patchSets = detail.getPatchSets();
      if (!detail.getPatchSets().isEmpty()) {
        currentPatch = patchSets.get(patchSets.size() - 1);
      } else {
        // Shouldn't happen, change shouldn't be visible if all the patchsets
        // are drafts
      }
    }
    return currentPatch;
  }

  private ChangeInfo newChangeInfo(final Change ac,
      Map<Change.Id,PatchSet.Id> ancestorPatchIds) {
    aic.want(ac.getOwner());
    ChangeInfo ci;
    if (ancestorPatchIds == null) {
      ci = new ChangeInfo(ac);
    } else {
      ci = new ChangeInfo(ac, ancestorPatchIds.get(ac.getId()));
    }
    ci.setStarred(isStarred(ac));
    return ci;
  }

  private boolean isStarred(final Change ac) {
    return control.getCurrentUser().getStarredChanges().contains(ac.getId());
  }
}
