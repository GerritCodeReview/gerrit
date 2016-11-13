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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.DependencyAttribute;
import com.google.gerrit.server.data.MessageAttribute;
import com.google.gerrit.server.data.PatchAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.PatchSetCommentAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.data.SubmitLabelAttribute;
import com.google.gerrit.server.data.SubmitRecordAttribute;
import com.google.gerrit.server.data.TrackingIdAttribute;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventFactory {
  private static final Logger log = LoggerFactory.getLogger(EventFactory.class);

  private final AccountCache accountCache;
  private final Provider<String> urlProvider;
  private final PatchListCache patchListCache;
  private final AccountByEmailCache byEmailCache;
  private final PersonIdent myIdent;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeKindCache changeKindCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  EventFactory(
      AccountCache accountCache,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      AccountByEmailCache byEmailCache,
      PatchListCache patchListCache,
      @GerritPersonIdent PersonIdent myIdent,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      ChangeKindCache changeKindCache,
      Provider<InternalChangeQuery> queryProvider,
      SchemaFactory<ReviewDb> schema) {
    this.accountCache = accountCache;
    this.urlProvider = urlProvider;
    this.patchListCache = patchListCache;
    this.byEmailCache = byEmailCache;
    this.myIdent = myIdent;
    this.changeDataFactory = changeDataFactory;
    this.approvalsUtil = approvalsUtil;
    this.changeKindCache = changeKindCache;
    this.queryProvider = queryProvider;
    this.schema = schema;
  }

  /**
   * Create a ChangeAttribute for the given change suitable for serialization to JSON.
   *
   * @param change
   * @return object suitable for serialization to JSON
   */
  public ChangeAttribute asChangeAttribute(Change change) {
    try (ReviewDb db = schema.open()) {
      return asChangeAttribute(db, change);
    } catch (OrmException e) {
      log.error("Cannot open database connection", e);
      return new ChangeAttribute();
    }
  }

  /**
   * Create a ChangeAttribute for the given change suitable for serialization to JSON.
   *
   * @param db Review database
   * @param change
   * @return object suitable for serialization to JSON
   */
  public ChangeAttribute asChangeAttribute(ReviewDb db, Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().getShortName();
    a.topic = change.getTopic();
    a.id = change.getKey().get();
    a.number = change.getId().toString();
    a.subject = change.getSubject();
    try {
      a.commitMessage = changeDataFactory.create(db, change).commitMessage();
    } catch (Exception e) {
      log.error("Error while getting full commit message for" + " change " + a.number);
    }
    a.url = getChangeUrl(change);
    a.owner = asAccountAttribute(change.getOwner());
    a.assignee = asAccountAttribute(change.getAssignee());
    a.status = change.getStatus();
    return a;
  }

  /**
   * Create a RefUpdateAttribute for the given old ObjectId, new ObjectId, and branch that is
   * suitable for serialization to JSON.
   *
   * @param oldId
   * @param newId
   * @param refName
   * @return object suitable for serialization to JSON
   */
  public RefUpdateAttribute asRefUpdateAttribute(
      ObjectId oldId, ObjectId newId, Branch.NameKey refName) {
    RefUpdateAttribute ru = new RefUpdateAttribute();
    ru.newRev = newId != null ? newId.getName() : ObjectId.zeroId().getName();
    ru.oldRev = oldId != null ? oldId.getName() : ObjectId.zeroId().getName();
    ru.project = refName.getParentKey().get();
    ru.refName = refName.get();
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
    a.open = change.getStatus().isOpen();
  }

  /**
   * Add allReviewers to an existing ChangeAttribute.
   *
   * @param a
   * @param notes
   */
  public void addAllReviewers(ReviewDb db, ChangeAttribute a, ChangeNotes notes)
      throws OrmException {
    Collection<Account.Id> reviewers = approvalsUtil.getReviewers(db, notes).all();
    if (!reviewers.isEmpty()) {
      a.allReviewers = Lists.newArrayListWithCapacity(reviewers.size());
      for (Account.Id id : reviewers) {
        a.allReviewers.add(asAccountAttribute(id));
      }
    }
  }

  /**
   * Add submitRecords to an existing ChangeAttribute.
   *
   * @param ca
   * @param submitRecords
   */
  public void addSubmitRecords(ChangeAttribute ca, List<SubmitRecord> submitRecords) {
    ca.submitRecords = new ArrayList<>();

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

  private void addSubmitRecordLabels(SubmitRecord submitRecord, SubmitRecordAttribute sa) {
    if (submitRecord.labels != null && !submitRecord.labels.isEmpty()) {
      sa.labels = new ArrayList<>();
      for (SubmitRecord.Label lbl : submitRecord.labels) {
        SubmitLabelAttribute la = new SubmitLabelAttribute();
        la.label = lbl.label;
        la.status = lbl.status.name();
        if (lbl.appliedBy != null) {
          Account a = accountCache.get(lbl.appliedBy).getAccount();
          la.by = asAccountAttribute(a);
        }
        sa.labels.add(la);
      }
    }
  }

  public void addDependencies(RevWalk rw, ChangeAttribute ca, Change change, PatchSet currentPs) {
    if (change == null || currentPs == null) {
      return;
    }
    ca.dependsOn = new ArrayList<>();
    ca.neededBy = new ArrayList<>();
    try {
      addDependsOn(rw, ca, change, currentPs);
      addNeededBy(rw, ca, change, currentPs);
    } catch (OrmException | IOException e) {
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

  private void addDependsOn(RevWalk rw, ChangeAttribute ca, Change change, PatchSet currentPs)
      throws OrmException, IOException {
    RevCommit commit = rw.parseCommit(ObjectId.fromString(currentPs.getRevision().get()));
    final List<String> parentNames = new ArrayList<>(commit.getParentCount());
    for (RevCommit p : commit.getParents()) {
      parentNames.add(p.name());
    }

    // Find changes in this project having a patch set matching any parent of
    // this patch set's revision.
    for (ChangeData cd : queryProvider.get().byProjectCommits(change.getProject(), parentNames)) {
      for (PatchSet ps : cd.patchSets()) {
        for (String p : parentNames) {
          if (!ps.getRevision().get().equals(p)) {
            continue;
          }
          ca.dependsOn.add(newDependsOn(checkNotNull(cd.change()), ps));
        }
      }
    }
    // Sort by original parent order.
    Collections.sort(
        ca.dependsOn,
        comparing(
            (DependencyAttribute d) -> {
              for (int i = 0; i < parentNames.size(); i++) {
                if (parentNames.get(i).equals(d.revision)) {
                  return i;
                }
              }
              return parentNames.size() + 1;
            }));
  }

  private void addNeededBy(RevWalk rw, ChangeAttribute ca, Change change, PatchSet currentPs)
      throws OrmException, IOException {
    if (currentPs.getGroups().isEmpty()) {
      return;
    }
    String rev = currentPs.getRevision().get();
    // Find changes in the same related group as this patch set, having a patch
    // set whose parent matches this patch set's revision.
    for (ChangeData cd :
        queryProvider.get().byProjectGroups(change.getProject(), currentPs.getGroups())) {
      patchSets:
      for (PatchSet ps : cd.patchSets()) {
        RevCommit commit = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
        for (RevCommit p : commit.getParents()) {
          if (!p.name().equals(rev)) {
            continue;
          }
          ca.neededBy.add(newNeededBy(checkNotNull(cd.change()), ps));
          continue patchSets;
        }
      }
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

  public void addTrackingIds(ChangeAttribute a, Multimap<String, String> set) {
    if (!set.isEmpty()) {
      a.trackingIds = new ArrayList<>(set.size());
      for (Map.Entry<String, Collection<String>> e : set.asMap().entrySet()) {
        for (String id : e.getValue()) {
          TrackingIdAttribute t = new TrackingIdAttribute();
          t.system = e.getKey();
          t.id = id;
          a.trackingIds.add(t);
        }
      }
    }
  }

  public void addCommitMessage(ChangeAttribute a, String commitMessage) {
    a.commitMessage = commitMessage;
  }

  public void addPatchSets(
      ReviewDb db,
      RevWalk revWalk,
      ChangeAttribute ca,
      Collection<PatchSet> ps,
      Map<PatchSet.Id, Collection<PatchSetApproval>> approvals,
      LabelTypes labelTypes) {
    addPatchSets(db, revWalk, ca, ps, approvals, false, null, labelTypes);
  }

  public void addPatchSets(
      ReviewDb db,
      RevWalk revWalk,
      ChangeAttribute ca,
      Collection<PatchSet> ps,
      Map<PatchSet.Id, Collection<PatchSetApproval>> approvals,
      boolean includeFiles,
      Change change,
      LabelTypes labelTypes) {
    if (!ps.isEmpty()) {
      ca.patchSets = new ArrayList<>(ps.size());
      for (PatchSet p : ps) {
        PatchSetAttribute psa = asPatchSetAttribute(db, revWalk, change, p);
        if (approvals != null) {
          addApprovals(psa, p.getId(), approvals, labelTypes);
        }
        ca.patchSets.add(psa);
        if (includeFiles) {
          addPatchSetFileNames(psa, change, p);
        }
      }
    }
  }

  public void addPatchSetComments(
      PatchSetAttribute patchSetAttribute, Collection<Comment> comments) {
    for (Comment comment : comments) {
      if (comment.key.patchSetId == Integer.parseInt(patchSetAttribute.number)) {
        if (patchSetAttribute.comments == null) {
          patchSetAttribute.comments = new ArrayList<>();
        }
        patchSetAttribute.comments.add(asPatchSetLineAttribute(comment));
      }
    }
  }

  public void addPatchSetFileNames(
      PatchSetAttribute patchSetAttribute, Change change, PatchSet patchSet) {
    try {
      PatchList patchList = patchListCache.get(change, patchSet);
      for (PatchListEntry patch : patchList.getPatches()) {
        if (patchSetAttribute.files == null) {
          patchSetAttribute.files = new ArrayList<>();
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
      log.warn("Cannot get patch list", e);
    }
  }

  public void addComments(ChangeAttribute ca, Collection<ChangeMessage> messages) {
    if (!messages.isEmpty()) {
      ca.comments = new ArrayList<>();
      for (ChangeMessage message : messages) {
        ca.comments.add(asMessageAttribute(message));
      }
    }
  }

  /**
   * Create a PatchSetAttribute for the given patchset suitable for serialization to JSON.
   *
   * @param revWalk
   * @param patchSet
   * @return object suitable for serialization to JSON
   */
  public PatchSetAttribute asPatchSetAttribute(RevWalk revWalk, Change change, PatchSet patchSet) {
    try (ReviewDb db = schema.open()) {
      return asPatchSetAttribute(db, revWalk, change, patchSet);
    } catch (OrmException e) {
      log.error("Cannot open database connection", e);
      return new PatchSetAttribute();
    }
  }

  /**
   * Create a PatchSetAttribute for the given patchset suitable for serialization to JSON.
   *
   * @param db Review database
   * @param patchSet
   * @return object suitable for serialization to JSON
   */
  public PatchSetAttribute asPatchSetAttribute(
      ReviewDb db, RevWalk revWalk, Change change, PatchSet patchSet) {
    PatchSetAttribute p = new PatchSetAttribute();
    p.revision = patchSet.getRevision().get();
    p.number = Integer.toString(patchSet.getPatchSetId());
    p.ref = patchSet.getRefName();
    p.uploader = asAccountAttribute(patchSet.getUploader());
    p.createdOn = patchSet.getCreatedOn().getTime() / 1000L;
    p.isDraft = patchSet.isDraft();
    PatchSet.Id pId = patchSet.getId();
    try {
      p.parents = new ArrayList<>();
      RevCommit c = revWalk.parseCommit(ObjectId.fromString(p.revision));
      for (RevCommit parent : c.getParents()) {
        p.parents.add(parent.name());
      }

      UserIdentity author = toUserIdentity(c.getAuthorIdent());
      if (author.getAccount() == null) {
        p.author = new AccountAttribute();
        p.author.email = author.getEmail();
        p.author.name = author.getName();
        p.author.username = "";
      } else {
        p.author = asAccountAttribute(author.getAccount());
      }

      List<Patch> list = patchListCache.get(change, patchSet).toPatchList(pId);
      for (Patch pe : list) {
        if (!Patch.isMagic(pe.getFileName())) {
          p.sizeDeletions -= pe.getDeletions();
          p.sizeInsertions += pe.getInsertions();
        }
      }
      p.kind = changeKindCache.getChangeKind(db, change, patchSet);
    } catch (IOException e) {
      log.error("Cannot load patch set data for " + patchSet.getId(), e);
    } catch (PatchListNotAvailableException e) {
      log.error(String.format("Cannot get size information for %s.", pId), e);
    }
    return p;
  }

  // TODO: The same method exists in PatchSetInfoFactory, find a common place
  // for it
  private UserIdentity toUserIdentity(PersonIdent who) {
    UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    // If only one account has access to this email address, select it
    // as the identity of the user.
    //
    Set<Account.Id> a = byEmailCache.get(u.getEmail());
    if (a.size() == 1) {
      u.setAccount(a.iterator().next());
    }

    return u;
  }

  public void addApprovals(
      PatchSetAttribute p,
      PatchSet.Id id,
      Map<PatchSet.Id, Collection<PatchSetApproval>> all,
      LabelTypes labelTypes) {
    Collection<PatchSetApproval> list = all.get(id);
    if (list != null) {
      addApprovals(p, list, labelTypes);
    }
  }

  public void addApprovals(
      PatchSetAttribute p, Collection<PatchSetApproval> list, LabelTypes labelTypes) {
    if (!list.isEmpty()) {
      p.approvals = new ArrayList<>(list.size());
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
   * Create an AuthorAttribute for the given account suitable for serialization to JSON.
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
   * Create an AuthorAttribute for the given account suitable for serialization to JSON.
   *
   * @param account
   * @return object suitable for serialization to JSON
   */
  public AccountAttribute asAccountAttribute(Account account) {
    if (account == null) {
      return null;
    }

    AccountAttribute who = new AccountAttribute();
    who.name = account.getFullName();
    who.email = account.getPreferredEmail();
    who.username = account.getUserName();
    return who;
  }

  /**
   * Create an AuthorAttribute for the given person ident suitable for serialization to JSON.
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
   * Create an ApprovalAttribute for the given approval suitable for serialization to JSON.
   *
   * @param approval
   * @param labelTypes label types for the containing project
   * @return object suitable for serialization to JSON
   */
  public ApprovalAttribute asApprovalAttribute(PatchSetApproval approval, LabelTypes labelTypes) {
    ApprovalAttribute a = new ApprovalAttribute();
    a.type = approval.getLabelId().get();
    a.value = Short.toString(approval.getValue());
    a.by = asAccountAttribute(approval.getAccountId());
    a.grantedOn = approval.getGranted().getTime() / 1000L;
    a.oldValue = null;

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
        message.getAuthor() != null
            ? asAccountAttribute(message.getAuthor())
            : asAccountAttribute(myIdent);
    a.message = message.getMessage();
    return a;
  }

  public PatchSetCommentAttribute asPatchSetLineAttribute(Comment c) {
    PatchSetCommentAttribute a = new PatchSetCommentAttribute();
    a.reviewer = asAccountAttribute(c.author.getId());
    a.file = c.key.filename;
    a.line = c.lineNbr;
    a.message = c.message;
    return a;
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  private String getChangeUrl(Change change) {
    if (change != null && urlProvider.get() != null) {
      StringBuilder r = new StringBuilder();
      r.append(urlProvider.get());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }
}
