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
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.CommentsInNotesUtil.addCommentToMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
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
    @VisibleForTesting
    ChangeUpdate create(ChangeControl ctl, Date when,
        Comparator<String> labelNameComparator);
  }

  private final AccountCache accountCache;
  private final Map<String, Short> approvals;
  private final Map<Account.Id, ReviewerStateInternal> reviewers;
  private final Multimap<Account.Id, String> removedApprovals;
  private Change.Status status;
  private String subject;
  private List<SubmitRecord> submitRecords;
  private final CommentsInNotesUtil commentsUtil;
  private List<PatchLineComment> comments;
  private String topic;
  private Set<String> hashtags;
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
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      CommentsInNotesUtil commentsUtil) {
    this(serverIdent, anonymousCowardName, repoManager, migration, accountCache,
        updateFactory, draftUpdateFactory,
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
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ProjectCache projectCache,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      CommentsInNotesUtil commentsUtil) {
    this(serverIdent, anonymousCowardName, repoManager, migration, accountCache,
        updateFactory, draftUpdateFactory, ctl,
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
    this.removedApprovals =
        MultimapBuilder.linkedHashKeys(1).arrayListValues(1).build();
    this.comments = Lists.newArrayList();
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
    approvals.put(label, value);
  }

  public void removeApproval(String label) {
    removeApprovalFor(getUser().getAccountId(), label);
  }

  public void removeApprovalFor(Account.Id reviewer, String label) {
    removedApprovals.put(reviewer, label);
  }

  public void merge(Iterable<SubmitRecord> submitRecords) {
    this.status = Change.Status.MERGED;
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
    if (migration.readChanges()) {
      checkArgument(!notes.containsComment(c),
          "A comment already exists with the same key as the following comment,"
          + " so we cannot insert this comment: %s", c);
    }
    comments.add(c);
  }

  private void insertDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull();
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
    if (migration.readChanges()) {
      checkArgument(!notes.containsCommentPublished(c),
          "Cannot update a comment that has already been published and saved");
    }
    comments.add(c);
  }

  private void upsertDraftComment(PatchLineComment c) {
    createDraftUpdateIfNull();
    draftUpdate.upsertComment(c);
  }

  private void updatePublishedComment(PatchLineComment c) throws OrmException {
    verifyComment(c);
    if (notes == null) {
      notes = getChangeNotes().load();
    }
    // See comment above in upsertPublishedComment() about potential risk with
    // this check.
    if (migration.readChanges()) {
      checkArgument(!notes.containsCommentPublished(c),
          "Cannot update a comment that has already been published and saved");
    }
    comments.add(c);
  }

  private void updateDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull();
    draftUpdate.updateComment(c);
  }

  private void deleteDraftComment(PatchLineComment c) throws OrmException {
    createDraftUpdateIfNull();
    draftUpdate.deleteComment(c);
  }

  private void deleteDraftCommentIfPresent(PatchLineComment c)
      throws OrmException {
    createDraftUpdateIfNull();
    draftUpdate.deleteCommentIfPresent(c);
  }

  private void createDraftUpdateIfNull() {
    if (draftUpdate == null) {
      draftUpdate = draftUpdateFactory.create(ctl, when);
    }
  }

  private void verifyComment(PatchLineComment c) {
    checkArgument(c.getRevId() != null);
    checkArgument(c.getStatus() == Status.PUBLISHED,
        "Cannot add a draft comment to a ChangeUpdate. Use a ChangeDraftUpdate"
        + " for draft comments");
    checkArgument(c.getAuthor().equals(getUser().getAccountId()),
        "The author for the following comment does not match the author of"
        + " this ChangeDraftUpdate (%s): %s", getUser().getAccountId(), c);

  }

  public void setTopic(String topic) {
    this.topic = Strings.nullToEmpty(topic);
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

  /** @return the tree id for the updated tree */
  private ObjectId storeCommentsInNotes() throws OrmException, IOException {
    ChangeNotes notes = ctl.getNotes().load();
    NoteMap noteMap = notes.getNoteMap();
    if (noteMap == null) {
      noteMap = NoteMap.newEmptyMap();
    }
    if (comments.isEmpty()) {
      return null;
    }

    Map<RevId, List<PatchLineComment>> allComments = Maps.newHashMap();
    for (Map.Entry<RevId, Collection<PatchLineComment>> e
        : notes.getComments().asMap().entrySet()) {
      List<PatchLineComment> comments = new ArrayList<>();
      for (PatchLineComment c : e.getValue()) {
        comments.add(c);
      }
      allComments.put(e.getKey(), comments);
    }
    for (PatchLineComment c : comments) {
      addCommentToMap(allComments, c);
    }
    commentsUtil.writeCommentsToNoteMap(noteMap, allComments, inserter);
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
    if (migration.writeChanges()) {
      ObjectId treeId = storeCommentsInNotes();
      if (treeId != null) {
        builder.setTreeId(treeId);
      }
    }
    batch.write(this, builder);
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(ctl.getId());
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

    if (topic != null) {
      addFooter(msg, FOOTER_TOPIC, topic);
    }

    if (hashtags != null) {
      addFooter(msg, FOOTER_HASHTAGS, Joiner.on(",").join(hashtags));
    }

    for (Map.Entry<Account.Id, ReviewerStateInternal> e : reviewers.entrySet()) {
      addFooter(msg, e.getValue().getFooterKey());
      addIdent(msg, e.getKey()).append('\n');
    }

    for (Map.Entry<String, Short> e : approvals.entrySet()) {
      addFooter(msg, FOOTER_LABEL, LabelVote.create(
          e.getKey(), e.getValue()).formatWithEquals());
    }

    for (Map.Entry<Account.Id, String> e : removedApprovals.entries()) {
      addFooter(msg, FOOTER_LABEL).append('-').append(e.getValue());
      Account.Id id = e.getKey();
      if (!id.equals(ctl.getUser().getAccountId())) {
        addIdent(msg.append(' '), id);
      }
      msg.append('\n');
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
        && removedApprovals.isEmpty()
        && changeMessage == null
        && comments.isEmpty()
        && reviewers.isEmpty()
        && status == null
        && subject == null
        && submitRecords == null
        && hashtags == null
        && topic == null;
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
    sb.append(ident.getName()).append(" <").append(ident.getEmailAddress())
        .append('>');
    return sb;
  }

  private static String sanitizeFooter(String value) {
    return value.replace('\n', ' ').replace('\0', ' ');
  }
}
