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

import static com.google.gerrit.server.notedb.ReviewerState.CC;
import static com.google.gerrit.server.notedb.ReviewerState.REVIEWER;
import static com.google.gerrit.testutil.TestChanges.incrementPatchSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
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
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

import java.io.IOException;
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
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(1, psas.get(0).getAccountId().get());
    assertEquals("Code-Review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(1, psas.get(1).getAccountId().get());
    assertEquals("Verified", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(psas.get(0).getGranted(), psas.get(1).getGranted());
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
    assertEquals(2, notes.getApprovals().keySet().size());

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertEquals(ps1, psa1.getPatchSetId());
    assertEquals(1, psa1.getAccountId().get());
    assertEquals("Code-Review", psa1.getLabel());
    assertEquals((short) -1, psa1.getValue());
    assertEquals(truncate(after(c, 1000)), psa1.getGranted());

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertEquals(ps2, psa2.getPatchSetId());
    assertEquals(1, psa2.getAccountId().get());
    assertEquals("Code-Review", psa2.getLabel());
    assertEquals((short) +1, psa2.getValue());
    assertEquals(truncate(after(c, 2000)), psa2.getGranted());
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
    assertEquals("Code-Review", psa.getLabel());
    assertEquals((short) -1, psa.getValue());

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals("Code-Review", psa.getLabel());
    assertEquals((short) 1, psa.getValue());
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
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(1, psas.get(0).getAccountId().get());
    assertEquals("Code-Review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(2, psas.get(1).getAccountId().get());
    assertEquals("Code-Review", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(truncate(after(c, 2000)), psas.get(1).getGranted());
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
    assertEquals(1, psa.getAccountId().get());
    assertEquals("Not-For-Long", psa.getLabel());
    assertEquals((short) 1, psa.getValue());

    update = newUpdate(c, changeOwner);
    update.removeApproval("Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertTrue(notes.getApprovals().isEmpty());
  }

  @Test
  public void multipleReviewers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(1),
          REVIEWER, new Account.Id(2)),
        notes.getReviewers());
  }

  @Test
  public void reviewerTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(1),
          CC, new Account.Id(2)),
        notes.getReviewers());
  }

  @Test
  public void oneReviewerMultipleTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(2)),
        notes.getReviewers());

    update = newUpdate(c, otherUser);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          CC, new Account.Id(2)),
        notes.getReviewers());
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
    assertEquals(2, psas.size());
    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
    assertEquals(otherUser.getAccount().getId(), psas.get(1).getAccountId());

    update = newUpdate(c, changeOwner);
    update.removeReviewer(otherUser.getAccount().getId());
    update.commit();

    notes = newNotes(c);
    psas = notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(1, psas.size());
    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
  }

  @Test
  public void submitRecords() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)),
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<SubmitRecord> recs = notes.getSubmitRecords();
    assertEquals(2, recs.size());
    assertEquals(submitRecord("NOT_READY", null,
        submitLabel("Verified", "OK", changeOwner.getAccountId()),
        submitLabel("Code-Review", "NEED", null)), recs.get(0));
    assertEquals(submitRecord("NOT_READY", null,
        submitLabel("Verified", "OK", changeOwner.getAccountId()),
        submitLabel("Alternative-Code-Review", "NEED", null)), recs.get(1));
  }

  @Test
  public void latestSubmitRecordsOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");
    update.submit(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", otherUser.getAccountId()))));
    update.commit();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 2");
    update.submit(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId()))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId())),
        Iterables.getOnlyElement(notes.getSubmitRecords()));
  }

  @Test
  public void emptyChangeUpdate() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.commit();
    assertNull(update.getRevision());
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
    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertTrue(commit.getFullMessage().endsWith("Hashtags: tag1,tag2\n"));
    } finally {
      walk.release();
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
    assertEquals(hashtags, notes.getHashtags());
  }

  @Test
  public void emptyExceptSubject() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.setSubject("Create change");
    update.commit();
    assertNotNull(update.getRevision());
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
    assertEquals(2, psas.size());

    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
    assertEquals("Verified", psas.get(0).getLabel());
    assertEquals((short) 1, psas.get(0).getValue());

    assertEquals(otherUser.getAccount().getId(), psas.get(1).getAccountId());
    assertEquals("Code-Review", psas.get(1).getLabel());
    assertEquals((short) 2, psas.get(1).getValue());
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
    PatchLineComment comment1 = newPublishedPatchLineComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update1.setPatchSetId(psId);
    update1.upsertComment(comment1);
    update1.writeCommit(batch);
    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);
    update2.writeCommit(batch);

    RevWalk rw = new RevWalk(repo);
    try {
      batch.commit();
      bru.execute(rw, NullProgressMonitor.INSTANCE);

      ChangeNotes notes = newNotes(c);
      ObjectId tip = notes.getRevision();
      RevCommit commitWithApprovals = rw.parseCommit(tip);
      assertNotNull(commitWithApprovals);
      RevCommit commitWithComments = commitWithApprovals.getParent(0);
      assertNotNull(commitWithComments);

      ChangeNotesParser notesWithComments =
          new ChangeNotesParser(c, commitWithComments.copy(), rw, repoManager);
      notesWithComments.parseAll();
      ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals1 =
          notesWithComments.buildApprovals();
      assertEquals(0, approvals1.size());
      assertEquals(1, notesWithComments.commentsForBase.size());
      notesWithComments.close();

      ChangeNotesParser notesWithApprovals =
          new ChangeNotesParser(c, commitWithApprovals.copy(), rw, repoManager);
      notesWithApprovals.parseAll();
      ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals2 =
          notesWithApprovals.buildApprovals();
      assertEquals(1, approvals2.size());
      assertEquals(1, notesWithApprovals.commentsForBase.size());
      notesWithApprovals.close();
    } finally {
      batch.close();
      rw.release();
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
      assertNull(repo.getRef(update1.getRefName()));

      batch2 = update2.openUpdateInBatch(bru);
      batch2.write(update2, new CommitBuilder());
      batch2.commit();
      assertNull(repo.getRef(update2.getRefName()));
    } finally {
      if (batch1 != null) {
        batch1.close();
      }
      if (batch2 != null) {
        batch2.close();
      }
    }

    List<ReceiveCommand> cmds = bru.getCommands();
    assertEquals(2, cmds.size());
    assertEquals(update1.getRefName(), cmds.get(0).getRefName());
    assertEquals(update2.getRefName(), cmds.get(1).getRefName());

    RevWalk rw = new RevWalk(repo);
    try {
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } finally {
      rw.release();
    }

    assertEquals(ReceiveCommand.Result.OK, cmds.get(0).getResult());
    assertEquals(ReceiveCommand.Result.OK, cmds.get(1).getResult());

    assertNotNull(repo.getRef(update1.getRefName()));
    assertNotNull(repo.getRef(update2.getRefName()));
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
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Just a little code change.\n",
        cm.getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.getAuthor());
    assertEquals(ps1, cm.getPatchSetId());
  }

  @Test
  public void noChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(0, changeMessages.keySet().size());
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
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Testing trailing double newline\n" + "\n", cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm1.getAuthor());
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
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3", cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm1.getAuthor());
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
        notes.getChangeMessages();
    assertEquals(2, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("This is the change message for the first PS.",
        cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm1.getAuthor());

    ChangeMessage cm2 = Iterables.getOnlyElement(changeMessages.get(ps2));
    assertEquals(ps1, cm1.getPatchSetId());
    assertEquals("This is the change message for the second PS.",
        cm2.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm2.getAuthor());
    assertEquals(ps2, cm2.getPatchSetId());
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
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    List<ChangeMessage> cm = changeMessages.get(ps1);
    assertEquals(2, cm.size());
    assertEquals("First change message.\n",
        cm.get(0).getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.get(0).getAuthor());
    assertEquals(ps1, cm.get(0).getPatchSetId());
    assertEquals("Second change message.\n",
        cm.get(1).getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.get(1).getAuthor());
    assertEquals(ps1, cm.get(1).getPatchSetId());
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

    PatchLineComment comment1 = newPublishedPatchLineComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range3 = new CommentRange(3, 1, 4, 1);
    PatchLineComment comment3 = newPublishedPatchLineComment(psId, "file2",
        uuid3, range3, range3.getEndLine(), otherUser, null, time3, message3,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment3);
    update.commit();

    ChangeNotes notes = newNotes(c);

    RevWalk walk = new RevWalk(repo);
    ArrayList<Note> notesInTree =
        Lists.newArrayList(notes.getNoteMap().iterator());
    Note note = Iterables.getOnlyElement(notesInTree);

    byte[] bytes =
        walk.getObjectReader().open(
            note.getData(), Constants.OBJ_BLOB).getBytes();
    String noteString = new String(bytes, UTF_8);
    assertEquals("Patch-set: 1\n"
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
        + "3:1-4:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time3) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid3\n"
        + "Bytes: 9\n"
        + "comment 3\n"
        + "\n",
        noteString);
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

    PatchLineComment comment1 = newPublishedPatchLineComment(psId, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    RevWalk walk = new RevWalk(repo);
    ArrayList<Note> notesInTree =
        Lists.newArrayList(notes.getNoteMap().iterator());
    Note note = Iterables.getOnlyElement(notesInTree);

    byte[] bytes =
        walk.getObjectReader().open(
            note.getData(), Constants.OBJ_BLOB).getBytes();
    String noteString = new String(bytes, UTF_8);
    assertEquals("Base-for-patch-set: 1\n"
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
        + "\n",
        noteString);
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFileBothSides()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String messageForBase = "comment for base";
    String messageForPS = "comment for ps";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment commentForBase =
        newPublishedPatchLineComment(psId, "filename", uuid1,
        range, range.getEndLine(), otherUser, null, now, messageForBase,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(commentForBase);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment commentForPS =
        newPublishedPatchLineComment(psId, "filename", uuid2,
        range, range.getEndLine(), otherUser, null, now, messageForPS,
        (short) 1, "abcd4567abcd4567abcd4567abcd4567abcd4567");
    update.setPatchSetId(psId);
    update.upsertComment(commentForPS);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 1);
    assertEquals(commentsForPS.size(), 1);

    assertEquals(commentForBase,
        Iterables.getOnlyElement(commentsForBase.get(psId)));
    assertEquals(commentForPS,
        Iterables.getOnlyElement(commentsForPS.get(psId)));
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFile() throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp timeForComment1 = TimeUtil.nowTs();
    Timestamp timeForComment2 = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(psId, filename,
        uuid1, range, range.getEndLine(), otherUser, null, timeForComment1,
        "comment 1", side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, filename,
        uuid2, range, range.getEndLine(), otherUser, null, timeForComment2,
        "comment 2", side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 0);
    assertEquals(commentsForPS.size(), 2);

    ImmutableList<PatchLineComment> commentsForThisPS =
        (ImmutableList<PatchLineComment>) commentsForPS.get(psId);
    assertEquals(commentsForThisPS.size(), 2);
    PatchLineComment commentFromNotes1 = commentsForThisPS.get(0);
    PatchLineComment commentFromNotes2 = commentsForThisPS.get(1);

    assertEquals(comment1, commentFromNotes1);
    assertEquals(comment2, commentFromNotes2);
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetMultipleFiles()
      throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename1 = "filename1";
    String filename2 = "filename2";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(psId, filename1,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 1",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, filename2,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 2",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 0);
    assertEquals(commentsForPS.size(), 2);

    ImmutableList<PatchLineComment> commentsForThisPS =
        (ImmutableList<PatchLineComment>) commentsForPS.get(psId);
    assertEquals(commentsForThisPS.size(), 2);
    PatchLineComment commentFromNotes1 = commentsForThisPS.get(0);
    PatchLineComment commentFromNotes2 = commentsForThisPS.get(1);

    assertEquals(comment1, commentFromNotes1);
    assertEquals(comment2, commentFromNotes2);
  }

  @Test
  public void patchLineCommentMultiplePatchsets() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(ps1, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(ps1);
    update.upsertComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newPublishedPatchLineComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, "abcd4567abcd4567abcd4567abcd4567abcd4567");
    update.setPatchSetId(ps2);
    update.upsertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    LinkedListMultimap<PatchSet.Id, PatchLineComment> commentsForBase =
        LinkedListMultimap.create(notes.getBaseComments());
    LinkedListMultimap<PatchSet.Id, PatchLineComment> commentsForPS =
        LinkedListMultimap.create(notes.getPatchSetComments());
    assertEquals(commentsForBase.keys().size(), 0);
    assertEquals(commentsForPS.values().size(), 2);

    List<PatchLineComment> commentsForPS1 = commentsForPS.get(ps1);
    assertEquals(commentsForPS1.size(), 1);
    PatchLineComment commentFromPs1 = commentsForPS1.get(0);

    List<PatchLineComment> commentsForPS2 = commentsForPS.get(ps2);
    assertEquals(commentsForPS2.size(), 1);
    PatchLineComment commentFromPs2 = commentsForPS2.get(0);

    assertEquals(comment1, commentFromPs1);
    assertEquals(comment2, commentFromPs2);
  }

  @Test
  public void patchLineCommentSingleDraftToPublished() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPatchLineComment(ps1, filename, uuid,
        range, range.getEndLine(), otherUser, null, now, "comment on ps1", side,
        "abcd4567abcd4567abcd4567abcd4567abcd4567", Status.DRAFT);
    update.setPatchSetId(ps1);
    update.insertComment(comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(1, notes.getDraftPsComments(otherUserId).values().size());
    assertEquals(0, notes.getDraftBaseComments(otherUserId).values().size());

    comment1.setStatus(Status.PUBLISHED);
    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.updateComment(comment1);
    update.commit();

    notes = newNotes(c);

    assertTrue(notes.getDraftPsComments(otherUserId).values().isEmpty());
    assertTrue(notes.getDraftBaseComments(otherUserId).values().isEmpty());

    assertTrue(notes.getBaseComments().values().isEmpty());
    PatchLineComment commentFromNotes =
        Iterables.getOnlyElement(notes.getPatchSetComments().values());
    assertEquals(comment1, commentFromNotes);
  }

  @Test
  public void patchLineCommentMultipleDraftsSameSidePublishOne()
      throws OrmException, IOException {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    short side = (short) 1;
    Timestamp now = TimeUtil.nowTs();
    String commitSHA1 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts on the same side of one patch set.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    PatchLineComment comment1 = newPatchLineComment(psId, filename, uuid1,
        range1, range1.getEndLine(), otherUser, null, now, "comment on ps1",
        side, commitSHA1, Status.DRAFT);
    PatchLineComment comment2 = newPatchLineComment(psId, filename, uuid2,
        range2, range2.getEndLine(), otherUser, null, now, "other on ps1",
        side, commitSHA1, Status.DRAFT);
    update.insertComment(comment1);
    update.insertComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertTrue(notes.getDraftBaseComments(otherUserId).values().isEmpty());
    assertEquals(2, notes.getDraftPsComments(otherUserId).values().size());

    assertTrue(notes.getDraftPsComments(otherUserId).containsValue(comment1));
    assertTrue(notes.getDraftPsComments(otherUserId).containsValue(comment2));

    // Publish first draft.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    comment1.setStatus(Status.PUBLISHED);
    update.updateComment(comment1);
    update.commit();

    notes = newNotes(c);
    assertEquals(comment1,
        Iterables.getOnlyElement(notes.getPatchSetComments().get(psId)));
    assertEquals(comment2,
        Iterables.getOnlyElement(
            notes.getDraftPsComments(otherUserId).values()));

    assertTrue(notes.getBaseComments().values().isEmpty());
    assertTrue(notes.getDraftBaseComments(otherUserId).values().isEmpty());
  }

  @Test
  public void patchLineCommentsMultipleDraftsBothSidesPublishAll()
      throws OrmException, IOException {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    Timestamp now = TimeUtil.nowTs();
    String commitSHA1 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    String baseSHA1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts, one on each side of the patchset.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    PatchLineComment baseComment = newPatchLineComment(psId, filename, uuid1,
        range1, range1.getEndLine(), otherUser, null, now, "comment on base",
        (short) 0, baseSHA1, Status.DRAFT);
    PatchLineComment psComment = newPatchLineComment(psId, filename, uuid2,
        range2, range2.getEndLine(), otherUser, null, now, "comment on ps",
        (short) 1, commitSHA1, Status.DRAFT);

    update.insertComment(baseComment);
    update.insertComment(psComment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchLineComment baseDraftCommentFromNotes =
        Iterables.getOnlyElement(
            notes.getDraftBaseComments(otherUserId).values());
    PatchLineComment psDraftCommentFromNotes =
        Iterables.getOnlyElement(
            notes.getDraftPsComments(otherUserId).values());

    assertEquals(baseComment, baseDraftCommentFromNotes);
    assertEquals(psComment, psDraftCommentFromNotes);

    // Publish both comments.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);

    baseComment.setStatus(Status.PUBLISHED);
    psComment.setStatus(Status.PUBLISHED);
    update.updateComment(baseComment);
    update.updateComment(psComment);
    update.commit();

    notes = newNotes(c);

    PatchLineComment baseCommentFromNotes =
        Iterables.getOnlyElement(notes.getBaseComments().values());
    PatchLineComment psCommentFromNotes =
        Iterables.getOnlyElement(notes.getPatchSetComments().values());

    assertEquals(baseComment, baseCommentFromNotes);
    assertEquals(psComment, psCommentFromNotes);

    assertTrue(notes.getDraftBaseComments(otherUserId).values().isEmpty());
    assertTrue(notes.getDraftPsComments(otherUserId).values().isEmpty());
  }

  @Test
  public void fileComment() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment commentForBase =
        newPublishedPatchLineComment(psId, "filename", uuid,
        null, 0, otherUser, null, now, messageForBase,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(commentForBase);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPs =
        notes.getPatchSetComments();

    assertTrue(commentsForPs.isEmpty());
    assertEquals(commentForBase,
        Iterables.getOnlyElement(commentsForBase.get(psId)));
  }

  @Test
  public void patchLineCommentNoRange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment commentForBase =
        newPublishedPatchLineComment(psId, "filename", uuid,
        null, 1, otherUser, null, now, messageForBase,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.upsertComment(commentForBase);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPs =
        notes.getPatchSetComments();

    assertTrue(commentsForPs.isEmpty());
    assertEquals(commentForBase,
        Iterables.getOnlyElement(commentsForBase.get(psId)));
  }
}
