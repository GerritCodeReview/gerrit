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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.CommentsInNotesUtil.getCommentPsId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
    @VisibleForTesting
    ChangeUpdate create(ChangeControl ctl, Date when,
        Comparator<String> labelNameComparator);
  }

  private final AccountCache accountCache;
  private final Map<String, Optional<Short>> approvals;
  private final Map<Account.Id, ReviewerState> reviewers;
  private Change.Status status;
  private String subject;
  private List<SubmitRecord> submitRecords;
  private final CommentsInNotesUtil commentsUtil;
  private List<PatchLineComment> commentsForBase;
  private List<PatchLineComment> commentsForPs;
  private String changeMessage;
  private ChangeNotes notes;

  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private ChangeDraftUpdate draftUpdate;

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      DraftCommentNotes.Factory draftNotesFactory,
      Provider<AllUsersName> allUsers,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      IdentifiedUser user,
      @Assisted ChangeControl ctl,
      CommentsInNotesUtil commentsUtil) {
    this(serverIdent, anonymousCowardName, repoManager, migration, accountCache,
        updateFactory, draftNotesFactory, allUsers, draftUpdateFactory,
        projectCache, ctl, serverIdent.getWhen(), commentsUtil);
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      DraftCommentNotes.Factory draftNotesFactory,
      Provider<AllUsersName> allUsers,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      CommentsInNotesUtil commentsUtil) {
    this(serverIdent, anonymousCowardName, repoManager, migration, accountCache,
        updateFactory, draftNotesFactory, allUsers, draftUpdateFactory, ctl,
        when,
        projectCache.get(getProjectName(ctl)).getLabelTypes().nameComparator(),
        commentsUtil);
  }

  private static Project.NameKey getProjectName(ChangeControl ctl) {
    return ctl.getChange().getDest().getParentKey();
  }

  @AssistedInject
  private ChangeUpdate(
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      AccountCache accountCache,
      MetaDataUpdate.User updateFactory,
      DraftCommentNotes.Factory draftNotesFactory,
      Provider<AllUsersName> allUsers,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator,
      CommentsInNotesUtil commentsUtil) {
    super(migration, repoManager, updateFactory, ctl, serverIdent,
        anonymousCowardName, when);
    this.draftUpdateFactory = draftUpdateFactory;
    this.accountCache = accountCache;
    this.commentsUtil = commentsUtil;
    this.approvals = Maps.newTreeMap(labelNameComparator);
    this.reviewers = Maps.newLinkedHashMap();
    this.commentsForPs = Lists.newArrayList();
    this.commentsForBase = Lists.newArrayList();
  }

  public void setStatus(Change.Status status) {
    checkArgument(status != Change.Status.SUBMITTED,
        "use submit(Iterable<PatchSetApproval>)");
    this.status = status;
  }

  public void putApproval(String label, short value) {
    approvals.put(label, Optional.of(value));
  }

  public void removeApproval(String label) {
    approvals.put(label, Optional.<Short> absent());
  }

  public void submit(Iterable<SubmitRecord> submitRecords) {
    status = Change.Status.SUBMITTED;
    this.submitRecords = ImmutableList.copyOf(submitRecords);
    checkArgument(!this.submitRecords.isEmpty(),
        "no submit records specified at submit time");
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public void setChangeMessage(String changeMessage) {
    this.changeMessage = changeMessage;
  }

  public void insertComment(PatchLineComment comment) throws OrmException {
    if (comment.getStatus() == Status.DRAFT) {
      insertDraftComment(comment);
    } else {
      insertPublishedComment(comment);
    }
  }

  public void upsertComment(PatchLineComment comment) throws OrmException {
    if (comment.getStatus() == Status.DRAFT) {
      upsertDraftComment(comment);
    } else {
      deleteDraftCommentIfPresent(comment);
      upsertPublishedComment(comment);
    }
  }

  public void updateComment(PatchLineComment comment) throws OrmException {
    if (comment.getStatus() == Status.DRAFT) {
      updateDraftComment(comment);
    } else {
      deleteDraftCommentIfPresent(comment);
      updatePublishedComment(comment);
    }
  }

  public void deleteComment(PatchLineComment comment) throws OrmException {
    if (comment.getStatus() == Status.DRAFT) {
      deleteDraftComment(comment);
    } else {
      throw new IllegalArgumentException("Cannot delete a published comment.");
    }
  }

  private void insertPublishedComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    if (notes == null) {
      notes = getChangeNotes().load();
    }
    if (migration.readComments()) {
      checkArgument(!notes.containsComment(c),
          "A comment already exists with the same key as the following comment,"
          + " so we cannot insert this comment: %s", c);
    }
    if (c.getSide() == 0) {
      commentsForBase.add(c);
    } else {
      commentsForPs.add(c);
    }
  }

  private void insertDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull(c);
    draftUpdate.insertComment(c);
  }

  private void upsertPublishedComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    if (notes == null) {
      notes = getChangeNotes().load();
    }
    // This could allow callers to update a published comment if migration.write
    // is on and migration.readComments is off because we will not be able to
    // verify that the comment didn't already exist as a published comment
    // since we don't have a ReviewDb.
    if (migration.readComments()) {
      checkArgument(!notes.containsCommentPublished(c),
          "Cannot update a comment that has already been published and saved");
    }
    if (c.getSide() == 0) {
      commentsForBase.add(c);
    } else {
      commentsForPs.add(c);
    }
  }

  private void upsertDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull(c);
    draftUpdate.upsertComment(c);
  }

  private void updatePublishedComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    if (notes == null) {
      notes = getChangeNotes().load();
    }
    // See comment above in upsertPublishedComment() about potential risk with
    // this check.
    if (migration.readComments()) {
      checkArgument(!notes.containsCommentPublished(c),
          "Cannot update a comment that has already been published and saved");
    }
    if (c.getSide() == 0) {
      commentsForBase.add(c);
    } else {
      commentsForPs.add(c);
    }
  }

  private void updateDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull(c);
    draftUpdate.updateComment(c);
  }

  private void deleteDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull(c);
    draftUpdate.deleteComment(c);
  }

  private void deleteDraftCommentIfPresent(PatchLineComment c)
      throws OrmException {
    createDraftUpdateIfNull(c);
    draftUpdate.deleteCommentIfPresent(c);
  }

  private void createDraftUpdateIfNull(PatchLineComment c) throws OrmException {
    if (draftUpdate == null) {
      draftUpdate = draftUpdateFactory.create(ctl, when);
      if (psId != null) {
        draftUpdate.setPatchSetId(psId);
      } else {
        draftUpdate.setPatchSetId(getCommentPsId(c));
      }
    }
  }

  private void verifyComment(PatchLineComment c) {
    checkArgument(psId != null,
        "setPatchSetId must be called first");
    checkArgument(getCommentPsId(c).equals(psId),
        "Comment on %s doesn't match previous patch set %s",
        getCommentPsId(c), psId);
    checkArgument(c.getRevId() != null);
    checkArgument(c.getStatus() == Status.PUBLISHED,
        "Cannot add a draft comment to a ChangeUpdate. Use a ChangeDraftUpdate"
        + " for draft comments");
    checkArgument(c.getAuthor().equals(getUser().getAccountId()),
        "The author for the following comment does not match the author of"
        + " this ChangeDraftUpdate (%s): %s", getUser().getAccountId(), c);

  }

  public void putReviewer(Account.Id reviewer, ReviewerState type) {
    checkArgument(type != ReviewerState.REMOVED, "invalid ReviewerType");
    reviewers.put(reviewer, type);
  }

  public void removeReviewer(Account.Id reviewer) {
    reviewers.put(reviewer, ReviewerState.REMOVED);
  }

  /** @return the tree id for the updated tree */
  private ObjectId storeCommentsInNotes() throws OrmException, IOException {
    ChangeNotes notes = ctl.getNotes().load();
    NoteMap noteMap = notes.getNoteMap();
    if (noteMap == null) {
      noteMap = NoteMap.newEmptyMap();
    }
    if (commentsForPs.isEmpty() && commentsForBase.isEmpty()) {
      return null;
    }

    Multimap<PatchSet.Id, PatchLineComment> allCommentsOnBases =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> allCommentsOnPs =
        notes.getPatchSetComments();

    // This writes all comments for the base of this PS to the note map.
    if (!commentsForBase.isEmpty()) {
      List<PatchLineComment> baseCommentsForThisPs =
          new ArrayList<>(allCommentsOnBases.get(psId));
      baseCommentsForThisPs.addAll(commentsForBase);
      commentsUtil.writeCommentsToNoteMap(noteMap, baseCommentsForThisPs,
          inserter);
    }

    // This write all comments for this PS to the note map.
    if (!commentsForPs.isEmpty()) {
      List<PatchLineComment> commentsForThisPs =
          new ArrayList<>(allCommentsOnPs.get(psId));
      commentsForThisPs.addAll(commentsForPs);
      commentsUtil.writeCommentsToNoteMap(noteMap, commentsForThisPs, inserter);
    }
    return noteMap.writeTree(inserter);
  }

  public RevCommit commit() throws IOException {
    BatchMetaDataUpdate batch = openUpdate();
    try {
      writeCommit(batch);
      if (draftUpdate != null) {
        draftUpdate.commit();
      }
      RevCommit c = batch.commit();
      return c;
    } catch (OrmException e) {
      throw new IOException(e);
    } finally {
      batch.close();
    }
  }

  @Override
  public void writeCommit(BatchMetaDataUpdate batch) throws OrmException,
      IOException {
    CommitBuilder builder = new CommitBuilder();
    if (migration.write()) {
      ObjectId treeId = storeCommentsInNotes();
      if (treeId != null) {
        builder.setTreeId(treeId);
      }
    }
    batch.write(this, builder);
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(getChange().getId());
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    if (isEmpty()) {
      return false;
    }
    commit.setAuthor(newIdent(getUser().getAccount(), when));
    commit.setCommitter(new PersonIdent(serverIdent, when));

    int ps = psId != null ? psId.get() : getChange().currentPatchSetId().get();
    StringBuilder msg = new StringBuilder();
    if (subject != null) {
      msg.append(subject);
    } else {
      msg.append("Update patch set ").append(ps);
    }
    msg.append("\n\n");

    if (changeMessage != null) {
      msg.append(changeMessage);
      msg.append("\n\n");
    }


    addFooter(msg, FOOTER_PATCH_SET, ps);
    if (status != null) {
      addFooter(msg, FOOTER_STATUS, status.name().toLowerCase());
    }

    for (Map.Entry<Account.Id, ReviewerState> e : reviewers.entrySet()) {
      Account account = accountCache.get(e.getKey()).getAccount();
      PersonIdent ident = newIdent(account, when);
      addFooter(msg, e.getValue().getFooterKey())
          .append(ident.getName())
          .append(" <").append(ident.getEmailAddress()).append(">\n");
    }

    for (Map.Entry<String, Optional<Short>> e : approvals.entrySet()) {
      if (!e.getValue().isPresent()) {
        addFooter(msg, FOOTER_LABEL, '-', e.getKey());
      } else {
        addFooter(msg, FOOTER_LABEL,
            new LabelVote(e.getKey(), e.getValue().get()).formatWithEquals());
      }
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

    commit.setMessage(msg.toString());
    return true;
  }

  @Override
  protected Project.NameKey getProjectName() {
    return getProjectName(ctl);
  }

  private boolean isEmpty() {
    return approvals.isEmpty()
        && changeMessage == null
        && commentsForBase.isEmpty()
        && commentsForPs.isEmpty()
        && reviewers.isEmpty()
        && status == null
        && subject == null
        && submitRecords == null;
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

  private static String sanitizeFooter(String value) {
    return value.replace('\n', ' ').replace('\0', ' ');
  }
}
