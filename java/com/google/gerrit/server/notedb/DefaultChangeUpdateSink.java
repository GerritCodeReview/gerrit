package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.AbstractChangeUpdate.NO_OP_UPDATE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHERRY_PICK_OF;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COPIED_LABEL;
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
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.LabelVote;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevWalk;

public class DefaultChangeUpdateSink extends ChangeUpdateSink {
  private final ChangeUpdate changeUpdate;

  //TODO(ghareeb): fix
  Account.Id realAccountId;

  public DefaultChangeUpdateSink(ObjectId curr) {
    super(curr);
    //todo:ghareeb
    this.changeUpdate = null;
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins) throws IOException {
    checkState(
        changeUpdate.getDeleteCommentRewriter() == null && changeUpdate.getDeleteChangeMessageRewriter() == null,
        "cannot update and rewrite ref in one BatchUpdate");

    PatchSet.Id patchSetId = changeUpdate.getPatchSetId() != null ? changeUpdate.getPatchSetId() : changeUpdate.getChange().currentPatchSetId();
    StringBuilder msg = new StringBuilder();
    if (changeUpdate.getCommitSubject() != null) {
      msg.append(changeUpdate.getCommitSubject());
    } else {
      msg.append("Update patch set ").append(patchSetId.get());
    }
    msg.append("\n\n");

    if (changeUpdate.getChangeMessage() != null) {
      msg.append(changeUpdate.getChangeMessage());
      msg.append("\n\n");
    }

    addPatchSetFooter(msg, patchSetId);

    if (changeUpdate.isCurrentPatchSet()) {
      addFooter(msg, FOOTER_CURRENT, Boolean.TRUE);
    }

    if (changeUpdate.getPsDescription() != null) {
      addFooter(msg, FOOTER_PATCH_SET_DESCRIPTION, changeUpdate.getPsDescription());
    }

    if (changeUpdate.getChangeId() != null) {
      addFooter(msg, FOOTER_CHANGE_ID, changeUpdate.getChangeId());
    }

    if (changeUpdate.getSubject() != null) {
      addFooter(msg, FOOTER_SUBJECT, changeUpdate.getSubject());
    }

    if (changeUpdate.getBranch() != null) {
      addFooter(msg, FOOTER_BRANCH, changeUpdate.getBranch());
    }

    if (changeUpdate.getStatus() != null) {
      addFooter(msg, FOOTER_STATUS, changeUpdate.getStatus().name().toLowerCase());
      if (changeUpdate.getStatus().equals(Change.Status.ABANDONED)) {
        clearAttentionSet("Change was abandoned");
      }
      if (changeUpdate.getStatus().equals(Change.Status.MERGED)) {
        clearAttentionSet("Change was submitted");
      }
    }

    if (changeUpdate.getTopic() != null) {
      addFooter(msg, FOOTER_TOPIC, changeUpdate.getTopic());
    }

    if (changeUpdate.getCommit() != null) {
      addFooter(msg, FOOTER_COMMIT, changeUpdate.getCommit());
    }

    if (changeUpdate.getAssignee() != null) {
      if (changeUpdate.getAssignee().isPresent()) {
        addFooter(msg, FOOTER_ASSIGNEE);
        changeUpdate.noteUtil.appendAccountIdIdentString(msg, changeUpdate.getAssignee().get()).append('\n');
      } else {
        addFooter(msg, FOOTER_ASSIGNEE).append('\n');
      }
    }

    Joiner comma = Joiner.on(',');
    if (changeUpdate.getHashtags() != null) {
      addFooter(msg, FOOTER_HASHTAGS, comma.join(changeUpdate.getHashtags()));
    }

    if (changeUpdate.getTag() != null) {
      addFooter(msg, FOOTER_TAG, changeUpdate.getTag());
    }

    if (changeUpdate.getGroups() != null) {
      addFooter(msg, FOOTER_GROUPS, comma.join(changeUpdate.getGroups()));
    }

    for (Map.Entry<Account.Id, ReviewerStateInternal> e : changeUpdate.getReviewers().entrySet()) {
      addFooter(msg, e.getValue().getFooterKey());
      // TODO(ghareeb): can we get rid of changeUpdate#noteUtil?
      changeUpdate.noteUtil.appendAccountIdIdentString(msg, e.getKey()).append('\n');
    }

    applyReviewerUpdatesToAttentionSet();

    for (Map.Entry<Address, ReviewerStateInternal> e : changeUpdate.getReviewersByEmail().entrySet()) {
      addFooter(msg, e.getValue().getByEmailFooterKey(), e.getKey().toString());
    }

    for (Table.Cell<String, Account.Id, Optional<PatchSetApproval>> c : changeUpdate.getApprovals().cellSet()) {
      addLabelFooter(msg, c);
    }
    for (PatchSetApproval patchSetApproval : changeUpdate.getCopiedApprovals()) {
      addCopiedLabelFooter(msg, patchSetApproval);
    }

    if (changeUpdate.getSubmissionId() != null) {
      addFooter(msg, FOOTER_SUBMISSION_ID, changeUpdate.getSubmissionId());
    }

    if (changeUpdate.getSubmitRecords() != null) {
      for (SubmitRecord rec : changeUpdate.getSubmitRecords()) {
        addFooter(msg, FOOTER_SUBMITTED_WITH).append(rec.status);
        if (rec.errorMessage != null) {
          msg.append(' ').append(sanitizeFooter(rec.errorMessage));
        }
        msg.append('\n');
        if (rec.ruleName != null) {
          addFooter(msg, FOOTER_SUBMITTED_WITH).append("Rule-Name: ").append(rec.ruleName);
          msg.append('\n');
        }
        if (rec.labels != null) {
          for (SubmitRecord.Label label : rec.labels) {
            // Label names/values are safe to append without sanitizing.
            addFooter(msg, FOOTER_SUBMITTED_WITH)
                .append(label.status)
                .append(": ")
                .append(label.label);
            if (label.appliedBy != null) {
              msg.append(": ");
              changeUpdate.noteUtil.appendAccountIdIdentString(msg, label.appliedBy);
            }
            msg.append('\n');
          }
        }
      }
    }

    if (!Objects.equals(changeUpdate.getAccountId(), realAccountId)) {
      addFooter(msg, FOOTER_REAL_USER);
      changeUpdate.noteUtil.appendAccountIdIdentString(msg, realAccountId).append('\n');
    }

    if (changeUpdate.getPrivate() != null) {
      addFooter(msg, FOOTER_PRIVATE, changeUpdate.getPrivate());
    }

    if (changeUpdate.getWorkInProgress() != null) {
      addFooter(msg, FOOTER_WORK_IN_PROGRESS, changeUpdate.getWorkInProgress());
      if (changeUpdate.getWorkInProgress()) {
        clearAttentionSet("Change was marked work in progress");
      } else {
        addAllReviewersToAttentionSet();
      }
    }

    if (changeUpdate.getRevertOf() != null) {
      addFooter(msg, FOOTER_REVERT_OF, changeUpdate.getRevertOf());
    }

    if (changeUpdate.getCherryPickOf() != null) {
      if (changeUpdate.getCherryPickOf().isPresent()) {
        addFooter(msg, FOOTER_CHERRY_PICK_OF, changeUpdate.getCherryPickOf().get());
      } else {
        // Update cherryPickOf with an empty value.
        addFooter(msg, FOOTER_CHERRY_PICK_OF).append('\n');
      }
    }

    boolean hasAttentionSeUpdates = updateAttentionSet(msg);
    if (isEmptyWithoutAttentionSet() && !hasAttentionSeUpdates) {
      return NO_OP_UPDATE;
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

  /**
   * Any updates to the attention set must be done in {@link #addToPlannedAttentionSetUpdates}. This
   * method is called after all the updates are finished to do the updates once and for real.
   *
   * <p>Changing the behaviour of this method might affect the way a ChangeUpdate is considered to
   * be an "Attention Set Change Only". Make sure the {@link #isAttentionSetChangeOnly} logic is
   * amended as well if needed.
   *
   * @return True if one or more attention set updates are appended to the {@code msg}, and false
   *     otherwise.
   */
  private boolean updateAttentionSet(StringBuilder msg) {
    if (changeUpdate.getPlannedAttentionSetUpdates() == null) {
      plannedAttentionSetUpdates = new HashMap<>();
    }
    Set<Account.Id> currentUsersInAttentionSet =
        AttentionSetUtil.additionsOnly(getNotes().getAttentionSet()).stream()
            .map(AttentionSetUpdate::account)
            .collect(Collectors.toSet());

    // Current reviewers/ccs are the reviewers/ccs before the update + the new reviewers/ccs - the
    // deleted reviewers/ccs.
    Set<Account.Id> currentReviewers =
        Stream.concat(
                getNotes().getReviewers().all().stream(),
                reviewers.entrySet().stream()
                    .filter(r -> r.getValue().asReviewerState() != ReviewerState.REMOVED)
                    .map(r -> r.getKey()))
            .collect(Collectors.toSet());
    currentReviewers.removeAll(
        reviewers.entrySet().stream()
            .filter(r -> r.getValue().asReviewerState() == ReviewerState.REMOVED)
            .map(r -> r.getKey())
            .collect(ImmutableSet.toImmutableSet()));

    removeInactiveUsersFromAttentionSet(currentReviewers);

    boolean hasUpdates = false;

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
          && serviceUserClassifier.isServiceUser(attentionSetUpdate.account())) {
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

      // Don't add accounts that are not active in the change to the attention set.
      if (attentionSetUpdate.operation() == AttentionSetUpdate.Operation.ADD
          && !isActiveOnChange(currentReviewers, attentionSetUpdate.account())) {
        continue;
      }

      addFooter(msg, FOOTER_ATTENTION, noteUtil.attentionSetUpdateToJson(attentionSetUpdate));
      hasUpdates = true;
    }
    return hasUpdates;
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

  private void addPatchSetFooter(StringBuilder sb, PatchSet.Id ps) {
    addFooter(sb, FOOTER_PATCH_SET).append(ps.get());
    if (changeUpdate.getPsState() != null) {
      sb.append(" (").append(changeUpdate.getPsState().name().toLowerCase()).append(')');
    }
    sb.append('\n');
  }

  // todo(ghareeb): this is not good. This is updating the state of ChangeUpdate
  private void clearAttentionSet(String reason) {
    if (changeUpdate.getNotes().getAttentionSet() == null) {
      return;
    }
    AttentionSetUtil.additionsOnly(changeUpdate.getNotes().getAttentionSet()).stream()
        .map(
            a ->
                AttentionSetUpdate.createForWrite(
                    a.account(), AttentionSetUpdate.Operation.REMOVE, reason))
        .forEach(attention -> changeUpdate.addToPlannedAttentionSetUpdates(attention));
  }

  private void applyReviewerUpdatesToAttentionSet() {
    if ((workInProgress != null && workInProgress == true)
        || getNotes().getChange().isWorkInProgress()
        || status == Change.Status.MERGED) {
      // Attention set shouldn't change here for changes that are work in progress or are about to
      // be submitted or when the caller is a robot.
      return;
    }

    Set<AttentionSetUpdate> updates = new HashSet<>();
    Set<Account.Id> currentReviewers =
        getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER);
    for (Map.Entry<Account.Id, ReviewerStateInternal> reviewer : reviewers.entrySet()) {
      Account.Id reviewerId = reviewer.getKey();

      ReviewerStateInternal reviewerState = reviewer.getValue();
      // Only add new reviewers to the attention set. Also, don't add the owner because the owner
      // can only be a "dummy" reviewer for legacy reasons.
      if (reviewerState.equals(ReviewerStateInternal.REVIEWER)
          && !currentReviewers.contains(reviewerId)
          && !reviewerId.equals(getChange().getOwner())) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewerId, AttentionSetUpdate.Operation.ADD, "Reviewer was added"));
      }
      boolean reviewerRemoved =
          !reviewerState.equals(ReviewerStateInternal.REVIEWER)
              && currentReviewers.contains(reviewerId);
      boolean ccRemoved = reviewerState.equals(ReviewerStateInternal.REMOVED);
      if (reviewerRemoved || ccRemoved) {
        updates.add(
            AttentionSetUpdate.createForWrite(
                reviewerId, AttentionSetUpdate.Operation.REMOVE, "Reviewer/Cc was removed"));
      }
    }
    addToPlannedAttentionSetUpdates(updates);
  }

  private void addLabelFooter(
      StringBuilder msg, Cell<String, Account.Id, Optional<PatchSetApproval>> c) {
    addFooter(msg, FOOTER_LABEL);
    String label = c.getRowKey();
    Account.Id reviewerId = c.getColumnKey();
    // Label names/values are safe to append without sanitizing.
    boolean isRemoval = !c.getValue().isPresent();
    if (isRemoval) {
      msg.append('-').append(label);
      // Since vote removals do not need to be referenced, e.g. by the copy approvals, they do not
      // require a UUID.
    } else {
      short value = c.getValue().get().value();
      msg.append(LabelVote.create(label, value).formatWithEquals());
      msg.append(", ");
      msg.append(c.getValue().get().uuid().get());
    }
    if (!reviewerId.equals(changeUpdate.getAccountId())) {
      changeUpdate.noteUtil.appendAccountIdIdentString(msg.append(' '), reviewerId);
    }
    msg.append('\n');
  }

  /** Returns the tree id for the updated tree */
  private ObjectId storeRevisionNotes(RevWalk rw, ObjectInserter inserter, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (changeUpdate.getSubmitRequirementResults() == null && changeUpdate.getComments().isEmpty() && changeUpdate.getPushCert() == null) {
      return null;
    }
    RevisionNoteMap<ChangeRevisionNote> rnm = getRevisionNoteMap(rw, curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);
    for (HumanComment c : changeUpdate.getComments()) {
      c.tag = changeUpdate.getTag();
      cache.get(c.getCommitId()).putComment(c);
    }
    if (changeUpdate.getSubmitRequirementResults() != null) {
      if (changeUpdate.getSubmitRequirementResults().isEmpty()) {
        ObjectId latestPsCommitId =
            Iterables.getLast(changeUpdate.getNotes().getPatchSets().values()).commitId();
        cache.get(latestPsCommitId).createEmptySubmitRequirementResults();
      } else {
        // Clear any previously stored SRs first. The SRs in this update will overwrite any
        // previously stored SRs (e.g. if the change is abandoned (SRs stored) -> un-abandoned ->
        // merged).
        changeUpdate.getSubmitRequirementResults().stream()
            .map(SubmitRequirementResult::patchSetCommitId)
            .distinct()
            .forEach(commit -> cache.get(commit).clearSubmitRequirementResults());
        for (SubmitRequirementResult sr : changeUpdate.getSubmitRequirementResults()) {
          cache.get(sr.patchSetCommitId()).putSubmitRequirementResult(sr);
        }
      }
    }
    if (changeUpdate.getPushCert() != null) {
      // TODO(ghareeb): this was not using my generated getter
      checkState(changeUpdate.getCommit() != null);
      cache.get(changeUpdate.getCommit()).setPushCertificate(changeUpdate.getPushCert());
    }
    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    checkComments(rnm.revisionNotes, builders);

    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data = inserter.insert(OBJ_BLOB, e.getValue().build(changeUpdate.noteUtil.getChangeNoteJson()));
      rnm.noteMap.set(e.getKey(), data);
    }

    return rnm.noteMap.writeTree(inserter);
  }

  private void checkComments(
      Map<ObjectId, ChangeRevisionNote> existingNotes,
      Map<ObjectId, RevisionNoteBuilder> toUpdate) {
    // Prohibit various kinds of illegal operations on comments.
    Set<Comment.Key> existing = new HashSet<>();
    for (ChangeRevisionNote rn : existingNotes.values()) {
      for (Comment c : rn.getEntities()) {
        existing.add(c.key);
        if (changeUpdate.getDraftUpdate() != null) {
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
          changeUpdate.getDraftUpdate().deleteComment(c.getCommitId(), c.key);
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

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return RevisionNoteMap.emptyMap();
    }
    // The old ChangeNotes may have already parsed the revision notes. We can reuse them as long as
    // the ref hasn't advanced.
    ChangeNotes notes = changeUpdate.getNotes();
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
        changeUpdate.noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap, HumanComment.Status.PUBLISHED);
  }

  private void addCopiedLabelFooter(StringBuilder msg, PatchSetApproval patchSetApproval) {
    if (patchSetApproval.value() == 0) {
      addFooter(msg, FOOTER_COPIED_LABEL);

      // Mark the copied approval as deleted.
      msg.append('-').append(patchSetApproval.label());

      changeUpdate.noteUtil.appendAccountIdIdentString(msg.append(' '), patchSetApproval.accountId());

      // In the non-copied labels, we don't need to pass the real account id since it's already
      // in FOOTER_REAL_USER. Here, we want to retain the original real account id.
      if (!patchSetApproval.realAccountId().equals(patchSetApproval.accountId())) {
        changeUpdate.noteUtil.appendAccountIdIdentString(msg.append(","), patchSetApproval.realAccountId());
      }

      msg.append('\n');
      return;
    }
    addFooter(msg, FOOTER_COPIED_LABEL);
    // Label names/values are safe to append without sanitizing.
    msg.append(
        LabelVote.create(patchSetApproval.label(), patchSetApproval.value()).formatWithEquals());
    // Might be copied from the vote that was generated before UUID was introduced.
    if (patchSetApproval.uuid().isPresent()) {
      msg.append(", ");
      msg.append(patchSetApproval.uuid().get());
    }
    Account.Id id = patchSetApproval.accountId();
    changeUpdate.noteUtil.appendAccountIdIdentString(msg.append(' '), id);

    // In the non-copied labels, we don't need to pass the real account id since it's already
    // in FOOTER_REAL_USER. Here, we want to retain the original real account id.
    if (!patchSetApproval.realAccountId().equals(patchSetApproval.accountId())) {
      changeUpdate.noteUtil.appendAccountIdIdentString(msg.append(","), patchSetApproval.realAccountId());
    }

    // In the non-copied labels, we don't need to pass the tag since it's already in
    // FOOTER_TAG, but in this chase we want to retain the original tag, and not the current tag.
    if (patchSetApproval.tag().isPresent()) {
      msg.append(":\"" + sanitizeFooter(patchSetApproval.tag().get()) + "\"");
    }

    msg.append('\n');
  }

  // todo(ghareeb): we should not modify the change update here.
  private void addAllReviewersToAttentionSet() {
    changeUpdate.getNotes().getReviewers().byState(ReviewerStateInternal.REVIEWER).stream()
        .map(
            r ->
                AttentionSetUpdate.createForWrite(
                    r, AttentionSetUpdate.Operation.ADD, "Change was marked ready for review"))
        .forEach(attention -> changeUpdate.addToPlannedAttentionSetUpdates(attention));
  }

  private boolean isEmptyWithoutAttentionSet() {
    return changeUpdate.getCommitSubject() == null
        && changeUpdate.getApprovals().isEmpty()
        && changeUpdate.getCopiedApprovals().isEmpty()
        && changeUpdate.getChangeMessage() == null
        && changeUpdate.getComments().isEmpty()
        && changeUpdate.getReviewers().isEmpty()
        && changeUpdate.getReviewersByEmail().isEmpty()
        && changeUpdate.getChangeId() == null
        && changeUpdate.getBranch() == null
        && changeUpdate.getStatus() == null
        && changeUpdate.getSubmissionId() == null
        && changeUpdate.getSubmitRecords() == null
        && changeUpdate.getAssignee() == null
        && changeUpdate.getHashtags() == null
        && changeUpdate.getTopic() == null
        // TODO(ghareeb): check get commit
        && changeUpdate.getCommit() == null
        && changeUpdate.getPsState() == null
        && changeUpdate.getGroups() == null
        && changeUpdate.getTag() == null
        && changeUpdate.getPsDescription() == null
        && !changeUpdate.isCurrentPatchSet()
        && changeUpdate.getPrivate() == null
        && changeUpdate.getWorkInProgress() == null
        && changeUpdate.getRevertOf() == null
        && changeUpdate.getCherryPickOf() == null;
  }
}
