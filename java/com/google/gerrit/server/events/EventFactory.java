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

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.UserIdentity;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.config.UrlFormatter;
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
import com.google.gerrit.server.data.SubmitRequirementAttribute;
import com.google.gerrit.server.data.TrackingIdAttribute;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class EventFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AccountCache accountCache;
  private final DynamicItem<UrlFormatter> urlFormatter;
  private final Emails emails;
  private final PatchListCache patchListCache;
  private final Provider<PersonIdent> myIdent;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeKindCache changeKindCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final IndexConfig indexConfig;

  @Inject
  EventFactory(
      AccountCache accountCache,
      Emails emails,
      DynamicItem<UrlFormatter> urlFormatter,
      PatchListCache patchListCache,
      @GerritPersonIdent Provider<PersonIdent> myIdent,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      ChangeKindCache changeKindCache,
      Provider<InternalChangeQuery> queryProvider,
      IndexConfig indexConfig) {
    this.accountCache = accountCache;
    this.urlFormatter = urlFormatter;
    this.emails = emails;
    this.patchListCache = patchListCache;
    this.myIdent = myIdent;
    this.changeDataFactory = changeDataFactory;
    this.approvalsUtil = approvalsUtil;
    this.changeKindCache = changeKindCache;
    this.queryProvider = queryProvider;
    this.indexConfig = indexConfig;
  }

  public ChangeAttribute asChangeAttribute(Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().shortName();
    a.topic = change.getTopic();
    a.id = change.getKey().get();
    a.number = change.getId().get();
    a.subject = change.getSubject();
    try {
      a.commitMessage = changeDataFactory.create(change).commitMessage();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Error while getting full commit message for change %d", a.number);
    }
    a.url = getChangeUrl(change);
    a.owner = asAccountAttribute(change.getOwner());
    a.assignee = asAccountAttribute(change.getAssignee());
    a.status = change.getStatus();
    a.createdOn = change.getCreatedOn().getTime() / 1000L;
    a.wip = change.isWorkInProgress() ? true : null;
    a.isPrivate = change.isPrivate() ? true : null;
    a.cherryPickOfChange =
        change.getCherryPickOf() != null ? change.getCherryPickOf().changeId().get() : null;
    a.cherryPickOfPatchSet =
        change.getCherryPickOf() != null ? change.getCherryPickOf().get() : null;
    return a;
  }

  /** Create a {@link ChangeAttribute} instance from the specified change. */
  public ChangeAttribute asChangeAttribute(Change change, ChangeNotes notes) {
    ChangeAttribute a = asChangeAttribute(change);
    Set<String> hashtags = notes.load().getHashtags();
    if (!hashtags.isEmpty()) {
      a.hashtags = new ArrayList<>(hashtags.size());
      a.hashtags.addAll(hashtags);
    }
    return a;
  }
  /**
   * Create a {@link RefUpdateAttribute} for the given old ObjectId, new ObjectId, and branch that
   * is suitable for serialization to JSON.
   */
  public RefUpdateAttribute asRefUpdateAttribute(
      ObjectId oldId, ObjectId newId, BranchNameKey refName) {
    RefUpdateAttribute ru = new RefUpdateAttribute();
    ru.newRev = newId != null ? newId.getName() : ObjectId.zeroId().getName();
    ru.oldRev = oldId != null ? oldId.getName() : ObjectId.zeroId().getName();
    ru.project = refName.project().get();
    ru.refName = refName.branch();
    return ru;
  }

  /** Extend the existing {@link ChangeAttribute} with additional fields. */
  public void extend(ChangeAttribute a, Change change) {
    a.lastUpdated = change.getLastUpdatedOn().getTime() / 1000L;
    a.open = change.isNew();
  }

  /** Add allReviewers to an existing {@link ChangeAttribute}. */
  public void addAllReviewers(ChangeAttribute a, ChangeNotes notes) {
    Collection<Account.Id> reviewers = approvalsUtil.getReviewers(notes).all();
    if (!reviewers.isEmpty()) {
      a.allReviewers = Lists.newArrayListWithCapacity(reviewers.size());
      for (Account.Id id : reviewers) {
        a.allReviewers.add(asAccountAttribute(id));
      }
    }
  }

  /** Add submitRecords to an existing {@link ChangeAttribute}. */
  public void addSubmitRecords(ChangeAttribute ca, List<SubmitRecord> submitRecords) {
    ca.submitRecords = new ArrayList<>();

    for (SubmitRecord submitRecord : submitRecords) {
      SubmitRecordAttribute sa = new SubmitRecordAttribute();
      sa.status = submitRecord.status.name();
      if (submitRecord.status != SubmitRecord.Status.RULE_ERROR) {
        addSubmitRecordLabels(submitRecord, sa);
        addSubmitRecordRequirements(submitRecord, sa);
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
          la.by = asAccountAttribute(lbl.appliedBy);
        }
        sa.labels.add(la);
      }
    }
  }

  private void addSubmitRecordRequirements(SubmitRecord submitRecord, SubmitRecordAttribute sa) {
    if (submitRecord.requirements != null && !submitRecord.requirements.isEmpty()) {
      sa.requirements = new ArrayList<>();
      for (SubmitRequirement req : submitRecord.requirements) {
        SubmitRequirementAttribute re = new SubmitRequirementAttribute();
        re.fallbackText = req.fallbackText();
        re.type = req.type();
        sa.requirements.add(re);
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
    } catch (StorageException | IOException e) {
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
      throws IOException {
    RevCommit commit = rw.parseCommit(currentPs.commitId());
    final List<String> parentNames = new ArrayList<>(commit.getParentCount());
    for (RevCommit p : commit.getParents()) {
      parentNames.add(p.name());
    }

    // Find changes in this project having a patch set matching any parent of
    // this patch set's revision.
    for (ChangeData cd : queryProvider.get().byProjectCommits(change.getProject(), parentNames)) {
      for (PatchSet ps : cd.patchSets()) {
        for (String p : parentNames) {
          if (!ps.commitId().name().equals(p)) {
            continue;
          }
          ca.dependsOn.add(newDependsOn(requireNonNull(cd.change()), ps));
        }
      }
    }
    // Sort by original parent order.
    ca.dependsOn.sort(
        comparing(
            d -> {
              for (int i = 0; i < parentNames.size(); i++) {
                if (parentNames.get(i).equals(d.revision)) {
                  return i;
                }
              }
              return parentNames.size() + 1;
            }));
  }

  private void addNeededBy(RevWalk rw, ChangeAttribute ca, Change change, PatchSet currentPs)
      throws IOException {
    if (currentPs.groups().isEmpty()) {
      return;
    }
    String rev = currentPs.commitId().name();
    // Find changes in the same related group as this patch set, having a patch
    // set whose parent matches this patch set's revision.
    for (ChangeData cd :
        InternalChangeQuery.byProjectGroups(
            queryProvider, indexConfig, change.getProject(), currentPs.groups())) {
      PATCH_SETS:
      for (PatchSet ps : cd.patchSets()) {
        RevCommit commit = rw.parseCommit(ps.commitId());
        for (RevCommit p : commit.getParents()) {
          if (!p.name().equals(rev)) {
            continue;
          }
          ca.neededBy.add(newNeededBy(requireNonNull(cd.change()), ps));
          continue PATCH_SETS;
        }
      }
    }
  }

  private DependencyAttribute newDependsOn(Change c, PatchSet ps) {
    DependencyAttribute d = newDependencyAttribute(c, ps);
    d.isCurrentPatchSet = ps.id().equals(c.currentPatchSetId());
    return d;
  }

  private DependencyAttribute newNeededBy(Change c, PatchSet ps) {
    return newDependencyAttribute(c, ps);
  }

  private DependencyAttribute newDependencyAttribute(Change c, PatchSet ps) {
    DependencyAttribute d = new DependencyAttribute();
    d.number = c.getId().get();
    d.id = c.getKey().toString();
    d.revision = ps.commitId().name();
    d.ref = ps.refName();
    return d;
  }

  public void addTrackingIds(ChangeAttribute a, ListMultimap<String, String> set) {
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
      RevWalk revWalk,
      ChangeAttribute ca,
      Collection<PatchSet> ps,
      Map<PatchSet.Id, Collection<PatchSetApproval>> approvals,
      LabelTypes labelTypes) {
    addPatchSets(revWalk, ca, ps, approvals, false, null, labelTypes);
  }

  public void addPatchSets(
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
        PatchSetAttribute psa = asPatchSetAttribute(revWalk, change, p);
        if (approvals != null) {
          addApprovals(psa, p.id(), approvals, labelTypes);
        }
        ca.patchSets.add(psa);
        if (includeFiles) {
          addPatchSetFileNames(psa, change, p);
        }
      }
    }
  }

  public void addPatchSetComments(
      PatchSetAttribute patchSetAttribute, Collection<HumanComment> comments) {
    for (HumanComment comment : comments) {
      if (comment.key.patchSetId == patchSetAttribute.number) {
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
    } catch (PatchListObjectTooLargeException e) {
      logger.atWarning().log("Cannot get patch list: %s", e.getMessage());
    } catch (PatchListNotAvailableException e) {
      logger.atSevere().withCause(e).log("Cannot get patch list");
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

  /** Create a PatchSetAttribute for the given patchset suitable for serialization to JSON. */
  public PatchSetAttribute asPatchSetAttribute(RevWalk revWalk, Change change, PatchSet patchSet) {
    PatchSetAttribute p = new PatchSetAttribute();
    p.revision = patchSet.commitId().name();
    p.number = patchSet.number();
    p.ref = patchSet.refName();
    p.uploader = asAccountAttribute(patchSet.uploader());
    p.createdOn = patchSet.createdOn().getTime() / 1000L;
    PatchSet.Id pId = patchSet.id();
    try {
      p.parents = new ArrayList<>();
      RevCommit c = revWalk.parseCommit(ObjectId.fromString(p.revision));
      for (RevCommit parent : c.getParents()) {
        p.parents.add(parent.name());
      }

      UserIdentity author = emails.toUserIdentity(c.getAuthorIdent());
      if (author.getAccount() == null) {
        p.author = new AccountAttribute();
        p.author.email = author.getEmail();
        p.author.name = author.getName();
        p.author.username = "";
      } else {
        p.author = asAccountAttribute(author.getAccount());
      }

      PatchList patchList = patchListCache.get(change, patchSet);
      p.sizeDeletions = patchList.getDeletions();
      p.sizeInsertions = patchList.getInsertions();
      p.kind = changeKindCache.getChangeKind(change, patchSet);
    } catch (IOException | StorageException e) {
      logger.atSevere().withCause(e).log("Cannot load patch set data for %s", patchSet.id());
    } catch (PatchListObjectTooLargeException e) {
      logger.atWarning().log("Cannot get size information for %s: %s", pId, e.getMessage());
    } catch (PatchListNotAvailableException e) {
      logger.atSevere().withCause(e).log("Cannot get size information for %s.", pId);
    }
    return p;
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
        if (a.value() != 0) {
          p.approvals.add(asApprovalAttribute(a, labelTypes));
        }
      }
      if (p.approvals.isEmpty()) {
        p.approvals = null;
      }
    }
  }

  /** Create an AuthorAttribute for the given account suitable for serialization to JSON. */
  public AccountAttribute asAccountAttribute(Account.Id id) {
    if (id == null) {
      return null;
    }
    return accountCache.get(id).map(this::asAccountAttribute).orElse(null);
  }

  /** Create an AuthorAttribute for the given account suitable for serialization to JSON. */
  public AccountAttribute asAccountAttribute(AccountState accountState) {
    AccountAttribute who = new AccountAttribute();
    who.name = accountState.account().fullName();
    who.email = accountState.account().preferredEmail();
    who.username = accountState.userName().orElse(null);
    return who;
  }

  /** Create an AuthorAttribute for the given person ident suitable for serialization to JSON. */
  public AccountAttribute asAccountAttribute(PersonIdent ident) {
    AccountAttribute who = new AccountAttribute();
    who.name = ident.getName();
    who.email = ident.getEmailAddress();
    return who;
  }

  /**
   * Create an ApprovalAttribute for the given approval suitable for serialization to JSON.
   *
   * @param labelTypes label types for the containing project
   * @return object suitable for serialization to JSON
   */
  public ApprovalAttribute asApprovalAttribute(PatchSetApproval approval, LabelTypes labelTypes) {
    ApprovalAttribute a = new ApprovalAttribute();
    a.type = approval.labelId().get();
    a.value = Short.toString(approval.value());
    a.by = asAccountAttribute(approval.accountId());
    a.grantedOn = approval.granted().getTime() / 1000L;
    a.oldValue = null;

    LabelType lt = labelTypes.byLabel(approval.labelId());
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
            : asAccountAttribute(myIdent.get());
    a.message = message.getMessage();
    return a;
  }

  public PatchSetCommentAttribute asPatchSetLineAttribute(HumanComment c) {
    PatchSetCommentAttribute a = new PatchSetCommentAttribute();
    a.reviewer = asAccountAttribute(c.author.getId());
    a.file = c.key.filename;
    a.line = c.lineNbr;
    a.message = c.message;
    return a;
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  private String getChangeUrl(Change change) {
    if (change != null) {
      return urlFormatter.get().getChangeViewUrl(change.getProject(), change.getId()).orElse(null);
    }
    return null;
  }
}
