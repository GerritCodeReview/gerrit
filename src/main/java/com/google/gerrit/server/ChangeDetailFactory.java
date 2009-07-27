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

package com.google.gerrit.server;

import static com.google.gerrit.server.BaseServiceImplementation.assertCanRead;
import static com.google.gerrit.server.BaseServiceImplementation.canPerform;
import static com.google.gerrit.server.BaseServiceImplementation.canRead;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ApprovalDetail;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetAncestor;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.workflow.CategoryFunction;
import com.google.gerrit.client.workflow.FunctionState;
import com.google.gerrit.server.BaseServiceImplementation.Action;
import com.google.gerrit.server.BaseServiceImplementation.Failure;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a {@link ChangeDetail} from a {@link Change}. */
class ChangeDetailFactory implements Action<ChangeDetail> {
  private final Change.Id changeId;

  private AccountInfoCacheFactory acc;
  private ChangeDetail detail;

  ChangeDetailFactory(final Change.Id id) {
    this.changeId = id;
  }

  public ChangeDetail run(final ReviewDb db) throws OrmException, Failure {
    final Account.Id me = Common.getAccountId();
    final Change change = db.changes().get(changeId);
    if (change == null) {
      throw new Failure(new NoSuchEntityException());
    }
    final PatchSet patch = db.patchSets().get(change.currentPatchSetId());
    final ProjectCache.Entry projEnt =
        Common.getProjectCache().get(change.getDest().getParentKey());
    if (patch == null || projEnt == null) {
      throw new Failure(new NoSuchEntityException());
    }
    final Project proj = projEnt.getProject();
    assertCanRead(change);

    final boolean anon;
    boolean canAbandon = false;
    if (me == null) {
      // Safe assumption, this wouldn't be allowed if it wasn't.
      //
      anon = true;
    } else {
      // Ask if the anonymous user can read this project; even if
      // we can that doesn't mean the anonymous user could.
      //
      anon = canRead(null, change.getDest().getParentKey());

      // The change owner, current patchset uploader, Gerrit administrator,
      // and project administrator can mark the change as abandoned.
      //
      canAbandon = change.getStatus().isOpen();
      canAbandon &=
          me.equals(change.getOwner()) || me.equals(patch.getUploader())
              || Common.getGroupCache().isAdministrator(me)
              || canPerform(me, projEnt, ApprovalCategory.OWN, (short) 1);
    }

    acc = new AccountInfoCacheFactory(db);
    acc.want(change.getOwner());

    detail = new ChangeDetail();
    detail.setChange(change);
    detail.setAllowsAnonymous(anon);
    detail.setCanAbandon(canAbandon);
    detail.setStarred(ChangeListServiceImpl.starredBy(db, me)
        .contains(changeId));
    loadPatchSets(db);
    loadMessages(db);
    if (change.currentPatchSetId() != null) {
      loadCurrentPatchSet(db);
    }
    load(db);
    detail.setAccounts(acc.create());
    return detail;
  }

  private void loadPatchSets(final ReviewDb db) throws OrmException {
    detail.setPatchSets(db.patchSets().byChange(changeId).toList());
  }

  private void loadMessages(final ReviewDb db) throws OrmException {
    detail.setMessages(db.changeMessages().byChange(changeId).toList());
    for (final ChangeMessage m : detail.getMessages()) {
      acc.want(m.getAuthor());
    }
  }

  private void load(final ReviewDb db) throws OrmException {
    final List<ChangeApproval> allApprovals =
        db.changeApprovals().byChange(changeId).toList();

    if (detail.getChange().getStatus().isOpen()) {
      final Account.Id me = Common.getAccountId();
      final FunctionState fs =
          new FunctionState(detail.getChange(), allApprovals);

      final Set<ApprovalCategory.Id> missingApprovals =
          new HashSet<ApprovalCategory.Id>();

      final Set<ApprovalCategory.Id> currentActions =
          new HashSet<ApprovalCategory.Id>();

      for (final ApprovalType at : Common.getGerritConfig().getApprovalTypes()) {
        CategoryFunction.forCategory(at.getCategory()).run(at, fs);
        if (!fs.isValid(at)) {
          missingApprovals.add(at.getCategory().getId());
        }
      }
      for (final ApprovalType at : Common.getGerritConfig().getActionTypes()) {
        if (CategoryFunction.forCategory(at.getCategory()).isValid(me, at, fs)) {
          currentActions.add(at.getCategory().getId());
        }
      }
      detail.setMissingApprovals(missingApprovals);
      detail.setCurrentActions(currentActions);
    }

    final HashMap<Account.Id, ApprovalDetail> ad =
        new HashMap<Account.Id, ApprovalDetail>();
    for (ChangeApproval ca : allApprovals) {
      ApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ApprovalDetail(ca.getAccountId());
        ad.put(d.getAccount(), d);
      }
      d.add(ca);
    }

    final Account.Id owner = detail.getChange().getOwner();
    if (ad.containsKey(owner)) {
      // Ensure the owner always sorts to the top of the table
      //
      ad.get(owner).sortFirst();
    }

    acc.want(ad.keySet());
    detail.setApprovals(ad.values());
  }

  private void loadCurrentPatchSet(final ReviewDb db) throws OrmException {
    final PatchSet.Id psId = detail.getChange().currentPatchSetId();
    final PatchSet ps = detail.getCurrentPatchSet();

    final PatchSetDetail currentDetail = new PatchSetDetail();
    currentDetail.load(db, ps);
    detail.setCurrentPatchSetDetail(currentDetail);

    final HashSet<Change.Id> changesToGet = new HashSet<Change.Id>();
    final List<Change.Id> ancestorOrder = new ArrayList<Change.Id>();
    for (PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(psId)) {
      for (PatchSet p : db.patchSets().byRevision(a.getAncestorRevision())) {
        final Change.Id ck = p.getId().getParentKey();
        if (changesToGet.add(ck)) {
          ancestorOrder.add(ck);
        }
      }
    }

    final RevId cprev = ps.getRevision();
    final List<PatchSetAncestor> descendants =
        cprev != null ? db.patchSetAncestors().descendantsOf(cprev).toList()
            : Collections.<PatchSetAncestor> emptyList();
    for (final PatchSetAncestor a : descendants) {
      changesToGet.add(a.getPatchSet().getParentKey());
    }
    final Map<Change.Id, Change> m =
        db.changes().toMap(db.changes().get(changesToGet));

    final ArrayList<ChangeInfo> dependsOn = new ArrayList<ChangeInfo>();
    for (final Change.Id a : ancestorOrder) {
      final Change ac = m.get(a);
      if (ac != null) {
        dependsOn.add(new ChangeInfo(ac, acc));
      }
    }

    final ArrayList<ChangeInfo> neededBy = new ArrayList<ChangeInfo>();
    for (final PatchSetAncestor a : descendants) {
      final Change ac = m.get(a.getPatchSet().getParentKey());
      if (ac != null) {
        neededBy.add(new ChangeInfo(ac, acc));
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
}
