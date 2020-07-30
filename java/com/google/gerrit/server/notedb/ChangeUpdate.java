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
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHERRY_PICK_OF;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CURRENT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET_DESCRIPTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PRIVATE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REVERT_OF;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_WORK_IN_PROGRESS;
import static com.google.gerrit.server.notedb.NoteDbUtil.sanitizeFooter;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.RobotClassifier;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
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
    ChangeUpdate create(ChangeNotes notes, CurrentUser user, Date when);

    @VisibleForTesting
    ChangeUpdate create(
        ChangeNotes notes, CurrentUser user, Date when, Comparator<String> labelNameComparator);
  }

  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final RobotCommentUpdate.Factory robotCommentUpdateFactory;
  private final DeleteCommentRewriter.Factory deleteCommentRewriterFactory;
  private final RobotClassifier robotClassifier;

  private final Table<String, Account.Id, Optional<Short>> approvals;
  private final Map<Account.Id, ReviewerStateInternal> reviewers = new LinkedHashMap<>();
  private final Map<Address, ReviewerStateInternal> reviewersByEmail = new LinkedHashMap<>();
  private final List<HumanComment> comments = new ArrayList<>();

  private String commitSubject;
  private String subject;
  private String changeId;
  private String branch;
  private Change.Status status;
  private List<SubmitRecord> submitRecords;
  private String submissionId;
  private String topic;
  private String commit;
  private Map<Account.Id, AttentionSetUpdate> plannedAttentionSetUpdates;
  private boolean ignoreDefaultAttentionSetRules;
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
  private Boolean isPrivate;
  private Boolean workInProgress;
  private Integer revertOf;
  private String cherryPickOf;

  private ChangeDraftUpdate draftUpdate;
  private RobotCommentUpdate robotCommentUpdate;
  private DeleteCommentRewriter deleteCommentRewriter;
  private DeleteChangeMessageRewriter deleteChangeMessageRewriter;

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      ProjectCache projectCache,
      RobotClassifier robotClassifier,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Date when,
      ChangeNoteUtil noteUtil) {
    this(
        serverIdent,
        updateManagerFactory,
        draftUpdateFactory,
        robotCommentUpdateFactory,
        deleteCommentRewriterFactory,
        robotClassifier,
        notes,
        user,
        when,
        projectCache
            .get(notes.getProjectName())
            .orElseThrow(illegalState(notes.getProjectName()))
            .getLabelTypes()
            .nameComparator(),
        noteUtil);
  }

  private static Table<String, Account.Id, Optional<Short>> approvals(
      Comparator<String> nameComparator) {
    return TreeBasedTable.create(nameComparator, naturalOrder());
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      RobotCommentUpdate.Factory robotCommentUpdateFactory,
      DeleteCommentRewriter.Factory deleteCommentRewriterFactory,
      RobotClassifier robotClassifier,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator,
      ChangeNoteUtil noteUtil) {
    super(notes, user, serverIdent, noteUtil, when);
    this.updateManagerFactory = updateManagerFactory;
    this.draftUpdateFactory = draftUpdateFactory;
    this.robotCommentUpdateFactory = robotCommentUpdateFactory;
    this.deleteCommentRewriterFactory = deleteCommentRewriterFactory;
    this.robotClassifier = robotClassifier;
    this.approvals = approvals(labelNameComparator);
  }

  public ObjectId commit() throws IOException {
    try (NoteDbUpdateManager updateManager = updateManagerFactory.create(getProjectName())) {
      updateManager.add(this);
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
    checkArgument(status != Change.Status.MERGED, "use merge(RequestId, Iterable<SubmitRecord>)");
    this.status = status;
  }

  public void fixStatusToMerged(SubmissionId submissionId) {
    checkArgument(submissionId != null, "submission id must be set for merged changes");
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId.toString();
  }

  public void putApproval(String label, short value) {
    putApprovalFor(getAccountId(), label, value);
  }

  public void putApprovalFor(Account.Id reviewer, String label, short value) {
    approvals.put(label, reviewer, Optional.of(value));
  }

  void removeApproval(String label) {
    removeApprovalFor(getAccountId(), label);
  }

  public void removeApprovalFor(Account.Id reviewer, String label) {
    approvals.put(label, reviewer, Optional.empty());
  }

  public void merge(SubmissionId submissionId, Iterable<SubmitRecord> submitRecords) {
    this.status = Change.Status.MERGED;
    this.submissionId = submissionId.toString();
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(), "no submit records specified at submit time");
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

  public void putComment(HumanComment.Status status, HumanComment c) {
    verifyComment(c);
    createDraftUpdateIfNull();
    if (status == HumanComment.Status.DRAFT) {
      draftUpdate.putComment(c);
    } else {
      comments.add(c);
      draftUpdate.markCommentPublished(c);
    }
  }

  public void putRobotComment(RobotComment c) {
    verifyComment(c);
    createRobotCommentUpdateIfNull();
    robotCommentUpdate.putComment(c);
  }

  public void deleteComment(HumanComment c) {
    verifyComment(c);
    createDraftUpdateIfNull().deleteComment(c);
  }

  public void deleteCommentByRewritingHistory(String uuid, String newMessage) {
    deleteCommentRewriter =
        deleteCommentRewriterFactory.create(getChange().getId(), uuid, newMessage);
  }

  public void deleteChangeMessageByRewritingHistory(String targetMessageId, String newMessage) {
    deleteChangeMessageRewriter =
        new DeleteChangeMessageRewriter(getChange().getId(), targetMessageId, newMessage);
  }

  @VisibleForTesting
  ChangeDraftUpdate createDraftUpdateIfNull() {
    if (draftUpdate == null) {
      ChangeNotes notes = getNotes();
      if (notes != null) {
        draftUpdate = draftUpdateFactory.create(notes, accountId, realAccountId, authorIdent, when);
      } else {
        // tests will always take the notes != null path above.
        draftUpdate =
            draftUpdateFactory.create(getChange(), accountId, realAccountId, authorIdent, when);
      }
    }
    return draftUpdate;
  }

  private void createRobotCommentUpdateIfNull() {
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
  }

  public void setTopic(String topic) throws ValidationException {

    if (isIllegalTopic(topic)) {
      throw new ValidationException("topic can't contain quotation marks.");
    }
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

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  /**
   * All updates must have a timestamp of null since we use the commit's timestamp. There also must
   * not be multiple updates for a single user. Only the first update takes place because of the
   * different priorities: e.g, if we want to add someone to the attention set but also want to
   * remove someone from the attention set, we should ensure to add/remove that user based on the
   * priority of the addition and removal. If most importantly we want to remove the user, then we
   * must first create the removal, and the addition will not take effect.
   */
  public void addToPlannedAttentionSetUpdates(Set<AttentionSetUpdate> updates) {
    if (updates == null || updates.isEmpty() || robotClassifier.isRobot(accountId)) {
      // No updates to do. Robots don't change attention set.
      return;
    }
    checkArgument(
        updates.stream().noneMatch(a -> a.timestamp() != null),
        "must not specify timestamp for write");

    checkArgument(
        updates.stream().map(AttentionSetUpdate::account).distinct().count() == updates.size(),
        "must not specify multiple updates for single user");

    if (plannedAttentionSetUpdates == null) {
      plannedAttentionSetUpdates = new HashMap<>();
    }

    Set<Account.Id> currentAccountUpdates =
        plannedAttentionSetUpdates.values().stream()
            .map(AttentionSetUpdate::account)
            .collect(Collectors.toSet());
    updates.stream()
        .filter(u -> !currentAccountUpdates.contains(u.account()))
        .forEach(u -> plannedAttentionSetUpdates.putIfAbsent(u.account(), u));
  }

  /**
   * If we need to ignore default attention set rules, no need to add any new updates in this class.
   */
  public void addToPlannedAttentionSetUpdates(AttentionSetUpdate update) {
    if (ignoreDefaultAttentionSetRules) {
      return;
    }
    addToPlannedAttentionSetUpdates(ImmutableSet.of(update));
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
    requireNonNull(groups, "groups may not be null");
    this.groups = groups;
  }

  public void setRevertOf(int revertOf) {
    int ownId = getId().get();
    checkArgument(ownId != revertOf, "A change cannot revert itself");
    this.revertOf = revertOf;
    rootOnly = true;
  }

  public void setCherryPickOf(String cherryPickOf) {
    this.cherryPickOf = cherryPickOf;
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeRevisionNotes(RevWalk rw, ObjectInserter inserter, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (comments.isEmpty() && pushCert == null) {
      return null;
    }
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);
    for (HumanComment c : comments) {
      c.tag = tag;
      cache.get(c.getCommitId()).putComment(c);
    }
    if (pushCert != null) {
      checkState(commit != null);
      cache.get(ObjectId.fromString(commit)).setPushCertificate(pushCert);
    }
    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    checkComments(rnm.revisionNotes, builders);

    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data = inserter.insert(OBJ_BLOB, e.getValue().build(noteUtil.getChangeNoteJson()));
      rnm.noteMap.set(e.getKey(), data);
    }

    return rnm.noteMap.writeTree(inserter);
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return RevisionNoteMap.emptyMap();
    }
    // The old ChangeNotes may have already parsed the revision notes. We can reuse them as long as
    // the ref hasn't advanced.
    ChangeNotes notes = getNotes();
    if (notes != null && notes.revisionNoteMap != null) {
      ObjectId idFromNotes = firstNonNull(notes.load().getRevision(), ObjectId.zeroId());
      if (idFromNotes.equals(curr)) {
        return notes.revisionNoteMap;
      }
    }
    NoteMap noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    // Even though reading from changes might not be enabled, we need to
    // parse any existing revision notes so we can merge them.
    return RevisionNoteMap.parse(
        noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap, HumanComment.Status.PUBLISHED);
  }

  private void checkComments(
      Map<ObjectId, ChangeRevisionNote> existingNotes,
      Map<ObjectId, RevisionNoteBuilder> toUpdate) {
    // Prohibit various kinds of illegal operations on comments.
    Set<Comment.Key> existing = new HashSet<>();
    for (ChangeRevisionNote rn : existingNotes.values()) {
      for (Comment c : rn.getEntities()) {
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
          draftUpdate.deleteComment(c.getCommitId(), c.key);
        }
      }
    }

    for (RevisionNoteBuilder b : toUpdate.values()) {
      for (Comment c : b.put.values()) {
        if (existing.contains(c.key)) {
          throw new StorageException("Cannot update existing published comment: " + c);
        }
      }
    }
  }

  @Override
  protected String getRefName() {
    return changeMetaRef(getId());
  }

  @Override
  protected boolean bypassMaxUpdates() {
    // Allow abandoning or submitting a change even if it would exceed the max update count.
    return status != null && status.isClosed();
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws IOException {
    checkState(
        deleteCommentRewriter == null && deleteChangeMessageRewriter == null,
        "cannot update and rewrite ref in one BatchUpdate");

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
      if (status.equals(Change.Status.ABANDONED)) {
        clearAttentionSet("Change was abandoned");
      }
      if (status.equals(Change.Status.MERGED)) {
        clearAttentionSet("Change was submitted");
      }
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
        noteUtil.appendAccountIdIdentString(msg, assignee.get()).append('\n');
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
      noteUtil.appendAccountIdIdentString(msg, e.getKey()).append('\n');
    }

    applyReviewerUpdatesToAttentionSet();

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
        noteUtil.appendAccountIdIdentString(msg.append(' '), id);
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
              noteUtil.appendAccountIdIdentString(msg, label.appliedBy);
            }
            msg.append('\n');
          }
        }
        // TODO(maximeg) We might want to list plugins that validated this submission.
      }
    }

    if (!Objects.equals(accountId, realAccountId)) {
      addFooter(msg, FOOTER_REAL_USER);
      noteUtil.appendAccountIdIdentString(msg, realAccountId).append('\n');
    }

    if (isPrivate != null) {
      addFooter(msg, FOOTER_PRIVATE, isPrivate);
    }

    if (workInProgress != null) {
      addFooter(msg, FOOTER_WORK_IN_PROGRESS, workInProgress);
      if (workInProgress) {
        clearAttentionSet("Change was marked work in progress");
      } else {
        addAllReviewersToAttentionSet();
      }
    }

    if (revertOf != null) {
      addFooter(msg, FOOTER_REVERT_OF, revertOf);
    }

    if (cherryPickOf != null) {
      addFooter(msg, FOOTER_CHERRY_PICK_OF, cherryPickOf);
    }

    if (plannedAttentionSetUpdates != null) {
      updateAttentionSet(msg);
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setMessage(msg.toString());
    try {
      ObjectId treeId = storeRevisionNotes(rw, ins, curr);
      if (treeId != null) {
        cb.setTreeId(treeId);
      }
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
    return cb;
  }

  private void clearAttentionSet(String reason) {
    if (getNotes().getAttentionSet() == null) {
      return;
    }
    AttentionSetUtil.additionsOnly(getNotes().getAttentionSet()).stream()
        .map(
            a ->
                AttentionSetUpdate.createForWrite(
                    a.account(), AttentionSetUpdate.Operation.REMOVE, reason))
        .forEach(this::addToPlannedAttentionSetUpdates);
  }

  private void applyReviewerUpdatesToAttentionSet() {
    if ((workInProgress != null && workInProgress == true)
        || getNotes().getChange().isWorkInProgress()
        || status == Change.Status.MERGED) {
      // Attention set shouldn't change here for changes that are work in progress or are about to
      // be submitted or when the caller is a robot.
      return;
    }
    Set<Account.Id> currentReviewers =
        getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER);
    Set<AttentionSetUpdate> updates = new HashSet<>();
    for (Map.Entry<Account.Id, ReviewerStateInternal> reviewer : reviewers.entrySet()) {
      // Only add new reviewers to the attention set.
      if (reviewer.getValue().equals(ReviewerStateInternal.REVIEWER)
          && !currentReviewers.contains(reviewer.getKey())) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewer.getKey(), AttentionSetUpdate.Operation.ADD, "Reviewer was added"));
      }
      // Treat both REMOVED and CC as "removed reviewers".
      if (!reviewer.getValue().equals(ReviewerStateInternal.REVIEWER)
          && currentReviewers.contains(reviewer.getKey())) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewer.getKey(), AttentionSetUpdate.Operation.REMOVE, "Reviewer was removed"));
      }
    }
    addToPlannedAttentionSetUpdates(updates);
  }

  private void addAllReviewersToAttentionSet() {
    getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER).stream()
        .map(
            r ->
                AttentionSetUpdate.createForWrite(
                    r, AttentionSetUpdate.Operation.ADD, "Change was marked ready for review"))
        .forEach(this::addToPlannedAttentionSetUpdates);
  }

  /**
   * Any updates to the attention set must be done in {@link #addToPlannedAttentionSetUpdates}. This
   * method is called after all the updates are finished to do the updates once and for real.
   */
  private void updateAttentionSet(StringBuilder msg) {
    if (plannedAttentionSetUpdates == null) {
      return;
    }
    Set<Account.Id> currentUsersInAttentionSet =
        AttentionSetUtil.additionsOnly(getNotes().getAttentionSet()).stream()
            .map(AttentionSetUpdate::account)
            .collect(Collectors.toSet());
    for (AttentionSetUpdate attentionSetUpdate : plannedAttentionSetUpdates.values()) {
      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && currentUsersInAttentionSet.contains(attentionSetUpdate.account())) {
        // Skip users that are already in the attention set: no need to re-add them.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.REMOVE
          && !currentUsersInAttentionSet.contains(attentionSetUpdate.account())) {
        // Skip users that are not in the attention set: no need to remove them.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && robotClassifier.isRobot(attentionSetUpdate.account())) {
        // Skip adding robots to the attention set.
        continue;
      }

      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && approvals.rowKeySet().contains(LabelId.legacySubmit().get())) {
        // On submit, we sometimes can add the person who submitted the change as a reviewer, and in
        // turn it will add that person to the attention set.
        // This ensures we don't add users to the attention set on submit.
        continue;
      }

      addFooter(msg, FOOTER_ATTENTION, noteUtil.attentionSetUpdateToJson(attentionSetUpdate));
    }
  }

  /**
   * When set, default attention set rules are ignored (E.g, adding reviewers -> adds to attention
   * set, etc).
   */
  public void ignoreDefaultAttentionSetRules() {
    ignoreDefaultAttentionSetRules = true;
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
        && plannedAttentionSetUpdates == null
        && assignee == null
        && hashtags == null
        && topic == null
        && commit == null
        && psState == null
        && groups == null
        && tag == null
        && psDescription == null
        && !currentPatchSet
        && isPrivate == null
        && workInProgress == null
        && revertOf == null
        && cherryPickOf == null;
  }

  ChangeDraftUpdate getDraftUpdate() {
    return draftUpdate;
  }

  RobotCommentUpdate getRobotCommentUpdate() {
    return robotCommentUpdate;
  }

  DeleteCommentRewriter getDeleteCommentRewriter() {
    return deleteCommentRewriter;
  }

  DeleteChangeMessageRewriter getDeleteChangeMessageRewriter() {
    return deleteChangeMessageRewriter;
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

  private static boolean isIllegalTopic(String topic) {
    return (topic != null && topic.contains("\""));
  }
}
