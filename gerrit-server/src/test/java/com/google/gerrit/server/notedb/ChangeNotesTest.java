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
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.reviewdb.client.RefNames.refsDraftComments;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.testutil.TestChanges.incrementPatchSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.testutil.TestChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ChangeNotesTest extends AbstractChangeNotesTest {
  @Inject
  private DraftCommentNotes.Factory draftNotesFactory;

  @Test
  public void tagChangeMessage() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("verification from jenkins");
    update.setTag(tag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    assertThat(notes.getChangeMessages()).hasSize(1);
    assertThat(notes.getChangeMessages().get(0).getTag()).isEqualTo(tag);
  }

  @Test
  public void tagInlineCommenrts() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    RevCommit commit = tr.commit().message("PS2").create();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putComment(newPublishedComment(c.currentPatchSetId(), "a.txt",
        "uuid1", new CommentRange(1, 2, 3, 4), 1, changeOwner, null,
        TimeUtil.nowTs(), "Comment", (short) 1, commit.name()));
    update.setTag(tag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<RevId, PatchLineComment> comments = notes.getComments();
    assertThat(comments).hasSize(1);
    assertThat(
        comments.entries().asList().get(0).getValue().getTag())
            .isEqualTo(tag);
  }

  @Test
  public void tagApprovals() throws Exception {
    String tag1 = "jenkins";
    String tag2 = "ip";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) -1);
    update.setTag(tag1);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.setTag(tag2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals =
        notes.getApprovals();
    assertThat(approvals).hasSize(2);
    assertThat(approvals.entries().asList().get(0).getValue().getTag())
        .isEqualTo(tag1);
    assertThat(approvals.entries().asList().get(1).getValue().getTag())
        .isEqualTo(tag2);
  }

  @Test
  public void multipleTags() throws Exception {
    String ipTag = "ip";
    String coverageTag = "coverage";
    String integrationTag = "integration";
    Change c = newChange();

    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) -1);
    update.setChangeMessage("integration verification");
    update.setTag(integrationTag);
    update.commit();

    RevCommit commit = tr.commit().message("PS2").create();
    update = newUpdate(c, changeOwner);
    update.putComment(newPublishedComment(c.currentPatchSetId(), "a.txt",
        "uuid1", new CommentRange(1, 2, 3, 4), 1, changeOwner, null,
        TimeUtil.nowTs(), "Comment", (short) 1, commit.name()));
    update.setChangeMessage("coverage verification");
    update.setTag(coverageTag);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setChangeMessage("ip clear");
    update.setTag(ipTag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals =
        notes.getApprovals();
    assertThat(approvals).hasSize(1);
    PatchSetApproval approval = approvals.entries().asList().get(0).getValue();
    assertThat(approval.getTag()).isEqualTo(integrationTag);
    assertThat(approval.getValue()).isEqualTo(-1);

    ImmutableListMultimap<RevId, PatchLineComment> comments =
        notes.getComments();
    assertThat(comments).hasSize(1);
    assertThat(comments.entries().asList().get(0).getValue().getTag())
        .isEqualTo(coverageTag);

    ImmutableList<ChangeMessage> messages = notes.getChangeMessages();
    assertThat(messages).hasSize(3);
    assertThat(messages.get(0).getTag()).isEqualTo(integrationTag);
    assertThat(messages.get(1).getTag()).isEqualTo(coverageTag);
    assertThat(messages.get(2).getTag()).isEqualTo(ipTag);
  }

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
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 2000)));

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
    assertThat(psa1.getGranted()).isEqualTo(truncate(after(c, 2000)));

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertThat(psa2.getPatchSetId()).isEqualTo(ps2);
    assertThat(psa2.getAccountId().get()).isEqualTo(1);
    assertThat(psa2.getLabel()).isEqualTo("Code-Review");
    assertThat(psa2.getValue()).isEqualTo((short) +1);
    assertThat(psa2.getGranted()).isEqualTo(truncate(after(c, 3000)));
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
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 2000)));

    assertThat(psas.get(1).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(1).getAccountId().get()).isEqualTo(2);
    assertThat(psas.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 1);
    assertThat(psas.get(1).getGranted()).isEqualTo(truncate(after(c, 3000)));
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
  public void removeOtherUsersApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    update.putApproval("Not-For-Long", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId()).isEqualTo(otherUserId);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 1);

    update = newUpdate(c, changeOwner);
    update.removeApprovalFor(otherUserId, "Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getApprovals()).isEmpty();

    // Add back approval on same label.
    update = newUpdate(c, otherUser);
    update.putApproval("Not-For-Long", (short) 2);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId()).isEqualTo(otherUserId);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 2);
  }

  @Test
  public void putOtherUsersApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.putApprovalFor(otherUser.getAccountId(), "Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> approvals = Ordering.natural().onResultOf(
        new Function<PatchSetApproval, Integer>() {
          @Override
          public Integer apply(PatchSetApproval in) {
            return in.getAccountId().get();
          }
        }).sortedCopy(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(approvals).hasSize(2);

    assertThat(approvals.get(0).getAccountId())
        .isEqualTo(changeOwner.getAccountId());
    assertThat(approvals.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(0).getValue()).isEqualTo((short) 1);

    assertThat(approvals.get(1).getAccountId())
        .isEqualTo(otherUser.getAccountId());
    assertThat(approvals.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(1).getValue()).isEqualTo((short) -1);
  }

  @Test
  public void multipleReviewers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers()).isEqualTo(ReviewerSet.fromTable(
        ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
            .put(REVIEWER, new Account.Id(1), ts)
            .put(REVIEWER, new Account.Id(2), ts)
            .build()));
  }

  @Test
  public void reviewerTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers()).isEqualTo(ReviewerSet.fromTable(
        ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
            .put(REVIEWER, new Account.Id(1), ts)
            .put(CC, new Account.Id(2), ts)
            .build()));
  }

  @Test
  public void oneReviewerMultipleTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers()).isEqualTo(ReviewerSet.fromTable(
        ImmutableTable.of(REVIEWER, new Account.Id(2), ts)));

    update = newUpdate(c, otherUser);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    notes = newNotes(c);
    ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers()).isEqualTo(ReviewerSet.fromTable(
        ImmutableTable.of(CC, new Account.Id(2), ts)));
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
    update.setSubjectForCommit("Submit patch set 1");

    update.merge("1-1453387607626-96fabc25", ImmutableList.of(
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
    assertThat(notes.getChange().getSubmissionId())
        .isEqualTo("1-1453387607626-96fabc25");
  }

  @Test
  public void latestSubmitRecordsOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubjectForCommit("Submit patch set 1");
    update.merge("1-1453387607626-96fabc25", ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", otherUser.getAccountId()))));
    update.commit();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.setSubjectForCommit("Submit patch set 2");
    update.merge("1-1453387901516-5d1e2450", ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId()))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getSubmitRecords()).containsExactly(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId())));
    assertThat(notes.getChange().getSubmissionId())
        .isEqualTo("1-1453387901516-5d1e2450");
  }

  @Test
  public void emptyChangeUpdate() throws Exception {
    Change c = newChange();
    Ref initial = repo.exactRef(changeMetaRef(c.getId()));
    assertThat(initial).isNotNull();

    // Empty update doesn't create a new commit.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.commit();
    assertThat(update.getResult()).isNull();

    Ref updated = repo.exactRef(changeMetaRef(c.getId()));
    assertThat(updated.getObjectId()).isEqualTo(initial.getObjectId());
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
      RevCommit commit = walk.parseCommit(update.getResult());
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

    // initially topic is not set
    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set topic
    String topic = "myTopic";
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting empty string
    update = newUpdate(c, changeOwner);
    update.setTopic("");
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set other topic
    topic = "otherTopic";
    update = newUpdate(c, changeOwner);
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting null
    update = newUpdate(c, changeOwner);
    update.setTopic(null);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();
  }

  @Test
  public void changeIdChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getKey()).isEqualTo(c.getKey());

    // An update doesn't affect the Change-Id
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(notes.getChange().getKey()).isEqualTo(c.getKey());

    // Trying to set another Change-Id fails
    String otherChangeId = "I577fb248e474018276351785930358ec0450e9f7";
    update = newUpdate(c, changeOwner);
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("The Change-Id was already set to " + c.getKey()
        + ", so we cannot set this Change-Id: " + otherChangeId);
    update.setChangeId(otherChangeId);
  }

  @Test
  public void branchChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    Branch.NameKey expectedBranch =
        new Branch.NameKey(project, "refs/heads/master");
    assertThat(notes.getChange().getDest()).isEqualTo(expectedBranch);

    // An update doesn't affect the branch
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getDest()).isEqualTo(expectedBranch);

    // Set another branch
    String otherBranch = "refs/heads/stable";
    update = newUpdate(c, changeOwner);
    update.setBranch(otherBranch);
    update.commit();
    assertThat(newNotes(c).getChange().getDest()).isEqualTo(
        new Branch.NameKey(project, otherBranch));
  }

  @Test
  public void ownerChangeNotes() throws Exception {
    Change c = newChange();

    assertThat(newNotes(c).getChange().getOwner()).isEqualTo(
        changeOwner.getAccountId());

    // An update doesn't affect the owner
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getOwner()).isEqualTo(
        changeOwner.getAccountId());
  }

  @Test
  public void createdOnChangeNotes() throws Exception {
    Change c = newChange();

    Timestamp createdOn = newNotes(c).getChange().getCreatedOn();
    assertThat(createdOn).isNotNull();

    // An update doesn't affect the createdOn timestamp.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getCreatedOn()).isEqualTo(createdOn);
  }

  @Test
  public void lastUpdatedOnChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    Timestamp ts1 = notes.getChange().getLastUpdatedOn();
    assertThat(ts1).isEqualTo(notes.getChange().getCreatedOn());

    // Various kinds of updates that update the timestamp.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    Timestamp ts2 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts2).isGreaterThan(ts1);

    update = newUpdate(c, changeOwner);
    update.setChangeMessage("Some message");
    update.commit();
    Timestamp ts3 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts3).isGreaterThan(ts2);

    update = newUpdate(c, changeOwner);
    update.setHashtags(ImmutableSet.of("foo"));
    update.commit();
    Timestamp ts4 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts4).isGreaterThan(ts3);

    incrementPatchSet(c);
    RevCommit commit = tr.commit().message("PS2").create();
    update = newUpdate(c, changeOwner);
    update.setCommit(rw, commit);
    update.commit();
    Timestamp ts5 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts5).isGreaterThan(ts4);

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();
    Timestamp ts6 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts6).isGreaterThan(ts5);

    update = newUpdate(c, changeOwner);
    update.setStatus(Change.Status.ABANDONED);
    update.commit();
    Timestamp ts7 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts7).isGreaterThan(ts6);

    update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccountId(), ReviewerStateInternal.REVIEWER);
    update.commit();
    Timestamp ts8 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts8).isGreaterThan(ts7);

    update = newUpdate(c, changeOwner);
    update.setGroups(ImmutableList.of("a", "b"));
    update.commit();
    Timestamp ts9 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts9).isGreaterThan(ts8);

    // Finish off by merging the change.
    update = newUpdate(c, changeOwner);
    update.merge("1-1453387607626-96fabc25", ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();
    Timestamp ts10 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts10).isGreaterThan(ts9);
  }

  @Test
  public void subjectLeadingWhitespaceChangeNotes() throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId());
    String trimmedSubj = c.getSubject();
    c.setCurrentPatchSet(c.currentPatchSetId(), "  " + trimmedSubj,
        c.getOriginalSubject());
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeId(c.getKey().get());
    update.setBranch(c.getDest().get());
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getSubject()).isEqualTo(trimmedSubj);

    String tabSubj = "\t\t" + trimmedSubj;

    c = TestChanges.newChange(project, changeOwner.getAccountId());
    c.setCurrentPatchSet(c.currentPatchSetId(), tabSubj,
        c.getOriginalSubject());
    update = newUpdate(c, changeOwner);
    update.setChangeId(c.getKey().get());
    update.setBranch(c.getDest().get());
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getChange().getSubject()).isEqualTo(tabSubj);
  }

  @Test
  public void commitChangeNotesUnique() throws Exception {
    // PatchSetId -> RevId must be a one to one mapping
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    PatchSet ps = notes.getCurrentPatchSet();
    assertThat(ps).isNotNull();

    // new revId for the same patch set, ps1
    ChangeUpdate update = newUpdate(c, changeOwner);
    RevCommit commit = tr.commit().message("PS1 again").create();
    update.setCommit(rw, commit);
    update.commit();

    try {
      notes = newNotes(c);
      fail("Expected IOException");
    } catch (OrmException e) {
      assertCause(e, ConfigInvalidException.class,
          "Multiple revisions parsed for patch set 1:"
              + " RevId{" + commit.name() + "} and " + ps.getRevision().get());
    }
  }

  @Test
  public void patchSetChangeNotes() throws Exception {
    Change c = newChange();

    // ps1 created by newChange()
    ChangeNotes notes = newNotes(c);
    PatchSet ps1 = notes.getCurrentPatchSet();
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps1.getId());
    assertThat(notes.getChange().getSubject()).isEqualTo("Change subject");
    assertThat(notes.getChange().getOriginalSubject())
        .isEqualTo("Change subject");
    assertThat(ps1.getId()).isEqualTo(new PatchSet.Id(c.getId(), 1));
    assertThat(ps1.getUploader()).isEqualTo(changeOwner.getAccountId());

    // ps2 by other user
    incrementPatchSet(c);
    RevCommit commit = tr.commit().message("PS2").create();
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setCommit(rw, commit);
    update.commit();
    notes = newNotes(c);
    PatchSet ps2 = notes.getCurrentPatchSet();
    assertThat(ps2.getId()).isEqualTo(new PatchSet.Id(c.getId(), 2));
    assertThat(notes.getChange().getSubject()).isEqualTo("PS2");
    assertThat(notes.getChange().getOriginalSubject())
        .isEqualTo("Change subject");
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps2.getId());
    assertThat(ps2.getRevision().get()).isNotEqualTo(ps1.getRevision());
    assertThat(ps2.getRevision().get()).isEqualTo(commit.name());
    assertThat(ps2.getUploader()).isEqualTo(otherUser.getAccountId());
    assertThat(ps2.getCreatedOn()).isEqualTo(update.getWhen());

    // comment on ps1, current patch set is still ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetId(ps1.getId());
    update.setChangeMessage("Comment on old patch set.");
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps2.getId());
  }

  @Test
  public void patchSetStates() throws Exception {
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();

    // ps2
    incrementPatchSet(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    RevCommit commit = tr.commit().message("PS2").create();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setCommit(rw, commit);
    update.setPatchSetState(PatchSetState.DRAFT);
    update.putApproval("Code-Review", (short) 1);
    update.setChangeMessage("This is a message");
    update.putComment(newPublishedComment(c.currentPatchSetId(), "a.txt",
        "uuid1", new CommentRange(1, 2, 3, 4), 1, changeOwner, null,
        TimeUtil.nowTs(), "Comment", (short) 1, commit.name()));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).isDraft()).isTrue();
    assertThat(notes.getPatchSets().keySet()).containsExactly(psId1, psId2);
    assertThat(notes.getApprovals()).isNotEmpty();
    assertThat(notes.getChangeMessagesByPatchSet()).isNotEmpty();
    assertThat(notes.getChangeMessages()).isNotEmpty();
    assertThat(notes.getComments()).isNotEmpty();

    // publish ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetState(PatchSetState.PUBLISHED);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).isDraft()).isFalse();

    // delete ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetState(PatchSetState.DELETED);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getPatchSets().keySet()).containsExactly(psId1);
    assertThat(notes.getApprovals()).isEmpty();
    assertThat(notes.getChangeMessagesByPatchSet()).isEmpty();
    assertThat(notes.getChangeMessages()).isEmpty();
    assertThat(notes.getComments()).isEmpty();
  }

  @Test
  public void patchSetGroups() throws Exception {
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId1).getGroups()).isEmpty();

    // ps1
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setGroups(ImmutableList.of("a", "b"));
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId1).getGroups())
      .containsExactly("a", "b").inOrder();

    // ps2
    incrementPatchSet(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    update = newUpdate(c, changeOwner);
    update.setCommit(rw, tr.commit().message("PS2").create());
    update.setGroups(ImmutableList.of("d"));
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).getGroups())
      .containsExactly("d");
    assertThat(notes.getPatchSets().get(psId1).getGroups())
      .containsExactly("a", "b").inOrder();
  }

  @Test
  public void pushCertificate() throws Exception {
    String pushCert = "certificate version 0.1\n"
      + "pusher This is not a real push cert\n"
      + "-----BEGIN PGP SIGNATURE-----\n"
      + "Version: GnuPG v1\n"
      + "\n"
      + "Nor is this a real signature.\n"
      + "-----END PGP SIGNATURE-----\n";

    // ps2 with push cert
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();
    incrementPatchSet(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPatchSetId(psId2);
    RevCommit commit = tr.commit().message("PS2").create();
    update.setCommit(rw, commit, pushCert);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(readNote(notes, commit)).isEqualTo(pushCert);
    Map<PatchSet.Id, PatchSet> patchSets = notes.getPatchSets();
    assertThat(patchSets.get(psId1).getPushCertificate()).isNull();
    assertThat(patchSets.get(psId2).getPushCertificate()).isEqualTo(pushCert);
    assertThat(notes.getComments()).isEmpty();

    // comment on ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetId(psId2);
    Timestamp ts = TimeUtil.nowTs();
    update.putComment(newPublishedComment(psId2, "a.txt",
        "uuid1", new CommentRange(1, 2, 3, 4), 1, changeOwner, null, ts,
        "Comment", (short) 1, commit.name()));
    update.commit();

    notes = newNotes(c);
    assertThat(readNote(notes, commit)).isEqualTo(
        pushCert
        + "Revision: " + commit.name() + "\n"
        + "Patch-set: 2\n"
        + "File: a.txt\n"
        + "\n"
        + "1:2-3:4\n"
        + ChangeNoteUtil.formatTime(serverIdent, ts) + "\n"
        + "Author: Change Owner <1@gerrit>\n"
        + "UUID: uuid1\n"
        + "Bytes: 7\n"
        + "Comment\n"
        + "\n");
    patchSets = notes.getPatchSets();
    assertThat(patchSets.get(psId1).getPushCertificate()).isNull();
    assertThat(patchSets.get(psId2).getPushCertificate()).isEqualTo(pushCert);
    assertThat(notes.getComments()).isNotEmpty();
  }

  @Test
  public void emptyExceptSubject() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.setSubjectForCommit("Create change");
    assertThat(update.commit()).isNotNull();
  }

  @Test
  public void multipleUpdatesInManager() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, changeOwner);
    update1.putApproval("Verified", (short) 1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    try (NoteDbUpdateManager updateManager =
        updateManagerFactory.create(project)) {
      updateManager.add(update1);
      updateManager.add(update2);
      updateManager.execute();
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
    RevCommit tipCommit;
    try (NoteDbUpdateManager updateManager =
        updateManagerFactory.create(project)) {
      PatchLineComment comment1 = newPublishedComment(psId, "file1",
          uuid1, range1, range1.getEndLine(), otherUser, null, time1, message1,
          (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
      update1.setPatchSetId(psId);
      update1.putComment(comment1);
      updateManager.add(update1);

      ChangeUpdate update2 = newUpdate(c, otherUser);
      update2.putApproval("Code-Review", (short) 2);
      updateManager.add(update2);

      updateManager.execute();
    }

    ChangeNotes notes = newNotes(c);
    ObjectId tip = notes.getRevision();
    tipCommit = rw.parseCommit(tip);

    RevCommit commitWithApprovals = tipCommit;
    assertThat(commitWithApprovals).isNotNull();
    RevCommit commitWithComments = commitWithApprovals.getParent(0);
    assertThat(commitWithComments).isNotNull();

    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo)) {
      ChangeNotesParser notesWithComments = new ChangeNotesParser(
          c.getId(), commitWithComments.copy(), rw, noteUtil, args.metrics);
      ChangeNotesState state = notesWithComments.parseAll();
      assertThat(state.approvals()).isEmpty();
      assertThat(state.publishedComments()).hasSize(1);
    }

    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo)) {
      ChangeNotesParser notesWithApprovals = new ChangeNotesParser(c.getId(),
          commitWithApprovals.copy(), rw, noteUtil, args.metrics);
      ChangeNotesState state = notesWithApprovals.parseAll();
      assertThat(state.approvals()).hasSize(1);
      assertThat(state.publishedComments()).hasSize(1);
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

    Ref initial1 = repo.exactRef(update1.getRefName());
    assertThat(initial1).isNotNull();
    Ref initial2 = repo.exactRef(update2.getRefName());
    assertThat(initial2).isNotNull();

    try (NoteDbUpdateManager updateManager =
        updateManagerFactory.create(project)) {
      updateManager.add(update1);
      updateManager.add(update2);
      updateManager.execute();
    }

    Ref ref1 = repo.exactRef(update1.getRefName());
    assertThat(ref1.getObjectId()).isEqualTo(update1.getResult());
    assertThat(ref1.getObjectId()).isNotEqualTo(initial1.getObjectId());
    Ref ref2 = repo.exactRef(update2.getRefName());
    assertThat(ref2.getObjectId()).isEqualTo(update2.getResult());
    assertThat(ref2.getObjectId()).isNotEqualTo(initial2.getObjectId());

    PatchSetApproval approval1 = newNotes(c1).getApprovals()
        .get(c1.currentPatchSetId()).iterator().next();
    assertThat(approval1.getLabel()).isEqualTo("Verified");

    PatchSetApproval approval2 = newNotes(c2).getApprovals()
        .get(c2.currentPatchSetId()).iterator().next();
    assertThat(approval2.getLabel()).isEqualTo("Code-Review");
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
  public void patchLineCommentsFileComment() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");

    PatchLineComment comment = newPublishedComment(psId, "file1",
        "uuid", null, 0, otherUser, null,
        TimeUtil.nowTs(), "message", (short) 1, revId.get());
    update.setPatchSetId(psId);
    update.putComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments())
        .isEqualTo(ImmutableMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentsZeroColumns() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 0, 2, 0);

    PatchLineComment comment = newPublishedComment(psId, "file1",
        "uuid", range, range.getEndLine(), otherUser, null,
        TimeUtil.nowTs(), "message", (short) 1, revId.get());
    update.setPatchSetId(psId);
    update.putComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments())
        .isEqualTo(ImmutableMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentZeroRange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(0, 0, 0, 0);

    PatchLineComment comment = newPublishedComment(psId, "file",
        "uuid", range, range.getEndLine(), otherUser, null,
        TimeUtil.nowTs(), "message", (short) 1, revId.get());
    update.setPatchSetId(psId);
    update.putComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments())
        .isEqualTo(ImmutableMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentEmptyFilename() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 2, 3, 4);

    PatchLineComment comment = newPublishedComment(psId, "",
        "uuid", range, range.getEndLine(), otherUser, null,
        TimeUtil.nowTs(), "message", (short) 1, revId.get());
    update.setPatchSetId(psId);
    update.putComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments())
        .isEqualTo(ImmutableMultimap.of(revId, comment));
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
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range3 = new CommentRange(3, 0, 4, 1);
    PatchLineComment comment3 = newPublishedComment(psId, "file2",
        uuid3, range3, range3.getEndLine(), otherUser, null, time3, message3,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment3);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      assertThat(noteString).isEqualTo(
          "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "Patch-set: 1\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + ChangeNoteUtil.formatTime(serverIdent, time1) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid1\n"
          + "Bytes: 9\n"
          + "comment 1\n"
          + "\n"
          + "2:1-3:1\n"
          + ChangeNoteUtil.formatTime(serverIdent, time2) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid2\n"
          + "Bytes: 9\n"
          + "comment 2\n"
          + "\n"
          + "File: file2\n"
          + "\n"
          + "3:0-4:1\n"
          + ChangeNoteUtil.formatTime(serverIdent, time3) + "\n"
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
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedComment(psId, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      assertThat(noteString).isEqualTo(
          "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "Base-for-patch-set: 1\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + ChangeNoteUtil.formatTime(serverIdent, time1) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid1\n"
          + "Bytes: 9\n"
          + "comment 1\n"
          + "\n"
          + "2:1-3:1\n"
          + ChangeNoteUtil.formatTime(serverIdent, time2) + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid2\n"
          + "Bytes: 9\n"
          + "comment 2\n"
          + "\n");
    }
  }

  @Test
  public void patchLineCommentNotesFormatMultiplePatchSetsSameRevId()
      throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String uuid3 = "uuid3";
    String message1 = "comment 1";
    String message2 = "comment 2";
    String message3 = "comment 3";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    Timestamp time = TimeUtil.nowTs();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");

    PatchSet.Id psId1 = c.currentPatchSetId();
    PatchSet.Id psId2 = new PatchSet.Id(c.getId(), psId1.get() + 1);

    PatchLineComment comment1 = newPublishedComment(psId1, "file1",
        uuid1, range1, range1.getEndLine(), otherUser, null, time, message1,
        (short) 0, revId.get());
    PatchLineComment comment2 = newPublishedComment(psId1, "file1",
        uuid2, range2, range2.getEndLine(), otherUser, null, time, message2,
        (short) 0, revId.get());
    PatchLineComment comment3 = newPublishedComment(psId2, "file1",
        uuid3, range1, range1.getEndLine(), otherUser, null, time, message3,
        (short) 0, revId.get());

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId2);
    update.putComment(comment3);
    update.putComment(comment2);
    update.putComment(comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      String timeStr = ChangeNoteUtil.formatTime(serverIdent, time);
      assertThat(noteString).isEqualTo(
          "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "Base-for-patch-set: 1\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + timeStr + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid1\n"
          + "Bytes: 9\n"
          + "comment 1\n"
          + "\n"
          + "2:1-3:1\n"
          + timeStr + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid2\n"
          + "Bytes: 9\n"
          + "comment 2\n"
          + "\n"
          + "Base-for-patch-set: 2\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + timeStr + "\n"
          + "Author: Other Account <2@gerrit>\n"
          + "UUID: uuid3\n"
          + "Bytes: 9\n"
          + "comment 3\n"
          + "\n");
    }

    assertThat(notes.getComments()).isEqualTo(
        ImmutableMultimap.of(
            revId, comment1,
            revId, comment2,
            revId, comment3));
  }

  @Test
  public void patchLineCommentNotesFormatWeirdUser() throws Exception {
    Account account = new Account(new Account.Id(3), TimeUtil.nowTs());
    account.setFullName("Weird\n\u0002<User>\n");
    account.setPreferredEmail(" we\r\nird@ex>ample<.com");
    accountCache.put(account);
    IdentifiedUser user = userFactory.create(account.getId());

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, user);
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp time = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment = newPublishedComment(psId, "file1",
        uuid, range, range.getEndLine(), user, null, time, "comment",
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree =
          Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes =
          walk.getObjectReader().open(
              note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      String timeStr = ChangeNoteUtil.formatTime(serverIdent, time);
      assertThat(noteString).isEqualTo(
          "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
          + "Patch-set: 1\n"
          + "File: file1\n"
          + "\n"
          + "1:1-2:1\n"
          + timeStr + "\n"
          + "Author: Weird\u0002User <3@gerrit>\n"
          + "UUID: uuid\n"
          + "Bytes: 7\n"
          + "comment\n"
          + "\n");
    }

    assertThat(notes.getComments())
        .isEqualTo(ImmutableMultimap.of(comment.getRevId(), comment));
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
    update.putComment(commentForBase);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment commentForPS =
        newPublishedComment(psId, "filename", uuid2,
        range, range.getEndLine(), otherUser, null, now, messageForPS,
        (short) 1, rev2);
    update.setPatchSetId(psId);
    update.putComment(commentForPS);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedComment(psId, filename,
        uuid2, range, range.getEndLine(), otherUser, null, timeForComment2,
        "comment 2", side, rev);
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedComment(psId, filename2,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 2",
        side, rev);
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newPublishedComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, rev2);
    update.setPatchSetId(ps2);
    update.putComment(comment2);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactlyEntriesIn(
        ImmutableMultimap.of(new RevId(rev), comment1));
    assertThat(notes.getComments()).isEmpty();

    comment1.setStatus(Status.PUBLISHED);
    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.putComment(comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment1);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactlyEntriesIn(
        ImmutableMultimap.of(
          new RevId(rev), comment1,
          new RevId(rev), comment2)).inOrder();
    assertThat(notes.getComments()).isEmpty();

    // Publish first draft.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    comment1.setStatus(Status.PUBLISHED);
    update.putComment(comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactlyEntriesIn(
        ImmutableMultimap.of(new RevId(rev), comment2));
    assertThat(notes.getComments()).containsExactlyEntriesIn(
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

    update.putComment(baseComment);
    update.putComment(psComment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).containsExactlyEntriesIn(
        ImmutableMultimap.of(
            new RevId(rev1), baseComment,
            new RevId(rev2), psComment));
    assertThat(notes.getComments()).isEmpty();

    // Publish both comments.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);

    baseComment.setStatus(Status.PUBLISHED);
    psComment.setStatus(Status.PUBLISHED);
    update.putComment(baseComment);
    update.putComment(psComment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment);
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
    update.putComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, rev2, Status.DRAFT);
    update.setPatchSetId(ps2);
    update.putComment(comment2);
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
  public void addingPublishedCommentDoesNotCreateNoOpCommitOnEmptyDraftRef()
      throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment = newComment(ps1, filename, uuid, range,
        range.getEndLine(), otherUser, null, now, "comment on ps1", side,
        rev, Status.PUBLISHED);
    update.putComment(comment);
    update.commit();

    assertThat(repo.exactRef(changeMetaRef(c.getId()))).isNotNull();
    String draftRef = refsDraftComments(c.getId(), otherUser.getAccountId());
    assertThat(exactRefAllUsers(draftRef)).isNull();
  }

  @Test
  public void addingPublishedCommentDoesNotCreateNoOpCommitOnNonEmptyDraftRef()
      throws Exception {
    Change c = newChange();
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment draft = newComment(ps1, filename, "uuid1", range,
        range.getEndLine(), otherUser, null, now, "draft comment on ps1", side,
        rev, Status.DRAFT);
    update.putComment(draft);
    update.commit();

    String draftRef = refsDraftComments(c.getId(), otherUser.getAccountId());
    ObjectId old = exactRefAllUsers(draftRef);
    assertThat(old).isNotNull();

    update = newUpdate(c, otherUser);
    PatchLineComment pub = newComment(ps1, filename, "uuid2", range,
        range.getEndLine(), otherUser, null, now, "comment on ps1", side,
        rev, Status.PUBLISHED);
    update.putComment(pub);
    update.commit();

    assertThat(exactRefAllUsers(draftRef)).isEqualTo(old);
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
    update.putComment(comment);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
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
    update.putComment(comment);
    update.commit();

    assertThat(newNotes(c).getComments()).containsExactlyEntriesIn(
        ImmutableMultimap.of(new RevId(rev), comment));
  }

  @Test
  public void putCommentsForMultipleRevisions() throws Exception {
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
    update.putComment(comment1);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(2);
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps2);
    comment1.setStatus(Status.PUBLISHED);
    comment2.setStatus(Status.PUBLISHED);
    update.putComment(comment1);
    update.putComment(comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).hasSize(2);
  }

  @Test
  public void publishSubsetOfCommentsOnRevision() throws Exception {
    Change c = newChange();
    RevId rev1 = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newComment(ps1, "file1",
        "uuid1", range, range.getEndLine(), otherUser, null, now, "comment1",
        side, rev1.get(), Status.DRAFT);
    PatchLineComment comment2 = newComment(ps1, "file2",
        "uuid2", range, range.getEndLine(), otherUser, null, now, "comment2",
        side, rev1.get(), Status.DRAFT);
    update.putComment(comment1);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1))
        .containsExactly(comment1, comment2);
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    comment2.setStatus(Status.PUBLISHED);
    update.putComment(comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1))
        .containsExactly(comment1);
    assertThat(notes.getComments().get(rev1)).containsExactly(comment2);
  }

  @Test
  public void updateWithServerIdent() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, internalUser);
    update.setChangeMessage("A message.");
    update.commit();

    ChangeMessage msg = Iterables.getLast(newNotes(c).getChangeMessages());
    assertThat(msg.getMessage()).isEqualTo("A message.");
    assertThat(msg.getAuthor()).isNull();

    update = newUpdate(c, internalUser);
    exception.expect(IllegalStateException.class);
    update.putApproval("Code-Review", (short) 1);
  }

  @Test
  public void filterOutAndFixUpZombieDraftComments() throws Exception {
    Change c = newChange();
    RevId rev1 = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newComment(ps1, "file1",
        "uuid1", range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, rev1.get(), Status.DRAFT);
    PatchLineComment comment2 = newComment(ps1, "file2",
        "uuid2", range, range.getEndLine(), otherUser, null, now, "another comment",
        side, rev1.get(), Status.DRAFT);
    update.putComment(comment1);
    update.putComment(comment2);
    update.commit();

    String refName = refsDraftComments(c.getId(), otherUserId);
    ObjectId oldDraftId = exactRefAllUsers(refName);

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    comment2.setStatus(Status.PUBLISHED);
    update.putComment(comment2);
    update.commit();
    assertThat(exactRefAllUsers(refName)).isNotNull();
    assertThat(exactRefAllUsers(refName)).isNotEqualTo(oldDraftId);

    // Re-add draft version of comment2 back to draft ref without updating
    // change ref. Simulates the case where deleting the draft failed
    // non-atomically after adding the published comment succeeded.
    ChangeDraftUpdate draftUpdate =
        newUpdate(c, otherUser).createDraftUpdateIfNull();
    comment2.setStatus(Status.DRAFT);
    draftUpdate.putComment(comment2);
    try (NoteDbUpdateManager manager =
        updateManagerFactory.create(c.getProject())) {
      manager.add(draftUpdate);
      manager.execute();
    }

    // Looking at drafts directly shows the zombie comment.
    DraftCommentNotes draftNotes = draftNotesFactory.create(c, otherUserId);
    assertThat(draftNotes.load().getComments().get(rev1))
        .containsExactly(comment1, comment2);

    comment2.setStatus(Status.PUBLISHED); // Reset for later assertions.

    // Zombie comment is filtered out of drafts via ChangeNotes.
    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1))
        .containsExactly(comment1);
    assertThat(notes.getComments().get(rev1))
        .containsExactly(comment2);

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    comment1.setStatus(Status.PUBLISHED);
    update.putComment(comment1);
    update.commit();

    // Updating an unrelated comment causes the zombie comment to get fixed up.
    assertThat(exactRefAllUsers(refName)).isNull();
  }

  @Test
  public void updateCommentsInSequentialUpdates() throws Exception {
    Change c = newChange();
    CommentRange range = new CommentRange(1, 1, 2, 1);
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";

    ChangeUpdate update1 = newUpdate(c, otherUser);
    PatchLineComment comment1 = newComment(c.currentPatchSetId(), "filename",
        "uuid1", range, range.getEndLine(), otherUser, null,
        new Timestamp(update1.getWhen().getTime()), "comment 1", (short) 1, rev,
        Status.PUBLISHED);
    update1.putComment(comment1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    PatchLineComment comment2 = newComment(c.currentPatchSetId(), "filename",
        "uuid2", range, range.getEndLine(), otherUser, null,
        new Timestamp(update2.getWhen().getTime()), "comment 2", (short) 1, rev,
        Status.PUBLISHED);
    update2.putComment(comment2);

    try (NoteDbUpdateManager manager = updateManagerFactory.create(project)) {
      manager.add(update1);
      manager.add(update2);
      manager.execute();
    }

    ChangeNotes notes = newNotes(c);
    List<PatchLineComment> comments = notes.getComments().get(new RevId(rev));
    assertThat(comments).hasSize(2);
    assertThat(comments.get(0).getMessage()).isEqualTo("comment 1");
    assertThat(comments.get(1).getMessage()).isEqualTo("comment 2");
  }

  private String readNote(ChangeNotes notes, ObjectId noteId) throws Exception {
    ObjectId dataId = notes.revisionNoteMap.noteMap.getNote(noteId).getData();
    return new String(
        rw.getObjectReader().open(dataId, OBJ_BLOB).getCachedBytes(), UTF_8);
  }

  private ObjectId exactRefAllUsers(String refName) throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      Ref ref = allUsersRepo.exactRef(refName);
      return ref != null ? ref.getObjectId() : null;
    }
  }

  private void assertCause(Throwable e,
      Class<? extends Throwable> expectedClass, String expectedMsg) {
    Throwable cause = null;
    for (Throwable t : Throwables.getCausalChain(e)) {
      if (expectedClass.isAssignableFrom(t.getClass())) {
        cause = t;
        break;
      }
    }
    assertThat(cause)
        .named(expectedClass.getSimpleName() + " in causal chain of:\n"
            + Throwables.getStackTraceAsString(e))
        .isNotNull();
    assertThat(cause.getMessage()).isEqualTo(expectedMsg);
  }
}
