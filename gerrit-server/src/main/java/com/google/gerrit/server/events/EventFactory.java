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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.ReplicationCallback.ReplicationStatus;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

@Singleton
public class EventFactory {
  private final AccountCache accountCache;
  private final Provider<String> urlProvider;
  private final ApprovalTypes approvalTypes;
  private final PatchListCache patchListCache;
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  EventFactory(AccountCache accountCache,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      ApprovalTypes approvalTypes,
      PatchListCache patchListCache, SchemaFactory<ReviewDb> schema) {
    this.accountCache = accountCache;
    this.urlProvider = urlProvider;
    this.approvalTypes = approvalTypes;
    this.patchListCache = patchListCache;
    this.schema = schema;
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
    a.createdOn = change.getCreatedOn().getTime() / 1000L;
    a.lastUpdated = change.getLastUpdatedOn().getTime() / 1000L;
    a.sortKey = change.getSortKey();
    a.open = change.getStatus().isOpen();
    a.status = change.getStatus();
  }

  public void addDependencies(ChangeAttribute ca, Change change) {
    ca.dependsOn = new ArrayList<DependencyAttribute>();
    ca.neededBy = new ArrayList<DependencyAttribute>();
    try {
      final ReviewDb db = schema.open();
      try {
        final PatchSet.Id psId = change.currentPatchSetId();
        for (PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(psId)) {
          for (PatchSet p :
              db.patchSets().byRevision(a.getAncestorRevision())) {
            Change c = db.changes().get(p.getId().getParentKey());
            ca.dependsOn.add(newDependsOn(c, p));
          }
        }

        final RevId revId = db.patchSets().get(psId).getRevision();
        for (PatchSetAncestor a : db.patchSetAncestors().descendantsOf(revId)) {
          final PatchSet p = db.patchSets().get(a.getPatchSet());
          final Change c = db.changes().get(p.getId().getParentKey());
          ca.neededBy.add(newNeededBy(c, p));
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      // Squash DB exceptions and leave dependency lists partially filled.
    }
    // Remove empty lists so a confusing label won't be displayed in the output.
    if (ca.dependsOn.isEmpty()) {
      ca.dependsOn = null;
    }
    if (ca.neededBy.isEmpty()) {
      ca.neededBy = null;
    }
  }

  private DependencyAttribute newDependsOn(Change c, PatchSet ps) {
    DependencyAttribute d = newDependencyAttribute(c, ps);
    d.isCurrentPatchSet = c.currPatchSetId().equals(ps.getId());
    return d;
  }

  private DependencyAttribute newNeededBy(Change c, PatchSet ps) {
    return newDependencyAttribute(c, ps);
  }

  private DependencyAttribute newDependencyAttribute(Change c, PatchSet ps) {
    DependencyAttribute d = new DependencyAttribute();
    d.number = c.getId().toString();
    d.id = c.getKey().toString();
    d.revision = ps.getRevision().get();
    d.ref = ps.getRefName();
    return d;
  }

  public void addTrackingIds(ChangeAttribute a, Collection<TrackingId> ids) {
    if (!ids.isEmpty()) {
      a.trackingIds = new ArrayList<TrackingIdAttribute>(ids.size());
      for (TrackingId t : ids) {
        a.trackingIds.add(asTrackingIdAttribute(t));
      }
    }
  }

  public void addCommitMessage(ChangeAttribute a, String commitMessage) {
    a.commitMessage = commitMessage;
  }

  public void addPatchSets(ChangeAttribute a, Collection<PatchSet> ps) {
    addPatchSets(a, ps, null, false, null);
  }

  public void addPatchSets(ChangeAttribute ca, Collection<PatchSet> ps,
      Map<PatchSet.Id,Collection<PatchSetApproval>> approvals) {
    addPatchSets(ca, ps, approvals, false, null);
  }

  public void addPatchSets(ChangeAttribute ca, Collection<PatchSet> ps,
      Map<PatchSet.Id,Collection<PatchSetApproval>> approvals,
      boolean includeFiles, Change change) {

    if (!ps.isEmpty()) {
      ca.patchSets = new ArrayList<PatchSetAttribute>(ps.size());
      for (PatchSet p : ps) {
        PatchSetAttribute psa = asPatchSetAttribute(p);
        if (approvals != null) {
          addApprovals(psa, p.getId(), approvals);
        }
        ca.patchSets.add(psa);
        if (includeFiles && change != null) {
          addPatchSetFileNames(psa, change, p);
        }
      }
    }
  }

  public void addPatchSetComments(PatchSetAttribute patchSetAttribute,
      Collection<PatchLineComment> patchLineComments) {
    for (PatchLineComment comment : patchLineComments) {
      if (comment.getKey().getParentKey().getParentKey().get()
          == Integer.parseInt(patchSetAttribute.number)) {
        if (patchSetAttribute.comments == null) {
          patchSetAttribute.comments =
            new ArrayList<PatchSetCommentAttribute>();
        }
        patchSetAttribute.comments.add(asPatchSetLineAttribute(comment));
      }
    }
  }

  public void addPatchSetFileNames(PatchSetAttribute patchSetAttribute,
      Change change, PatchSet patchSet) {
    PatchList patchList = patchListCache.get(change, patchSet);
    for (PatchListEntry patch : patchList.getPatches()) {
      if (patchSetAttribute.files == null) {
        patchSetAttribute.files = new ArrayList<PatchAttribute>();
      }

      PatchAttribute p = new PatchAttribute();
      p.file = patch.getNewName();
      p.type = patch.getChangeType();
      patchSetAttribute.files.add(p);
    }
  }

  public void addComments(ChangeAttribute ca,
      Collection<ChangeMessage> messages) {
    if (!messages.isEmpty()) {
      ca.comments = new ArrayList<MessageAttribute>();
      for (ChangeMessage message : messages) {
        ca.comments.add(asMessageAttribute(message));
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
    p.createdOn = patchSet.getCreatedOn().getTime() / 1000L;
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

  public MessageAttribute asMessageAttribute(ChangeMessage message) {
    MessageAttribute a = new MessageAttribute();
    a.timestamp = message.getWrittenOn().getTime() / 1000L;
    a.reviewer = asAccountAttribute(message.getAuthor());
    a.message = message.getMessage();
    return a;
  }

  public PatchSetCommentAttribute asPatchSetLineAttribute(PatchLineComment c) {
    PatchSetCommentAttribute a = new PatchSetCommentAttribute();
    a.reviewer = asAccountAttribute(c.getAuthor());
    a.file = c.getKey().getParentKey().get();
    a.line = c.getLine();
    a.message = c.getMessage();
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

  public PatchSetReplicationAttribute asPatchSetReplicationAttribute(URIish uri,
      ReplicationStatus status, int finishedNodes, int totalNodes) {
    PatchSetReplicationAttribute r = new PatchSetReplicationAttribute();
    if (uri.isRemote()) {
      r.nodeName = uri.getHost();
    } else {
      r.nodeName = "localhost";
    }

    r.status = status.name();
    r.finishedNodes = finishedNodes;
    r.totalNodes = totalNodes;
    return r;
  }
}
