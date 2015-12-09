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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.testutil.TestChanges.incrementPatchSet;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ChangeNotesTest extends AbstractChangeNotesTest {
  @Test
  public void approvalsOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getApprovals().keySet())
        .containsExactly(c.currentPatchSetId());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(0).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(0).getValue()).isEqualTo((short) -1);
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 1000)));

    assertThat(psas.get(1).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(1).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(1).getLabel()).isEqualTo("Verified");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 1);
    assertThat(psas.get(1).getGranted()).isEqualTo(psas.get(0).getGranted());
  }

  @Test
  public void approvalsMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, PatchSetApproval> psas = notes.getApprovals();
    assertThat(psas).hasSize(2);

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertThat(psa1.getPatchSetId()).isEqualTo(ps1);
    assertThat(psa1.getAccountId().get()).isEqualTo(1);
    assertThat(psa1.getLabel()).isEqualTo("Code-Review");
    assertThat(psa1.getValue()).isEqualTo((short) -1);
    assertThat(psa1.getGranted()).isEqualTo(truncate(after(c, 1000)));

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertThat(psa2.getPatchSetId()).isEqualTo(ps2);
    assertThat(psa2.getAccountId().get()).isEqualTo(1);
    assertThat(psa2.getLabel()).isEqualTo("Code-Review");
    assertThat(psa2.getValue()).isEqualTo((short) +1);
    assertThat(psa2.getGranted()).isEqualTo(truncate(after(c, 2000)));
  }

  @Test
  public void approvalsMultipleApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getLabel()).isEqualTo("Code-Review");
    assertThat(psa.getValue()).isEqualTo((short) -1);

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getLabel()).isEqualTo("Code-Review");
    assertThat(psa.getValue()).isEqualTo((short) 1);
  }

  @Test
  public void approvalsMultipleUsers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getApprovals().keySet())
        .containsExactly(c.currentPatchSetId());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(0).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(0).getValue()).isEqualTo((short) -1);
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 1000)));

    assertThat(psas.get(1).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(1).getAccountId().get()).isEqualTo(2);
    assertThat(psas.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 1);
    assertThat(psas.get(1).getGranted()).isEqualTo(truncate(after(c, 2000)));
  }

  @Test
  public void approvalsTombstone() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Not-For-Long", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId().get()).isEqualTo(1);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 1);

    update = newUpdate(c, changeOwner);
    update.removeApproval("Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getApprovals()).isEmpty();
  }

  @Test
  public void multipleReviewers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewers()).isEqualTo(
        ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(1),
          REVIEWER, new Account.Id(2)));
  }

  @Test
  public void reviewerTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewers()).isEqualTo(
        ImmutableSetMultimap.of(
            REVIEWER, new Account.Id(1),
            CC, new Account.Id(2)));
  }

  @Test
  public void oneReviewerMultipleTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewers()).isEqualTo(
        ImmutableSetMultimap.of(REVIEWER, new Account.Id(2)));

    update = newUpdate(c, otherUser);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getReviewers()).isEqualTo(
        ImmutableSetMultimap.of(CC, new Account.Id(2)));
  }

  @Test
  public void removeReviewer() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas =
        notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);
    assertThat(psas.get(0).getAccountId())
        .isEqualTo(changeOwner.getAccount().getId());
    assertThat(psas.get(1).getAccountId())
        .isEqualTo(otherUser.getAccount().getId());

    update = newUpdate(c, changeOwner);
    update.removeReviewer(otherUser.getAccount().getId());
    update.commit();

    notes = newNotes(c);
    psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(1);
    assertThat(psas.get(0).getAccountId())
        .isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void submitRecords() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.merge(ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)),
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<SubmitRecord> recs = notes.getSubmitRecords();
    assertThat(recs).hasSize(2);
    assertThat(recs.get(0)).isEqualTo(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)));
    assertThat(recs.get(1)).isEqualTo(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null)));
  }

  @Test
  public void latestSubmitRecordsOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");
    update.merge(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", otherUser.getAccountId()))));
    update.commit();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 2");
    update.merge(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId()))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getSubmitRecords()).containsExactly(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId())));
  }

  @Test
  public void emptyChangeUpdate() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.commit();
    assertThat(update.getRevision()).isNull();
  }

  @Test
  public void hashtagCommit() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    LinkedHashSet<String> hashtags = new LinkedHashSet<>();
    hashtags.add("tag1");
    hashtags.add("tag2");
    update.setHashtags(hashtags);
    update.commit();
    try (RevWalk walk = new RevWalk(repo)) {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertThat(commit.getFullMessage()).endsWith("Hashtags: tag1,tag2\n");
    }
  }

  @Test
  public void hashtagChangeNotes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    LinkedHashSet<String> hashtags = new LinkedHashSet<>();
    hashtags.add("tag1");
    hashtags.add("tag2");
    update.setHashtags(hashtags);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getHashtags()).isEqualTo(hashtags);
  }

  @Test
  public void topicChangeNotes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    // initially topic is not set
    ChangeNotes notes = newNotes(c);
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set topic
    String topic = "myTopic";
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting empty string
    update.setTopic("");
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set other topic
    topic = "otherTopic";
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting null
    update.setTopic(null);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();
  }

  @Test
  public void emptyExceptSubject() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.setSubject("Create change");
    update.commit();
    assertThat(update.getRevision()).isNotNull();
  }

  @Test
  public void multipleUpdatesInBatch() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, changeOwner);
    update1.putApproval("Verified", (short) 1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    BatchMetaDataUpdate batch = update1.openUpdate();
    try {
      batch.write(update1, new CommitBuilder());
      batch.write(update2, new CommitBuilder());
      batch.commit();
    } finally {
      batch.close();
    }

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas =
        notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getAccountId())
        .isEqualTo(changeOwner.getAccount().getId());
    assertThat(psas.get(0).getLabel()).isEqualTo("Verified");
    assertThat(psas.get(0).getValue()).isEqualTo((short) 1);

    assertThat(psas.get(1).getAccountId())
        .isEqualTo(otherUser.getAccount().getId());
    assertThat(psas.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 2);
  }

  @Test
  public void multipleUpdatesIncludingComments() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String message1 = "comment 1";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();
    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    BatchMetaDataUpdate batch = update1.openUpdateInBatch(bru);
    PatchLineComment comment1 = newPublishedComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update1.setPatchSetId(psId);
    update1.upsertComment(comment1);
    update1.writeCommit(batch);
    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);
    update2.writeCommit(batch);

    try (RevWalk rw = new RevWalk(repo)) {
      batch.commit();
      bru.execute(rw, NullProgressMonitor.INSTANCE);

      ChangeNotes notes = newNotes(c);
      ObjectId tip = notes.getRevision();
      RevCommit commitWithApprovals = rw.parseCommit(tip);
      assertThat(commitWithApprovals).isNotNull();
      RevCommit commitWithComments = commitWithApprovals.getParent(0);
      assertThat(commitWithComments).isNotNull();

      try (ChangeNotesParser notesWithComments =
          new ChangeNotesParser(c, commitWithComments.copy(), rw, repoManager)) {
        notesWithComments.parseAll();
        ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals1 =
            notesWithComments.buildApprovals();
        assertThat(approvals1).isEmpty();
        assertThat(notesWithComments.comments).hasSize(1);
      }

      try (ChangeNotesParser notesWithApprovals =
          new ChangeNotesParser(c, commitWithApprovals.copy(), rw, repoManager)) {
        notesWithApprovals.parseAll();
        ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals2 =
            notesWithApprovals.buildApprovals();
        assertThat(approvals2).hasSize(1);
        assertThat(notesWithApprovals.comments).hasSize(1);
      }
    } finally {
      batch.close();
    }
  }

  @Test
  public void multipleUpdatesAcrossRefs() throws Exception {
    Change c1 = newChange();
    ChangeUpdate update1 = newUpdate(c1, changeOwner);
    update1.putApproval("Verified", (short) 1);

    Change c2 = newChange();
    ChangeUpdate update2 = newUpdate(c2, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    BatchMetaDataUpdate batch1 = null;
    BatchMetaDataUpdate batch2 = null;

    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    try {
      batch1 = update1.openUpdateInBatch(bru);
      batch1.write(update1, new CommitBuilder());
      batch1.commit();
      assertThat(repo.getRef(update1.getRefName())).isNull();

      batch2 = update2.openUpdateInBatch(bru);
      batch2.write(update2, new CommitBuilder());
      batch2.commit();
      assertThat(repo.getRef(update2.getRefName())).isNull();
    } finally {
      if (batch1 != null) {
        batch1.close();
      }
      if (batch2 != null) {
        batch2.close();
      }
    }

    List<ReceiveCommand> cmds = bru.getCommands();
    assertThat(cmds).hasSize(2);
    assertThat(cmds.get(0).getRefName()).isEqualTo(update1.getRefName());
    assertThat(cmds.get(1).getRefName()).isEqualTo(update2.getRefName());

    try (RevWalk rw = new RevWalk(repo)) {
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    }

    assertThat(cmds.get(0).getResult()).isEqualTo(ReceiveCommand.Result.OK);
    assertThat(cmds.get(1).getResult()).isEqualTo(ReceiveCommand.Result.OK);

    assertThat(repo.getRef(update1.getRefName())).isNotNull();
    assertThat(repo.getRef(update2.getRefName())).isNotNull();
  }

  @Test
  public void changeMessageOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Just a little code change.\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages.keySet()).containsExactly(ps1);

    ChangeMessage cm = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm.getMessage()).isEqualTo("Just a little code change.\n");
    assertThat(cm.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.getPatchSetId()).isEqualTo(ps1);
  }

  @Test
  public void noChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChangeMessages()).isEmpty();
  }

  @Test
  public void changeMessageWithTrailingDoubleNewline() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing trailing double newline\n"
        + "\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(1);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage()).isEqualTo("Testing trailing double newline\n" + "\n");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void changeMessageWithMultipleParagraphs() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(1);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage()).isEqualTo("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void changeMessagesMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("This is the change message for the first PS.");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);

    update.setChangeMessage("This is the change message for the second PS.");
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(2);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage())
        .isEqualTo("This is the change message for the first PS.");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());

    ChangeMessage cm2 = Iterables.getOnlyElement(changeMessages.get(ps2));
    assertThat(cm1.getPatchSetId()).isEqualTo(ps1);
    assertThat(cm2.getMessage())
        .isEqualTo("This is the change message for the second PS.");
    assertThat(cm2.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm2.getPatchSetId()).isEqualTo(ps2);
  }

  @Test
  public void changeMessageMultipleInOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("First change message.\n");
    update.commit();

    PatchSet.Id ps1 = c.currentPatchSetId();

    update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Second change message.\n");
    update.commit();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages.keySet()).hasSize(1);

    List<ChangeMessage> cm = changeMessages.get(ps1);
    assertThat(cm).hasSize(2);
    assertThat(cm.get(0).getMessage()).isEqualTo("First change message.\n");
    assertThat(cm.get(0).getAuthor())
        .isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.get(0).getPatchSetId()).isEqualTo(ps1);
    assertThat(cm.get(1).getMessage()).isEqualTo("Second change message.\n");
    assertThat(cm.get(1).getAuthor())
        .isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.get(1).getPatchSetId()).isEqualTo(ps1);
  }

  @Test
  public void patchLineCommentNotesFormatSide1() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String uuid3 = "uuid3";
    String message1 = "comment 1";
    String message2 = "comment 2";
    String message3 = "comment 3";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    Timestamp time3 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment1 = newPublishedComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range3 = new CommentRange(3, 0, 4, 1);
    PatchLineComment comment3 = newPublishedComment(psId, "file2",
        uuid3, range3, range3.getEndLine(), otherUser, null, time3, message3,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment3);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.getNoteMap().iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      assertThat(noteString).isEqualTo("Patch-set: 1\n"
          + "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + CommentsInNotesUtil.formatTime(serverIdent, time1) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid1\n"
          + "Bytes: 9\n"
          + "comment 1\n"
          + "\n"
          + "2:1-3:1\n"
          + CommentsInNotesUtil.formatTime(serverIdent, time2) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid2\n"
          + "Bytes: 9\n"
          + "comment 2\n"
          + "\n"
          + "File: file2\n"
          + "\n"
          + "3:0-4:1\n"
          + CommentsInNotesUtil.formatTime(serverIdent, time3) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid3\n"
          + "Bytes: 9\n"
          + "comment 3\n"
          + "\n");
    }
  }

  @Test
  public void patchLineCommentNotesFormatSide0() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String message1 = "comment 1";
    String message2 = "comment 2";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment1 = newPublishedComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.getNoteMap().iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      assertThat(noteString).isEqualTo("Base-for-patch-set: 1\n"
          + "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + CommentsInNotesUtil.formatTime(serverIdent, time1) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid1\n"
          + "Bytes: 9\n"
          + "comment 1\n"
          + "\n"
          + "2:1-3:1\n"
          + CommentsInNotesUtil.formatTime(serverIdent, time2) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid2\n"
          + "Bytes: 9\n"
          + "comment 2\n"
          + "\n");
    }
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFileBothSides()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    String messageForBase = "comment for base";
    String messageForPS = "comment for ps";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment commentForBase =
        newPublishedComment(psId, "filename", uuid1,
        range, range.getEndLine(), otherUser, null, now, messageForBase,
        (short) 0, rev1);
    update.setPatchSetId(psId);
    update.upsertComment(commentForBase);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment commentForPS =
        newPublishedComment(psId, "filename", uuid2,
        range, range.getEndLine(), otherUser, null, now, messageForPS,
        (short) 1, rev2);
    update.setPatchSetId(psId);
    update.upsertComment(commentForPS);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(
            new RevId(rev1), commentForBase,
            new RevId(rev2), commentForPS));
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFile() throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp timeForComment1 = TimeUtil.nowTs();
    Timestamp timeForComment2 = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedComment(psId, filename,
        uuid1, range, range.getEndLine(), otherUser, null, timeForComment1,
        "comment 1", side, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedComment(psId, filename,
        uuid2, range, range.getEndLine(), otherUser, null, timeForComment2,
        "comment 2", side, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(
          new RevId(rev), comment1,
          new RevId(rev), comment2)).inOrder();
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetMultipleFiles()
      throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename1 = "filename1";
    String filename2 = "filename2";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedComment(psId, filename1,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 1",
        side, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedComment(psId, filename2,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 2",
        side, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(
          new RevId(rev), comment1,
          new RevId(rev), comment2)).inOrder();
  }

  @Test
  public void patchLineCommentMultiplePatchsets() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedComment(ps1, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, rev1);
    update.setPatchSetId(ps1);
    update.upsertComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newPublishedComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, rev2);
    update.setPatchSetId(ps2);
    update.upsertComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(
          new RevId(rev1), comment1,
          new RevId(rev2), comment2));
  }

  @Test
  public void patchLineCommentSingleDraftToPublished() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newComment(ps1, filename, uuid, range,
        range.getEndLine(), otherUser, null, now, "comment on ps1", side,
        rev, Status.DRAFT);
    update.setPatchSetId(ps1);
    update.insertComment(comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment1));
    assertThat(notes.getComments()).isEmpty();

    comment1.setStatus(Status.PUBLISHED);
    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.updateComment(comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment1));
  }

  @Test
  public void patchLineCommentMultipleDraftsSameSidePublishOne()
      throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    short side = (short) 1;
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts on the same side of one patch set.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    PatchLineComment comment1 = newComment(psId, filename, uuid1,
        range1, range1.getEndLine(), otherUser, null, now, "comment on ps1",
        side, rev, Status.DRAFT);
    PatchLineComment comment2 = newComment(psId, filename, uuid2,
        range2, range2.getEndLine(), otherUser, null, now, "other on ps1",
        side, rev, Status.DRAFT);
    update.insertComment(comment1);
    update.insertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactly(
        ImmutableMultimap.of(
          new RevId(rev), comment1,
          new RevId(rev), comment2)).inOrder();
    assertThat(notes.getComments()).isEmpty();

    // Publish first draft.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    comment1.setStatus(Status.PUBLISHED);
    update.updateComment(comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment2));
    assertThat(notes.getComments()).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment1));
  }

  @Test
  public void patchLineCommentsMultipleDraftsBothSidesPublishAll()
      throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts, one on each side of the patchset.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    PatchLineComment baseComment = newComment(psId, filename, uuid1,
        range1, range1.getEndLine(), otherUser, null, now, "comment on base",
        (short) 0, rev1, Status.DRAFT);
    PatchLineComment psComment = newComment(psId, filename, uuid2,
        range2, range2.getEndLine(), otherUser, null, now, "comment on ps",
        (short) 1, rev2, Status.DRAFT);

    update.insertComment(baseComment);
    update.insertComment(psComment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactly(
        ImmutableMultimap.of(
            new RevId(rev1), baseComment,
            new RevId(rev2), psComment));
    assertThat(notes.getComments()).isEmpty();

    // Publish both comments.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);

    baseComment.setStatus(Status.PUBLISHED);
    psComment.setStatus(Status.PUBLISHED);
    update.updateComment(baseComment);
    update.updateComment(psComment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).containsExactly(
        ImmutableMultimap.of(
            new RevId(rev1), baseComment,
            new RevId(rev2), psComment));
  }

  @Test
  public void patchLineCommentsDeleteAllDrafts() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    ObjectId objId = ObjectId.fromString(rev);
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment = newComment(psId, filename, uuid, range,
        range.getEndLine(), otherUser, null, now, "comment on ps1", side,
        rev, Status.DRAFT);
    update.setPatchSetId(psId);
    update.upsertComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(1);
    assertThat(notes.getDraftCommentNotes().getNoteMap().contains(objId))
      .isTrue();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    update.setPatchSetId(psId);
    update.deleteComment(comment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getDraftCommentNotes().getNoteMap()).isNull();
  }

  @Test
  public void patchLineCommentsDeleteAllDraftsForOneRevision()
      throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    ObjectId objId1 = ObjectId.fromString(rev1);
    ObjectId objId2 = ObjectId.fromString(rev2);
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newComment(ps1, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, rev1, Status.DRAFT);
    update.setPatchSetId(ps1);
    update.upsertComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, rev2, Status.DRAFT);
    update.setPatchSetId(ps2);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(2);

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    update.setPatchSetId(ps2);
    update.deleteComment(comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(1);
    NoteMap noteMap = notes.getDraftCommentNotes().getNoteMap();
    assertThat(noteMap.contains(objId1)).isTrue();
    assertThat(noteMap.contains(objId2)).isFalse();
  }

  @Test
  public void fileComment() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment = newPublishedComment(
        psId, "filename", uuid, null, 0, otherUser, null, now, messageForBase,
        (short) 0, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment));
  }

  @Test
  public void patchLineCommentNoRange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment = newPublishedComment(
        psId, "filename", uuid, null, 1, otherUser, null, now, messageForBase,
        (short) 0, rev);
    update.setPatchSetId(psId);
    update.upsertComment(comment);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactly(
        ImmutableMultimap.of(new RevId(rev), comment));
  }

  @Test
  public void updateCommentsForMultipleRevisions() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(ps2);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newComment(ps1, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, rev1, Status.DRAFT);
    PatchLineComment comment2 = newComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, rev2, Status.DRAFT);
    update.upsertComment(comment1);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(2);
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps2);
    comment1.setStatus(Status.PUBLISHED);
    comment2.setStatus(Status.PUBLISHED);
    update.upsertComment(comment1);
    update.upsertComment(comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).hasSize(2);
  }
}
