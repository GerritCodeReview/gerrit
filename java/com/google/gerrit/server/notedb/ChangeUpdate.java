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
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CURRENT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET_DESCRIPTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PRIVATE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_READ_ONLY_UNTIL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REVERT_OF;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_WORK_IN_PROGRESS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.sanitizeFooter;
import static java.util.Comparator.comparing;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A delta to apply to a change.
 *
 * <p>This delta will become two unique commits: one in the AllUsers repo that will contain the
 * draft comments on this change and one in the notes branch that will contain approvals, reviewers,
 * change status, subject, submit records, the change message, and published comments. There are
 * limitations on the set of modifications that can be handled in a single update. In particular,
 * there is a single author and timestamp for each update.
 *
 * <p>This class is not thread-safe.
 */
public class ChangeUpdate extends AbstractChangeUpdate {
  public interface Factory {
    ChangeUpdate create(ChangeNotes notes, CurrentUser user);

    ChangeUpdate create(ChangeNotes notes, CurrentUser user, Date when);

    ChangeUpdate create(
        Change change,
        @Assisted("effective") @Nullable Account.Id accountId,
        @Assisted("real") @Nullable Account.Id realAccountId,
        PersonIdent authorIdent,
        Date when,
        Comparator<String> labelNameComparator);

    @VisibleForTesting
    ChangeUpdate create(
        ChangeNotes notes, CurrentUser user, Date when, Comparator<String> labelNameComparator);
  }

  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final RobotCommentUpdate.Factory robotCommentUpdateFactory;
  private final DeleteCommentRewriter.Factory deleteCommentRewriterFactory;

  private final Table<String, Account.Id, Optional<Short>> approvals;
  private final Map<Account.Id, ReviewerStateInternal> reviewers = new LinkedHashMap<>();
  private final Map<Address, ReviewerStateInternal> reviewersByEmail = new LinkedHashMap<>();
  private final List<Comment> comments = new ArrayList<>();

  private String commitSubject;
  private String subject;
  private String changeId;
  private String branch;
  private Change.Status status;
  private List<SubmitRecord> submitRecords;
  private String submissionId;
  private String topic;
  private String commit;
  private Optional<Account.Id> assignee;
  private Set<String> hashtags;
  private String changeMessage;
  private String tag;
  private PatchSetState psState;
  private Iterable<String> groups;
  private String pushCert;
  private boolean isAllowWriteToNewtRef;
  private String psDescription;
  private boolean currentPatchSet;
  private Timestamp readOnlyUntil;
  private Boolean isPrivate;
  private Boolean workInProgress;
  private Integer revertOf;

  private ChangeDraftUpdate draftUpdate;
  private RobotCommentUpdate robotCommentUpdate;
  private DeleteCommentRewriter deleteCommentRewriter;

  @AssistedInject
  private ChangeUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      ChangeNoteUtil noteUtil) {
    this(
        cfg,
        serverIdent,
        migration,
        updateManagerFactory,
        draftUpdateFactory,
        robotCommentUpdateFactory,
        deleteCommentRewriterFactory,
        projectCache,
        notes,
        user,
        serverIdent.getWhen(),
        noteUtil);
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Date when,
      ChangeNoteUtil noteUtil) {
    this(
        cfg,
        serverIdent,
        migration,
        updateManagerFactory,
        draftUpdateFactory,
        robotCommentUpdateFactory,
        deleteCommentRewriterFactory,
        notes,
        user,
        when,
        projectCache.get(notes.getProjectName()).getLabelTypes().nameComparator(),
        noteUtil);
  }

  private static Table<String, Account.Id, Optional<Short>> approvals(
      Comparator<String> nameComparator) {
    return TreeBasedTable.create(nameComparator, comparing(IntKey::get));
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator,
      ChangeNoteUtil noteUtil) {
    super(cfg, migration, notes, user, serverIdent, noteUtil, when);
    this.updateManagerFactory = updateManagerFactory;
    this.draftUpdateFactory = draftUpdateFactory;
    this.robotCommentUpdateFactory = robotCommentUpdateFactory;
    this.deleteCommentRewriterFactory = deleteCommentRewriterFactory;
    this.approvals = approvals(labelNameComparator);
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritServerConfig Config cfg,
      @GerritPersonIdent PersonIdent serverIdent,
      NotesMigration migration,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ChangeNoteUtil noteUtil,
      @Assisted Change change,
      @Assisted("effective") @Nullable Account.Id accountId,
      @Assisted("real") @Nullable Account.Id realAccountId,
      @Assisted PersonIdent authorIdent,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator) {
    super(
        cfg,
        migration,
        noteUtil,
        serverIdent,
        null,
        change,
        accountId,
        realAccountId,
        authorIdent,
        when);
    this.draftUpdateFactory = draftUpdateFactory;
    this.robotCommentUpdateFactory = robotCommentUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.deleteCommentRewriterFactory = deleteCommentRewriterFactory;
    this.approvals = approvals(labelNameComparator);
  }

  public ObjectId commit() throws IOException, OrmException {
    try (NoteDbUpdateManager updateManager = updateManagerFactory.create(getProjectName())) {
      updateManager.add(this);
      updateManager.stageAndApplyDelta(getChange());
      updateManager.execute();
    }
    return getResult();
  }

  public void setChangeId(String changeId) {
    String old = getChange().getKey().get();
    checkArgument(
        old.equals(changeId),
        "The Change-Id was already set to %s, so we cannot set this Change-Id: %s",
        old,
        changeId);
    this.changeId = changeId;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public void setStatus(Change.Status status) {
    checkArgument(status != Change.Status.MERGED, "use merge(Iterable<SubmitRecord>)");
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
    approvals.put(label, reviewer, Optional.empty());
  }

  public void merge(RequestId submissionId, Iterable<SubmitRecord> submitRecords) {
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId.toStringForStorage();
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(), "no submit records specified at submit time");
  }

  @Deprecated // Only until we improve ChangeRebuilder to call merge().
  public void setSubmissionId(String submissionId) {
    this.submissionId = submissionId;
  }

  public void setSubjectForCommit(String commitSubject) {
    this.commitSubject = commitSubject;
  }

  public void setSubject(String subject) {
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

  public void setPsDescription(String psDescription) {
    this.psDescription = psDescription;
  }

  public void putComment(PatchLineComment.Status status, Comment c) {
    verifyComment(c);
    createDraftUpdateIfNull();
    if (status == PatchLineComment.Status.DRAFT) {
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

  public void putRobotComment(RobotComment c) {
    verifyComment(c);
    createRobotCommentUpdateIfNull();
    robotCommentUpdate.putComment(c);
  }

  public void deleteComment(Comment c) {
    verifyComment(c);
    createDraftUpdateIfNull().deleteComment(c);
  }

  public void deleteCommentByRewritingHistory(String uuid, String newMessage) {
    deleteCommentRewriter =
        deleteCommentRewriterFactory.create(getChange().getId(), uuid, newMessage);
  }

  @VisibleForTesting
  ChangeDraftUpdate createDraftUpdateIfNull() {
    if (draftUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        draftUpdate = draftUpdateFactory.create(notes, accountId, realAccountId, authorIdent, when);
      } else {
        draftUpdate =
            draftUpdateFactory.create(getChange(), accountId, realAccountId, authorIdent, when);
      }
    }
    return draftUpdate;
  }

  @VisibleForTesting
  RobotCommentUpdate createRobotCommentUpdateIfNull() {
    if (robotCommentUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        robotCommentUpdate =
            robotCommentUpdateFactory.create(notes, accountId, realAccountId, authorIdent, when);
      } else {
        robotCommentUpdate =
            robotCommentUpdateFactory.create(
                getChange(), accountId, realAccountId, authorIdent, when);
      }
    }
    return robotCommentUpdate;
  }

  public void setTopic(String topic) {
    this.topic = Strings.nullToEmpty(topic);
  }

  public void setCommit(RevWalk rw, ObjectId id) throws IOException {
    setCommit(rw, id, null);
  }

  public void setCommit(RevWalk rw, ObjectId id, String pushCert) throws IOException {
    RevCommit commit = rw.parseCommit(id);
    rw.parseBody(commit);
    this.commit = commit.name();
    subject = commit.getShortMessage();
    this.pushCert = pushCert;
  }

  /**
   * Set the revision without depending on the commit being present in the repository; should only
   * be used for converting old corrupt commits.
   */
  public void setRevisionForMissingCommit(String id, String pushCert) {
    commit = id;
    this.pushCert = pushCert;
  }

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  public void setAssignee(Account.Id assignee) {
    checkArgument(assignee != null, "use removeAssignee");
    this.assignee = Optional.of(assignee);
  }

  public void removeAssignee() {
    this.assignee = Optional.empty();
  }

  public Map<Account.Id, ReviewerStateInternal> getReviewers() {
    return reviewers;
  }

  public void putReviewer(Account.Id reviewer, ReviewerStateInternal type) {
    checkArgument(type != ReviewerStateInternal.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerStateInternal.REMOVED);
  }

  public void putReviewerByEmail(Address reviewer, ReviewerStateInternal type) {
    checkArgument(type != ReviewerStateInternal.REMOVED, "invalid ReviewerType");
    reviewersByEmail.put(reviewer, type);
  }

  public void removeReviewerByEmail(Address reviewer) {
    reviewersByEmail.put(reviewer, ReviewerStateInternal.REMOVED);
  }

  public void setPatchSetState(PatchSetState psState) {
    this.psState = psState;
  }

  public void setCurrentPatchSet() {
    this.currentPatchSet = true;
  }

  public void setGroups(List<String> groups) {
    checkNotNull(groups, "groups may not be null");
    this.groups = groups;
  }

  public void setRevertOf(int revertOf) {
    int ownId = getChange().getId().get();
    checkArgument(ownId != revertOf, "A change cannot revert itself");
    this.revertOf = revertOf;
    rootOnly = true;
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeRevisionNotes(RevWalk rw, ObjectInserter inserter, ObjectId curr)
      throws ConfigInvalidException, OrmException, IOException {
    if (comments.isEmpty() && pushCert == null) {
      return null;
    }
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);
    for (Comment c : comments) {
      c.tag = tag;
      cache.get(new RevId(c.revId)).putComment(c);
    }
    if (pushCert != null) {
      checkState(commit != null);
      cache.get(new RevId(commit)).setPushCertificate(pushCert);
    }
    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    checkComments(rnm.revisionNotes, builders);

    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data =
          inserter.insert(OBJ_BLOB, e.getValue().build(noteUtil, noteUtil.getWriteJson()));
      rnm.noteMap.set(ObjectId.fromString(e.getKey().get()), data);
    }

    return rnm.noteMap.writeTree(inserter);
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
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
        ObjectId idFromNotes = firstNonNull(notes.load().getRevision(), ObjectId.zeroId());
        if (idFromNotes.equals(curr)) {
          return notes.revisionNoteMap;
        }
      }
    }
    NoteMap noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    // Even though reading from changes might not be enabled, we need to
    // parse any existing revision notes so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil, getId(), rw.getObjectReader(), noteMap, PatchLineComment.Status.PUBLISHED);
  }

  private void checkComments(
      Map<RevId, ChangeRevisionNote> existingNotes, Map<RevId, RevisionNoteBuilder> toUpdate)
      throws OrmException {
    // Prohibit various kinds of illegal operations on comments.
    Set<Comment.Key> existing = new HashSet<>();
    for (ChangeRevisionNote rn : existingNotes.values()) {
      for (Comment c : rn.getComments()) {
        existing.add(c.key);
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
          draftUpdate.deleteComment(c.revId, c.key);
        }
      }
    }

    for (RevisionNoteBuilder b : toUpdate.values()) {
      for (Comment c : b.put.values()) {
        if (existing.contains(c.key)) {
          throw new OrmException("Cannot update existing published comment: " + c);
        }
      }
    }
  }

  @Override
  protected String getRefName() {
    return changeMetaRef(getId());
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException {
    checkState(deleteCommentRewriter == null, "cannot update and rewrite ref in one BatchUpdate");

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

    if (currentPatchSet) {
      addFooter(msg, FOOTER_CURRENT, Boolean.TRUE);
    }

    if (psDescription != null) {
      addFooter(msg, FOOTER_PATCH_SET_DESCRIPTION, psDescription);
    }

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

    if (assignee != null) {
      if (assignee.isPresent()) {
        addFooter(msg, FOOTER_ASSIGNEE);
        addIdent(msg, assignee.get()).append('\n');
      } else {
        addFooter(msg, FOOTER_ASSIGNEE).append('\n');
      }
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

    for (Map.Entry<Address, ReviewerStateInternal> e : reviewersByEmail.entrySet()) {
      addFooter(msg, e.getValue().getByEmailFooterKey(), e.getKey().toString());
    }

    for (Table.Cell<String, Account.Id, Optional<Short>> c : approvals.cellSet()) {
      addFooter(msg, FOOTER_LABEL);
      // Label names/values are safe to append without sanitizing.
      if (!c.getValue().isPresent()) {
        msg.append('-').append(c.getRowKey());
      } else {
        msg.append(LabelVote.create(c.getRowKey(), c.getValue().get()).formatWithEquals());
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
        addFooter(msg, FOOTER_SUBMITTED_WITH).append(rec.status);
        if (rec.errorMessage != null) {
          msg.append(' ').append(sanitizeFooter(rec.errorMessage));
        }
        msg.append('\n');

        if (rec.labels != null) {
          for (SubmitRecord.Label label : rec.labels) {
            // Label names/values are safe to append without sanitizing.
            addFooter(msg, FOOTER_SUBMITTED_WITH)
                .append(label.status)
                .append(": ")
                .append(label.label);
            if (label.appliedBy != null) {
              msg.append(": ");
              addIdent(msg, label.appliedBy);
            }
            msg.append('\n');
          }
        }
        // TODO(maximeg) We might want to list plugins that validated this submission.
      }
    }

    if (!Objects.equals(accountId, realAccountId)) {
      addFooter(msg, FOOTER_REAL_USER);
      addIdent(msg, realAccountId).append('\n');
    }

    if (readOnlyUntil != null) {
      addFooter(msg, FOOTER_READ_ONLY_UNTIL, ChangeNoteUtil.formatTime(serverIdent, readOnlyUntil));
    }

    if (isPrivate != null) {
      addFooter(msg, FOOTER_PRIVATE, isPrivate);
    }

    if (workInProgress != null) {
      addFooter(msg, FOOTER_WORK_IN_PROGRESS, workInProgress);
    }

    if (revertOf != null) {
      addFooter(msg, FOOTER_REVERT_OF, revertOf);
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
        && reviewersByEmail.isEmpty()
        && changeId == null
        && branch == null
        && status == null
        && submissionId == null
        && submitRecords == null
        && assignee == null
        && hashtags == null
        && topic == null
        && commit == null
        && psState == null
        && groups == null
        && tag == null
        && psDescription == null
        && !currentPatchSet
        && readOnlyUntil == null
        && isPrivate == null
        && workInProgress == null
        && revertOf == null;
  }

  ChangeDraftUpdate getDraftUpdate() {
    return draftUpdate;
  }

  RobotCommentUpdate getRobotCommentUpdate() {
    return robotCommentUpdate;
  }

  public DeleteCommentRewriter getDeleteCommentRewriter() {
    return deleteCommentRewriter;
  }

  public void setAllowWriteToNewRef(boolean allow) {
    isAllowWriteToNewtRef = allow;
  }

  @Override
  public boolean allowWriteToNewRef() {
    return isAllowWriteToNewtRef;
  }

  public void setPrivate(boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  public void setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
  }

  void setReadOnlyUntil(Timestamp readOnlyUntil) {
    this.readOnlyUntil = readOnlyUntil;
  }

  private static StringBuilder addFooter(StringBuilder sb, FooterKey footer) {
    return sb.append(footer.getName()).append(": ");
  }

  private static void addFooter(StringBuilder sb, FooterKey footer, Object... values) {
    addFooter(sb, footer);
    for (Object value : values) {
      sb.append(sanitizeFooter(Objects.toString(value)));
    }
    sb.append('\n');
  }

  private StringBuilder addIdent(StringBuilder sb, Account.Id accountId) {
    PersonIdent ident = newIdent(accountId, when);

    PersonIdent.appendSanitized(sb, ident.getName());
    sb.append(" <");
    PersonIdent.appendSanitized(sb, ident.getEmailAddress());
    sb.append('>');
    return sb;
  }

  @Override
  protected void checkNotReadOnly() throws OrmException {
    // Allow setting Read-only-until to 0 to release an existing lease.
    if (readOnlyUntil != null && readOnlyUntil.getTime() == 0) {
      return;
    }
    super.checkNotReadOnly();
  }
}
