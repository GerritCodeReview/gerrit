// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetAncestor;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Detail necessary to display {@link ChangeScreen}. */
public class ChangeDetail {
  protected AccountInfoCache accounts;
  protected Change change;
  protected List<ChangeInfo> dependsOn;
  protected List<ChangeInfo> neededBy;
  protected List<PatchSet> patchSets;
  protected List<ApprovalDetail> approvals;
  protected List<ChangeMessage> messages;
  protected PatchSet.Id currentPatchSetId;
  protected PatchSetDetail currentDetail;

  public ChangeDetail() {
  }

  public void load(final ReviewDb db, final AccountInfoCacheFactory acc,
      final Change c) throws OrmException {
    change = c;
    acc.want(change.getOwner());

    patchSets = db.patchSets().byChange(change.getId()).toList();
    messages = db.changeMessages().byChange(change.getId()).toList();
    for (final ChangeMessage m : messages) {
      acc.want(m.getAuthor());
    }

    final HashMap<Account.Id, ApprovalDetail> ad =
        new HashMap<Account.Id, ApprovalDetail>();
    {
      final ApprovalDetail d = new ApprovalDetail(change.getOwner());
      d.sortOrder = ApprovalDetail.EG_0;
      // TODO Mark self-approved, self-verified if permitted.
      ad.put(d.getAccount(), d);
    }
    for (ChangeApproval ca : db.changeApprovals().byChange(change.getId())) {
      ApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ApprovalDetail(ca.getAccountId());
        ad.put(d.getAccount(), d);
      }
      d.add(ca);
    }
    acc.want(ad.keySet());
    approvals = new ArrayList<ApprovalDetail>(ad.values());
    Collections.sort(approvals, new Comparator<ApprovalDetail>() {
      public int compare(final ApprovalDetail o1, final ApprovalDetail o2) {
        return o2.sortOrder.compareTo(o1.sortOrder);
      }
    });

    currentPatchSetId = change.currentPatchSetId();
    if (currentPatchSetId != null) {
      currentDetail = new PatchSetDetail();
      currentDetail.load(db, getCurrentPatchSet());

      final HashSet<Change.Id> changesToGet = new HashSet<Change.Id>();
      final List<Change.Id> ancestorOrder = new ArrayList<Change.Id>();
      for (final PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(
          currentPatchSetId).toList()) {
        for (PatchSet p : db.patchSets().byRevision(a.getAncestorRevision())) {
          final Change.Id ck = p.getId().getParentKey();
          if (changesToGet.add(ck)) {
            ancestorOrder.add(ck);
          }
        }
      }

      final RevId cprev = getCurrentPatchSet().getRevision();
      final List<PatchSetAncestor> descendants =
          cprev != null ? db.patchSetAncestors().descendantsOf(cprev).toList()
              : Collections.<PatchSetAncestor> emptyList();
      for (final PatchSetAncestor a : descendants) {
        changesToGet.add(a.getPatchSet().getParentKey());
      }
      final Map<Change.Id, Change> m =
          db.changes().toMap(db.changes().get(changesToGet));

      dependsOn = new ArrayList<ChangeInfo>();
      for (final Change.Id a : ancestorOrder) {
        final Change ac = m.get(a);
        if (ac != null) {
          dependsOn.add(new ChangeInfo(ac, acc));
        }
      }

      neededBy = new ArrayList<ChangeInfo>();
      for (final PatchSetAncestor a : descendants) {
        final Change ac = m.get(a.getPatchSet().getParentKey());
        if (ac != null) {
          neededBy.add(new ChangeInfo(ac, acc));
        }
      }

      Collections.sort(neededBy, new Comparator<ChangeInfo>() {
        public int compare(final ChangeInfo o1, final ChangeInfo o2) {
          // TODO sort neededBy by something more reasonable than Id
          return o1.getId().get() - o2.getId().get();
        }
      });
    }

    accounts = acc.create();
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public Change getChange() {
    return change;
  }

  public List<ChangeInfo> getDependsOn() {
    return dependsOn;
  }

  public List<ChangeInfo> getNeededBy() {
    return neededBy;
  }

  public List<ChangeMessage> getMessages() {
    return messages;
  }

  public List<PatchSet> getPatchSets() {
    return patchSets;
  }

  public List<ApprovalDetail> getApprovals() {
    return approvals;
  }

  public PatchSet getCurrentPatchSet() {
    if (currentPatchSetId != null) {
      // We search through the list backwards because its *very* likely
      // that the current patch set is also the last patch set.
      //
      for (int i = patchSets.size() - 1; i >= 0; i--) {
        final PatchSet ps = patchSets.get(i);
        if (ps.getId().equals(currentPatchSetId)) {
          return ps;
        }
      }
    }
    return null;
  }

  public PatchSetDetail getCurrentPatchSetDetail() {
    return currentDetail;
  }

  public String getDescription() {
    return currentDetail != null ? currentDetail.getInfo().getMessage() : "";
  }
}
