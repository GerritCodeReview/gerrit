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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

@Singleton
public class EventFactory {
  private static final Logger log = LoggerFactory.getLogger(EventFactory.class);
  private final AccountCache accountCache;
  private final Provider<String> urlProvider;
  private final PatchListCache patchListCache;
  private final SchemaFactory<ReviewDb> schema;
  private final PatchSetInfoFactory psInfoFactory;
  private final PersonIdent myIdent;

  @Inject
  EventFactory(AccountCache accountCache,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      PatchSetInfoFactory psif,
      PatchListCache patchListCache, SchemaFactory<ReviewDb> schema,
      @GerritPersonIdent PersonIdent myIdent) {
    this.accountCache = accountCache;
    this.urlProvider = urlProvider;
    this.patchListCache = patchListCache;
    this.schema = schema;
    this.psInfoFactory = psif;
    this.myIdent = myIdent;
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

  /**
   * Add submitRecords to an existing ChangeAttribute.
   *
   * @param ca
   * @param submitRecords
   */
  public void addSubmitRecords(ChangeAttribute ca,
      List<SubmitRecord> submitRecords) {
    ca.submitRecords = new ArrayList<SubmitRecordAttribute>();

    for (SubmitRecord submitRecord : submitRecords) {
      SubmitRecordAttribute sa = new SubmitRecordAttribute();
      sa.status = submitRecord.status.name();
      if (submitRecord.status != SubmitRecord.Status.RULE_ERROR) {
        addSubmitRecordLabels(submitRecord, sa);
      }
      ca.submitRecords.add(sa);
    }
    // Remove empty lists so a confusing label won't be displayed in the output.
    if (ca.submitRecords.isEmpty()) {
      ca.submitRecords = null;
    }
  }

  private void addSubmitRecordLabels(SubmitRecord submitRecord,
      SubmitRecordAttribute sa) {
    if (submitRecord.labels != null && !submitRecord.labels.isEmpty()) {
      sa.labels = new ArrayList<SubmitLabelAttribute>();
      for (SubmitRecord.Label lbl : submitRecord.labels) {
        SubmitLabelAttribute la = new SubmitLabelAttribute();
        la.label = lbl.label;
        la.status = lbl.status.name();
        if(lbl.appliedBy != null) {
          Account a = accountCache.get(lbl.appliedBy).getAccount();
          la.by = asAccountAttribute(a);
        }
        sa.labels.add(la);
      }
    }
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

        final PatchSet ps = db.patchSets().get(psId);
        if (ps == null) {
          log.error("Error while generating the list of descendants for"
              + " PatchSet " + psId + ": Cannot find PatchSet entry in"
              + " database.");
        } else {
          final RevId revId = ps.getRevision();
          for (PatchSetAncestor a : db.patchSetAncestors().descendantsOf(revId)) {
            final PatchSet p = db.patchSets().get(a.getPatchSet());
            if (p == null) {
              log.error("Error while generating the list of descendants for"
                  + " revision " + revId.get() + ": Cannot find PatchSet entry in"
                  + " database for " + a.getPatchSet());
              continue;
            }
            final Change c = db.changes().get(p.getId().getParentKey());
            ca.neededBy.add(newNeededBy(c, p));
          }
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
    d.isCurrentPatchSet = ps.getId().equals(c.currentPatchSetId());
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

  public void addPatchSets(ChangeAttribute a, Collection<PatchSet> ps,
      LabelTypes labelTypes) {
    addPatchSets(a, ps, null, false, null, labelTypes);
  }

  public void addPatchSets(ChangeAttribute ca, Collection<PatchSet> ps,
      Map<PatchSet.Id, Collection<PatchSetApproval>> approvals,
      LabelTypes labelTypes) {
    addPatchSets(ca, ps, approvals, false, null, labelTypes);
  }

  public void addPatchSets(ChangeAttribute ca, Collection<PatchSet> ps,
      Map<PatchSet.Id, Collection<PatchSetApproval>> approvals,
      boolean includeFiles, Change change, LabelTypes labelTypes) {
    if (!ps.isEmpty()) {
      ca.patchSets = new ArrayList<PatchSetAttribute>(ps.size());
      for (PatchSet p : ps) {
        PatchSetAttribute psa = asPatchSetAttribute(p);
        if (approvals != null) {
          addApprovals(psa, p.getId(), approvals, labelTypes);
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
    try {
      PatchList patchList = patchListCache.get(change, patchSet);
      for (PatchListEntry patch : patchList.getPatches()) {
        if (patchSetAttribute.files == null) {
          patchSetAttribute.files = new ArrayList<PatchAttribute>();
        }

        PatchAttribute p = new PatchAttribute();
        p.file = patch.getNewName();
        p.fileOld = patch.getOldName();
        p.type = patch.getChangeType();
        p.deletions -= patch.getDeletions();
        p.insertions = patch.getInsertions();
        patchSetAttribute.files.add(p);
      }
    } catch (PatchListNotAvailableException e) {
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
    final PatchSet.Id pId = patchSet.getId();
    try {
      final ReviewDb db = schema.open();
      try {
        p.parents = new ArrayList<String>();
        for (PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(
            patchSet.getId())) {
          p.parents.add(a.getAncestorRevision().get());
        }

        UserIdentity author = psInfoFactory.get(db, pId).getAuthor();
        if (author.getAccount() == null) {
          p.author = new AccountAttribute();
          p.author.email = author.getEmail();
          p.author.name = author.getName();
          p.author.username = "";
        } else {
          p.author = asAccountAttribute(author.getAccount());
        }

        Change change = db.changes().get(pId.getParentKey());
        List<Patch> list =
            patchListCache.get(change, patchSet).toPatchList(pId);
        for (Patch pe : list) {
          if (!Patch.COMMIT_MSG.equals(pe.getFileName())) {
            p.sizeDeletions -= pe.getDeletions();
            p.sizeInsertions += pe.getInsertions();
          }
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      log.error("Cannot load patch set data for " + patchSet.getId(), e);
    } catch (PatchSetInfoNotAvailableException e) {
      log.error(String.format("Cannot get authorEmail for %s.", pId), e);
    } catch (PatchListNotAvailableException e) {
      log.error(String.format("Cannot get size information for %s.", pId), e);
    }
    return p;
  }

  public void addApprovals(PatchSetAttribute p, PatchSet.Id id,
      Map<PatchSet.Id, Collection<PatchSetApproval>> all,
      LabelTypes labelTypes) {
    Collection<PatchSetApproval> list = all.get(id);
    if (list != null) {
      addApprovals(p, list, labelTypes);
    }
  }

  public void addApprovals(PatchSetAttribute p,
      Collection<PatchSetApproval> list, LabelTypes labelTypes) {
    if (!list.isEmpty()) {
      p.approvals = new ArrayList<ApprovalAttribute>(list.size());
      for (PatchSetApproval a : list) {
        if (a.getValue() != 0) {
          p.approvals.add(asApprovalAttribute(a, labelTypes));
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
    if (id == null) {
      return null;
    }
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
    who.username = account.getUserName();
    return who;
  }

  /**
   * Create an AuthorAttribute for the given person ident suitable for
   * serialization to JSON.
   *
   * @param ident
   * @return object suitable for serialization to JSON
   */
  public AccountAttribute asAccountAttribute(PersonIdent ident) {
    AccountAttribute who = new AccountAttribute();
    who.name = ident.getName();
    who.email = ident.getEmailAddress();
    return who;
  }

  /**
   * Create an ApprovalAttribute for the given approval suitable for
   * serialization to JSON.
   *
   * @param approval
   * @param labelTypes label types for the containing project
   * @return object suitable for serialization to JSON
   */
  public ApprovalAttribute asApprovalAttribute(PatchSetApproval approval,
      LabelTypes labelTypes) {
    ApprovalAttribute a = new ApprovalAttribute();
    a.type = approval.getLabelId().get();
    a.value = Short.toString(approval.getValue());
    a.by = asAccountAttribute(approval.getAccountId());
    a.grantedOn = approval.getGranted().getTime() / 1000L;

    LabelType lt = labelTypes.byLabel(approval.getLabelId());
    if (lt != null) {
      a.description = lt.getName();
    }
    return a;
  }

  public MessageAttribute asMessageAttribute(ChangeMessage message) {
    MessageAttribute a = new MessageAttribute();
    a.timestamp = message.getWrittenOn().getTime() / 1000L;
    a.reviewer =
        message.getAuthor() != null ? asAccountAttribute(message.getAuthor())
            : asAccountAttribute(myIdent);
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
}
