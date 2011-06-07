// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.TrackingId;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

@Singleton
public class EventFactory {
  private final AccountCache accountCache;
  private final Provider<String> urlProvider;
  private final ApprovalTypes approvalTypes;

  @Inject
  EventFactory(AccountCache accountCache,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      ApprovalTypes approvalTypes) {
    this.accountCache = accountCache;
    this.urlProvider = urlProvider;
    this.approvalTypes = approvalTypes;
  }

  /**
   * Create a ChangeAttribute for the given change suitable for serialization to
   * JSON.
   *
   * @param change
   * @return object suitable for serialization to JSON
   */
  public ChangeAttribute asChangeAttribute(final Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().getShortName();
    a.topic = change.getTopic();
    a.id = change.getKey().get();
    a.number = change.getId().toString();
    a.subject = change.getSubject();
    a.url = getChangeUrl(change);
    a.owner = asAccountAttribute(change.getOwner());
    return a;
  }

  /**
   * Create a RefUpdateAttribute for the given old ObjectId, new ObjectId, and
   * branch that is suitable for serialization to JSON.
   *
   * @param refUpdate
   * @param refName
   * @return object suitable for serialization to JSON
   */
  public RefUpdateAttribute asRefUpdateAttribute(final ObjectId oldId, final ObjectId newId, final Branch.NameKey refName) {
    RefUpdateAttribute ru = new RefUpdateAttribute();
    ru.newRev = newId != null ? newId.getName() : ObjectId.zeroId().getName();
    ru.oldRev = oldId != null ? oldId.getName() : ObjectId.zeroId().getName();
    ru.project = refName.getParentKey().get();
    ru.refName = refName.getShortName();
    return ru;
  }

  /**
   * Extend the existing ChangeAttribute with additional fields.
   *
   * @param a
   * @param change
   */
  public void extend(ChangeAttribute a, Change change) {
    a.lastUpdated = change.getLastUpdatedOn().getTime() / 1000L;
    a.sortKey = change.getSortKey();
    a.open = change.getStatus().isOpen();
    a.status = change.getStatus();
  }

  public void addTrackingIds(ChangeAttribute a, Collection<TrackingId> ids) {
    if (!ids.isEmpty()) {
      a.trackingIds = new ArrayList<TrackingIdAttribute>(ids.size());
      for (TrackingId t : ids) {
        a.trackingIds.add(asTrackingIdAttribute(t));
      }
    }
  }

  public void addPatchSets(ChangeAttribute a, Collection<PatchSet> ps) {
    addPatchSets(a, ps, null);
  }

  public void addPatchSets(ChangeAttribute ca, Collection<PatchSet> ps,
      Map<PatchSet.Id,Collection<PatchSetApproval>> approvals) {
    if (!ps.isEmpty()) {
      ca.patchSets = new ArrayList<PatchSetAttribute>(ps.size());
      for (PatchSet p : ps) {
        PatchSetAttribute psa = asPatchSetAttribute(p);
        if (approvals != null) {
          addApprovals(psa, p.getId(), approvals);
        }
        ca.patchSets.add(psa);
      }
    }
  }

  public TrackingIdAttribute asTrackingIdAttribute(TrackingId id) {
    TrackingIdAttribute a = new TrackingIdAttribute();
    a.system = id.getSystem();
    a.id = id.getTrackingId();
    return a;
  }

  /**
   * Create a PatchSetAttribute for the given patchset suitable for
   * serialization to JSON.
   *
   * @param patchSet
   * @return object suitable for serialization to JSON
   */
  public PatchSetAttribute asPatchSetAttribute(final PatchSet patchSet) {
    PatchSetAttribute p = new PatchSetAttribute();
    p.revision = patchSet.getRevision().get();
    p.number = Integer.toString(patchSet.getPatchSetId());
    p.ref = patchSet.getRefName();
    p.uploader = asAccountAttribute(patchSet.getUploader());
    return p;
  }

  public void addApprovals(PatchSetAttribute p, PatchSet.Id id,
      Map<PatchSet.Id,Collection<PatchSetApproval>> all) {
    Collection<PatchSetApproval> list = all.get(id);
    if (list != null) {
      addApprovals(p, list);
    }
  }

  public void addApprovals(PatchSetAttribute p,
      Collection<PatchSetApproval> list) {
    if (!list.isEmpty()) {
      p.approvals = new ArrayList<ApprovalAttribute>(list.size());
      for (PatchSetApproval a : list) {
        if (a.getValue() != 0) {
          p.approvals.add(asApprovalAttribute(a));
        }
      }
      if (p.approvals.isEmpty()) {
        p.approvals = null;
      }
    }
  }

  /**
   * Create an AuthorAttribute for the given account suitable for serialization
   * to JSON.
   *
   * @param id
   * @return object suitable for serialization to JSON
   */
  public AccountAttribute asAccountAttribute(Account.Id id) {
    return asAccountAttribute(accountCache.get(id).getAccount());
  }

  /**
   * Create an AuthorAttribute for the given account suitable for serialization
   * to JSON.
   *
   * @param account
   * @return object suitable for serialization to JSON
   */
  public AccountAttribute asAccountAttribute(final Account account) {
    AccountAttribute who = new AccountAttribute();
    who.name = account.getFullName();
    who.email = account.getPreferredEmail();
    return who;
  }

  /**
   * Create an ApprovalAttribute for the given approval suitable for
   * serialization to JSON.
   *
   * @param approval
   * @return object suitable for serialization to JSON
   */
  public ApprovalAttribute asApprovalAttribute(PatchSetApproval approval) {
    ApprovalAttribute a = new ApprovalAttribute();
    a.type = approval.getCategoryId().get();
    a.value = Short.toString(approval.getValue());
    a.by = asAccountAttribute(approval.getAccountId());
    a.grantedOn = approval.getGranted().getTime() / 1000L;

    ApprovalType at = approvalTypes.byId(approval.getCategoryId());
    if (at != null) {
      a.description = at.getCategory().getName();
    }
    return a;
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  private String getChangeUrl(final Change change) {
    if (change != null && urlProvider.get() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(urlProvider.get());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }
}
