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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A delta to apply to a change.
 * <p>
 * This delta will become two unique commits: one in the AllUsers repo that will
 * contain the draft comments on this change and one in the notes branch that
 * will contain approvals, reviewers, change status, subject, submit records,
 * the change message, and published comments. There are limitations on the set
 * of modifications that can be handled in a single update. In particular, there
 * is a single author and timestamp for each update.
 * <p>
 * This class is not thread-safe.
 */
public class ChangeUpdate extends AbstractChangeUpdate {
  public interface Factory {
    ChangeUpdate create(ChangeControl ctl);
    ChangeUpdate create(ChangeControl ctl, Date when);
    ChangeUpdate create(Change change, @Nullable Account.Id accountId,
        PersonIdent authorIdent, Date when,
        Comparator<String> labelNameComparator);

    @VisibleForTesting
    ChangeUpdate create(ChangeControl ctl, Date when,
        Comparator<String> labelNameComparator);
  }

  private final AccountCache accountCache;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;

  private final Table<String, Account.Id, Optional<Short>> approvals;
  private final Map<Account.Id, ReviewerStateInternal> reviewers = new LinkedHashMap<>();
  private final List<PatchLineComment> comments = new ArrayList<>();

  private String commitSubject;
  private String subject;
  private String changeId;
  private String branch;
  private Change.Status status;
  private List<SubmitRecord> submitRecords;
  private String submissionId;
  private String topic;
  private String commit;
  private Set<String> hashtags;
  private String changeMessage;
  private String tag;
  private PatchSetState psState;
  private Iterable<String> groups;
  private String pushCert;
  private boolean isAllowWriteToNewtRef;

  private ChangeDraftUpdate draftUpdate;

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AccountCache accountCache,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      ChangeNoteUtil noteUtil) {
    this(serverIdent, anonymousCowardName, migration, accountCache,
        updateManagerFactory, draftUpdateFactory,
        projectCache, ctl, serverIdent.getWhen(), noteUtil);
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AccountCache accountCache,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      ChangeNoteUtil noteUtil) {
    this(serverIdent, anonymousCowardName, migration, accountCache,
        updateManagerFactory, draftUpdateFactory, ctl,
        when,
        projectCache.get(getProjectName(ctl)).getLabelTypes().nameComparator(),
        noteUtil);
  }

  private static Project.NameKey getProjectName(ChangeControl ctl) {
    return ctl.getProject().getNameKey();
  }

  private static Table<String, Account.Id, Optional<Short>> approvals(
      Comparator<String> nameComparator) {
    return TreeBasedTable.create(nameComparator, ReviewDbUtil.intKeyOrdering());
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AccountCache accountCache,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator,
      ChangeNoteUtil noteUtil) {
    super(migration, ctl, serverIdent,
        anonymousCowardName, noteUtil, when);
    this.accountCache = accountCache;
    this.draftUpdateFactory = draftUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.approvals = approvals(labelNameComparator);
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      NotesMigration migration,
      AccountCache accountCache,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted @Nullable Account.Id accountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator) {
    super(migration, noteUtil, serverIdent, anonymousCowardName, null, change,
        accountId, authorIdent, when);
    this.accountCache = accountCache;
    this.draftUpdateFactory = draftUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.approvals = approvals(labelNameComparator);
  }

  public ObjectId commit() throws IOException, OrmException {
    try (NoteDbUpdateManager updateManager =
        updateManagerFactory.create(getProjectName())) {
      updateManager.add(this);
      updateManager.stageAndApplyDelta(getChange());
      updateManager.execute();
    }
    return getResult();
  }

  public void setChangeId(String changeId) {
    String old = getChange().getKey().get();
    checkArgument(old.equals(changeId),
        "The Change-Id was already set to %s, so we cannot set this Change-Id: %s",
        old, changeId);
    this.changeId = changeId;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public void setStatus(Change.Status status) {
    checkArgument(status != Change.Status.MERGED,
        "use merge(Iterable<SubmitRecord>)");
    this.status = status;
  }

  public void fixStatus(Change.Status status) {
    this.status = status;
  }

  public void putApproval(String label, short value) {
    putApprovalFor(getAccountId(), label, value);
  }

  public void putApprovalFor(Account.Id reviewer, String label, short value) {
    approvals.put(label, reviewer, Optional.of(value));
  }

  public void removeApproval(String label) {
    removeApprovalFor(getAccountId(), label);
  }

  public void removeApprovalFor(Account.Id reviewer, String label) {
    approvals.put(label, reviewer, Optional.<Short> absent());
  }

  public void merge(String submissionId, Iterable<SubmitRecord> submitRecords) {
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId;
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(),
        "no submit records specified at submit time");
  }

  @Deprecated // Only until we improve ChangeRebuilder to call merge().
  public void setSubmissionId(String submissionId) {
    this.submissionId = submissionId;
  }

  public void setSubjectForCommit(String commitSubject) {
    this.commitSubject = commitSubject;
  }

  void setSubject(String subject) {
    this.subject = subject;
  }

  @VisibleForTesting
  ObjectId getCommit() {
    return ObjectId.fromString(commit);
  }

  public void setChangeMessage(String changeMessage) {
    this.changeMessage = changeMessage;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public void putComment(PatchLineComment c) {
    verifyComment(c);
    createDraftUpdateIfNull();
    if (c.getStatus() == PatchLineComment.Status.DRAFT) {
      draftUpdate.putComment(c);
    } else {
      comments.add(c);
      // Always delete the corresponding comment from drafts. Published comments
      // are immutable, meaning in normal operation we only hit this path when
      // publishing a comment. It's exactly in that case that we have to delete
      // the draft.
      draftUpdate.deleteComment(c);
    }
  }

  public void deleteComment(PatchLineComment c) {
    verifyComment(c);
    if (c.getStatus() == PatchLineComment.Status.DRAFT) {
      createDraftUpdateIfNull().deleteComment(c);
    } else {
      throw new IllegalArgumentException(
          "Cannot delete published comment " + c);
    }
  }

  @VisibleForTesting
  ChangeDraftUpdate createDraftUpdateIfNull() {
    if (draftUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        draftUpdate =
            draftUpdateFactory.create(notes, accountId, authorIdent, when);
      } else {
        draftUpdate = draftUpdateFactory.create(
            getChange(), accountId, authorIdent, when);
      }
    }
    return draftUpdate;
  }

  private void verifyComment(PatchLineComment c) {
    checkArgument(c.getRevId() != null, "RevId required for comment: %s", c);
    checkArgument(c.getAuthor().equals(getAccountId()),
        "The author for the following comment does not match the author of"
        + " this ChangeDraftUpdate (%s): %s", getAccountId(), c);

  }

  public void setTopic(String topic) {
    this.topic = Strings.nullToEmpty(topic);
  }

  public void setCommit(RevWalk rw, ObjectId id) throws IOException {
    setCommit(rw, id, null);
  }

  public void setCommit(RevWalk rw, ObjectId id, String pushCert)
      throws IOException {
    RevCommit commit = rw.parseCommit(id);
    rw.parseBody(commit);
    this.commit = commit.name();
    subject = commit.getShortMessage();
    this.pushCert = pushCert;
  }

  /**
   * Set the revision without depending on the commit being present in the
   * repository; should only be used for converting old corrupt commits.
   */
  public void setRevisionForMissingCommit(String id, String pushCert) {
    commit = id;
    this.pushCert = pushCert;
  }

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  public void putReviewer(Account.Id reviewer, ReviewerStateInternal type) {
    checkArgument(type != ReviewerStateInternal.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerStateInternal.REMOVED);
  }

  public void setPatchSetState(PatchSetState psState) {
    this.psState = psState;
  }

  public void setGroups(List<String> groups) {
    checkNotNull(groups, "groups may not be null");
    this.groups = groups;
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeRevisionNotes(RevWalk rw, ObjectInserter inserter,
      ObjectId curr) throws ConfigInvalidException, OrmException, IOException {
    if (comments.isEmpty() && pushCert == null) {
      return null;
    }
    RevisionNoteMap rnm = getRevisionNoteMap(rw, curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);
    for (PatchLineComment c : comments) {
      c.setTag(tag);
      cache.get(c.getRevId()).putComment(c);
    }
    if (pushCert != null) {
      checkState(commit != null);
      cache.get(new RevId(commit)).setPushCertificate(pushCert);
    }
    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    checkComments(rnm.revisionNotes, builders);

    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data = inserter.insert(
          OBJ_BLOB, e.getValue().build(noteUtil));
      rnm.noteMap.set(ObjectId.fromString(e.getKey().get()), data);
    }

    return rnm.noteMap.writeTree(inserter);
  }

  private RevisionNoteMap getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, OrmException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return RevisionNoteMap.emptyMap();
    }
    if (migration.readChanges()) {
      // If reading from changes is enabled, then the old ChangeNotes may have
      // already parsed the revision notes. We can reuse them as long as the ref
      // hasn't advanced.
      ChangeNotes notes = getNotes();
      if (notes != null && notes.revisionNoteMap != null) {
        ObjectId idFromNotes =
            firstNonNull(notes.load().getRevision(), ObjectId.zeroId());
        if (idFromNotes.equals(curr)) {
          return notes.revisionNoteMap;
        }
      }
    }
    NoteMap noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    // Even though reading from changes might not be enabled, we need to
    // parse any existing revision notes so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil, getId(), rw.getObjectReader(), noteMap, false);
  }

  private void checkComments(Map<RevId, RevisionNote> existingNotes,
      Map<RevId, RevisionNoteBuilder> toUpdate) throws OrmException {
    // Prohibit various kinds of illegal operations on comments.
    Set<PatchLineComment.Key> existing = new HashSet<>();
    for (RevisionNote rn : existingNotes.values()) {
      for (PatchLineComment c : rn.comments) {
        existing.add(c.getKey());
        if (draftUpdate != null) {
          // Take advantage of an existing update on All-Users to prune any
          // published comments from drafts. NoteDbUpdateManager takes care of
          // ensuring that this update is applied before its dependent draft
          // update.
          //
          // Deleting aggressively in this way, combined with filtering out
          // duplicate published/draft comments in ChangeNotes#getDraftComments,
          // makes up for the fact that updates between the change repo and
          // All-Users are not atomic.
          //
          // TODO(dborowitz): We might want to distinguish between deleted
          // drafts that we're fixing up after the fact by putting them in a
          // separate commit. But note that we don't care much about the commit
          // graph of the draft ref, particularly because the ref is completely
          // deleted when all drafts are gone.
          draftUpdate.deleteComment(c.getRevId(), c.getKey());
        }
      }
    }

    for (RevisionNoteBuilder b : toUpdate.values()) {
      for (PatchLineComment c : b.put.values()) {
        if (existing.contains(c.getKey())) {
          throw new OrmException(
              "Cannot update existing published comment: " + c);
        }
      }
    }
  }

  @Override
  protected String getRefName() {
    return changeMetaRef(getId());
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins,
      ObjectId curr) throws OrmException, IOException {
    CommitBuilder cb = new CommitBuilder();

    int ps = psId != null ? psId.get() : getChange().currentPatchSetId().get();
    StringBuilder msg = new StringBuilder();
    if (commitSubject != null) {
      msg.append(commitSubject);
    } else {
      msg.append("Update patch set ").append(ps);
    }
    msg.append("\n\n");

    if (changeMessage != null) {
      msg.append(changeMessage);
      msg.append("\n\n");
    }

    addPatchSetFooter(msg, ps);

    if (changeId != null) {
      addFooter(msg, FOOTER_CHANGE_ID, changeId);
    }

    if (subject != null) {
      addFooter(msg, FOOTER_SUBJECT, subject);
    }

    if (branch != null) {
      addFooter(msg, FOOTER_BRANCH, branch);
    }

    if (status != null) {
      addFooter(msg, FOOTER_STATUS, status.name().toLowerCase());
    }

    if (topic != null) {
      addFooter(msg, FOOTER_TOPIC, topic);
    }

    if (commit != null) {
      addFooter(msg, FOOTER_COMMIT, commit);
    }

    Joiner comma = Joiner.on(',');
    if (hashtags != null) {
      addFooter(msg, FOOTER_HASHTAGS, comma.join(hashtags));
    }

    if (tag != null) {
      addFooter(msg, FOOTER_TAG, tag);
    }

    if (groups != null) {
      addFooter(msg, FOOTER_GROUPS, comma.join(groups));
    }

    for (Map.Entry<Account.Id, ReviewerStateInternal> e : reviewers.entrySet()) {
      addFooter(msg, e.getValue().getFooterKey());
      addIdent(msg, e.getKey()).append('\n');
    }

    for (Table.Cell<String, Account.Id, Optional<Short>> c
        : approvals.cellSet()) {
      addFooter(msg, FOOTER_LABEL);
      if (!c.getValue().isPresent()) {
        msg.append('-').append(c.getRowKey());
      } else {
        msg.append(LabelVote.create(
            c.getRowKey(), c.getValue().get()).formatWithEquals());
      }
      Account.Id id = c.getColumnKey();
      if (!id.equals(getAccountId())) {
        addIdent(msg.append(' '), id);
      }
      msg.append('\n');
    }

    if (submissionId != null) {
      addFooter(msg, FOOTER_SUBMISSION_ID, submissionId);
    }

    if (submitRecords != null) {
      for (SubmitRecord rec : submitRecords) {
        addFooter(msg, FOOTER_SUBMITTED_WITH)
            .append(rec.status);
        if (rec.errorMessage != null) {
          msg.append(' ').append(sanitizeFooter(rec.errorMessage));
        }
        msg.append('\n');

        if (rec.labels != null) {
          for (SubmitRecord.Label label : rec.labels) {
            addFooter(msg, FOOTER_SUBMITTED_WITH)
                .append(label.status).append(": ").append(label.label);
            if (label.appliedBy != null) {
              PersonIdent ident =
                  newIdent(accountCache.get(label.appliedBy).getAccount(), when);
              msg.append(": ").append(ident.getName())
                  .append(" <").append(ident.getEmailAddress()).append('>');
            }
            msg.append('\n');
          }
        }
      }
    }

    cb.setMessage(msg.toString());
    try {
      ObjectId treeId = storeRevisionNotes(rw, ins, curr);
      if (treeId != null) {
        cb.setTreeId(treeId);
      }
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    }
    return cb;
  }

  private void addPatchSetFooter(StringBuilder sb, int ps) {
    addFooter(sb, FOOTER_PATCH_SET).append(ps);
    if (psState != null) {
      sb.append(" (").append(psState.name().toLowerCase()).append(')');
    }
    sb.append('\n');
  }

  @Override
  protected Project.NameKey getProjectName() {
    return getChange().getProject();
  }

  @Override
  public boolean isEmpty() {
    return commitSubject == null
        && approvals.isEmpty()
        && changeMessage == null
        && comments.isEmpty()
        && reviewers.isEmpty()
        && changeId == null
        && branch == null
        && status == null
        && submissionId == null
        && submitRecords == null
        && hashtags == null
        && topic == null
        && commit == null
        && psState == null
        && groups == null
        && tag == null;
  }

  ChangeDraftUpdate getDraftUpdate() {
    return draftUpdate;
  }

  public void setAllowWriteToNewRef(boolean allow) {
    isAllowWriteToNewtRef = allow;
  }

  @Override
  public boolean allowWriteToNewRef() {
    return isAllowWriteToNewtRef;
  }

  private static StringBuilder addFooter(StringBuilder sb, FooterKey footer) {
    return sb.append(footer.getName()).append(": ");
  }

  private static void addFooter(StringBuilder sb, FooterKey footer,
      Object... values) {
    addFooter(sb, footer);
    for (Object value : values) {
      sb.append(value);
    }
    sb.append('\n');
  }

  private StringBuilder addIdent(StringBuilder sb, Account.Id accountId) {
    Account account = accountCache.get(accountId).getAccount();
    PersonIdent ident = newIdent(account, when);

    PersonIdent.appendSanitized(sb, ident.getName());
    sb.append(" <");
    PersonIdent.appendSanitized(sb, ident.getEmailAddress());
    sb.append('>');
    return sb;
  }

  private static String sanitizeFooter(String value) {
    return value.replace('\n', ' ').replace('\0', ' ');
  }
}
