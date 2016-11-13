// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.TimeUtil.roundToSecond;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.NOTE_DB;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.REVIEW_DB;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeBundleTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private static final ProtobufCodec<Change> CHANGE_CODEC = CodecFactory.encoder(Change.class);
  private static final ProtobufCodec<ChangeMessage> CHANGE_MESSAGE_CODEC =
      CodecFactory.encoder(ChangeMessage.class);
  private static final ProtobufCodec<PatchSet> PATCH_SET_CODEC =
      CodecFactory.encoder(PatchSet.class);
  private static final ProtobufCodec<PatchSetApproval> PATCH_SET_APPROVAL_CODEC =
      CodecFactory.encoder(PatchSetApproval.class);
  private static final ProtobufCodec<PatchLineComment> PATCH_LINE_COMMENT_CODEC =
      CodecFactory.encoder(PatchLineComment.class);

  private String systemTimeZoneProperty;
  private TimeZone systemTimeZone;

  private Project.NameKey project;
  private Account.Id accountId;

  @Before
  public void setUp() {
    String tz = "US/Eastern";
    systemTimeZoneProperty = System.setProperty("user.timezone", tz);
    systemTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(tz));
    long maxMs = ChangeRebuilderImpl.MAX_WINDOW_MS;
    assertThat(maxMs).isGreaterThan(1000L);
    TestTimeUtil.resetWithClockStep(maxMs * 2, MILLISECONDS);
    project = new Project.NameKey("project");
    accountId = new Account.Id(100);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZoneProperty);
    TimeZone.setDefault(systemTimeZone);
  }

  private void superWindowResolution() {
    TestTimeUtil.setClockStep(ChangeRebuilderImpl.MAX_WINDOW_MS * 2, MILLISECONDS);
    TimeUtil.nowTs();
  }

  private void subWindowResolution() {
    TestTimeUtil.setClockStep(1, SECONDS);
    TimeUtil.nowTs();
  }

  @Test
  public void diffChangesDifferentIds() throws Exception {
    Change c1 = TestChanges.newChange(project, accountId);
    int id1 = c1.getId().get();
    Change c2 = TestChanges.newChange(project, accountId);
    int id2 = c2.getId().get();
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);

    assertDiffs(
        b1,
        b2,
        "changeId differs for Changes: {" + id1 + "} != {" + id2 + "}",
        "createdOn differs for Changes:" + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:06.0}",
        "effective last updated time differs for Changes:"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:06.0}");
  }

  @Test
  public void diffChangesSameId() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    c2.setTopic("topic");
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ": {null} != {topic}");
  }

  @Test
  public void diffChangesMixedSourcesAllowsSlop() throws Exception {
    subWindowResolution();
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    c2.setCreatedOn(TimeUtil.nowTs());
    c2.setLastUpdatedOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "createdOn differs for Change.Id "
            + c1.getId()
            + ":"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:02.0}",
        "effective last updated time differs for Change.Id "
            + c1.getId()
            + ":"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:03.0}");

    // One NoteDb, slop is allowed.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);

    // But not too much slop.
    superWindowResolution();
    Change c3 = clone(c1);
    c3.setLastUpdatedOn(TimeUtil.nowTs());
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    ChangeBundle b3 =
        new ChangeBundle(
            c3, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    String msg =
        "effective last updated time differs for Change.Id "
            + c1.getId()
            + " in NoteDb vs. ReviewDb:"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:10.0}";
    assertDiffs(b1, b3, msg);
    assertDiffs(b3, b1, msg);
  }

  @Test
  public void diffChangesIgnoresOriginalSubjectInReviewDb() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    c1.setCurrentPatchSet(c1.currentPatchSetId(), "Subject", "Original A");
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(c2.currentPatchSetId(), c1.getSubject(), "Original B");

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "originalSubject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Original A} != {Original B}");

    // Both NoteDb, exact match required.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(
        b1,
        b2,
        "originalSubject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Original A} != {Original B}");

    // One ReviewDb, one NoteDb, original subject is ignored.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffChangesConsidersEmptyReviewDbTopicEquivalentToNullInNoteDb() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    c1.setTopic("");
    Change c2 = clone(c1);
    c2.setTopic(null);

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ":" + " {} != {null}");

    // Topic ignored if ReviewDb is empty and NoteDb is null.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);

    // Exact match still required if NoteDb has empty value (not realistic).
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ":" + " {} != {null}");

    // Null is not equal to a non-empty string.
    Change c3 = clone(c1);
    c3.setTopic("topic");
    b1 =
        new ChangeBundle(
            c3, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ":" + " {topic} != {null}");

    // Null is equal to a string that is all whitespace.
    Change c4 = clone(c1);
    c4.setTopic("  ");
    b1 =
        new ChangeBundle(
            c4, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffChangesIgnoresLeadingWhitespaceInReviewDbTopics() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    c1.setTopic(" abc");
    Change c2 = clone(c1);
    c2.setTopic("abc");

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ":" + " { abc} != {abc}");

    // Leading whitespace in ReviewDb topic is ignored.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);

    // Must match except for the leading whitespace.
    Change c3 = clone(c1);
    c3.setTopic("cba");
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c3, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(b1, b2, "topic differs for Change.Id " + c1.getId() + ":" + " { abc} != {cba}");
  }

  @Test
  public void diffChangesTakesMaxEntityTimestampFromReviewDb() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    PatchSet ps = new PatchSet(c1.currentPatchSetId());
    ps.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps.setUploader(accountId);
    ps.setCreatedOn(TimeUtil.nowTs());
    PatchSetApproval a =
        new PatchSetApproval(
            new PatchSetApproval.Key(c1.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());

    Change c2 = clone(c1);
    c2.setLastUpdatedOn(a.getGranted());

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(ps), approvals(a), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(ps), approvals(a), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "effective last updated time differs for Change.Id "
            + c1.getId()
            + ":"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:12.0}");

    // NoteDb allows latest timestamp from all entities in bundle.
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(ps), approvals(a), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
  }

  @Test
  public void diffChangesIgnoresChangeTimestampIfAnyOtherEntitiesExist() {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    PatchSet ps = new PatchSet(c1.currentPatchSetId());
    ps.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps.setUploader(accountId);
    ps.setCreatedOn(TimeUtil.nowTs());
    PatchSetApproval a =
        new PatchSetApproval(
            new PatchSetApproval.Key(c1.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());
    c1.setLastUpdatedOn(a.getGranted());

    Change c2 = clone(c1);
    c2.setLastUpdatedOn(TimeUtil.nowTs());

    // ReviewDb has later lastUpdatedOn timestamp than NoteDb, allowed since
    // NoteDb matches the latest timestamp of a non-Change entity.
    ChangeBundle b1 =
        new ChangeBundle(
            c2, messages(), patchSets(ps), approvals(a), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c1, messages(), patchSets(ps), approvals(a), comments(), reviewers(), NOTE_DB);
    assertThat(b1.getChange().getLastUpdatedOn()).isGreaterThan(b2.getChange().getLastUpdatedOn());
    assertNoDiffs(b1, b2);

    // Timestamps must actually match if Change is the only entity.
    b1 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(
        b1,
        b2,
        "effective last updated time differs for Change.Id "
            + c1.getId()
            + " in NoteDb vs. ReviewDb:"
            + " {2009-09-30 17:00:12.0} != {2009-09-30 17:00:18.0}");
  }

  @Test
  public void diffChangesAllowsReviewDbSubjectToBePrefixOfNoteDbSubject() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(
        c1.currentPatchSetId(), c1.getSubject().substring(0, 10), c1.getOriginalSubject());
    assertThat(c2.getSubject()).isNotEqualTo(c1.getSubject());

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "subject differs for Change.Id " + c1.getId() + ":" + " {Change subject} != {Change sub}");

    // ReviewDb has shorter subject, allowed.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);

    // NoteDb has shorter subject, not allowed.
    b1 =
        new ChangeBundle(
            c1, messages(), latest(c1), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(c2, messages(), latest(c2), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(
        b1,
        b2,
        "subject differs for Change.Id " + c1.getId() + ":" + " {Change subject} != {Change sub}");
  }

  @Test
  public void diffChangesTrimsLeadingSpacesFromReviewDbComparingToNoteDb() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(c1.currentPatchSetId(), "   " + c1.getSubject(), c1.getOriginalSubject());

    // Both ReviewDb, exact match required.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Change subject} != {   Change subject}");

    // ReviewDb is missing leading spaces, allowed.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffChangesDoesntTrimLeadingNonSpaceWhitespaceFromSubject() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(c1.currentPatchSetId(), "\t" + c1.getSubject(), c1.getOriginalSubject());

    // Both ReviewDb.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Change subject} != {\tChange subject}");

    // One NoteDb.
    b1 =
        new ChangeBundle(c1, messages(), latest(c1), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), latest(c2), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Change subject} != {\tChange subject}");
    assertDiffs(
        b2,
        b1,
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {\tChange subject} != {Change subject}");
  }

  @Test
  public void diffChangesHandlesBuggyJGitSubjectExtraction() throws Exception {
    Change c1 = TestChanges.newChange(project, accountId);
    String buggySubject = "Subject\r \r Rest of message.";
    c1.setCurrentPatchSet(c1.currentPatchSetId(), buggySubject, buggySubject);
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(c2.currentPatchSetId(), "Subject", "Subject");

    // Both ReviewDb.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "originalSubject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Subject\r \r Rest of message.} != {Subject}",
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Subject\r \r Rest of message.} != {Subject}");

    // NoteDb has correct subject without "\r ".
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffChangesIgnoresInvalidCurrentPatchSetIdInReviewDb() throws Exception {
    Change c1 = TestChanges.newChange(new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    c2.setCurrentPatchSet(
        new PatchSet.Id(c2.getId(), 0), "Unrelated subject", c2.getOriginalSubject());

    // Both ReviewDb.
    ChangeBundle b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "currentPatchSetId differs for Change.Id " + c1.getId() + ":" + " {1} != {0}",
        "subject differs for Change.Id "
            + c1.getId()
            + ":"
            + " {Change subject} != {Unrelated subject}");

    // One NoteDb.
    //
    // This is based on a real corrupt change where all patch sets were deleted
    // but the Change entity stuck around, resulting in a currentPatchSetId of 0
    // after converting to NoteDb.
    b1 =
        new ChangeBundle(
            c1, messages(), patchSets(), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c2, messages(), patchSets(), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffChangeMessageKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    ChangeMessage cm2 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid2"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);

    assertDiffs(
        b1,
        b2,
        "ChangeMessage.Key sets differ:"
            + " ["
            + id
            + ",uuid1] only in A; ["
            + id
            + ",uuid2] only in B");
  }

  @Test
  public void diffChangeMessages() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    cm2.setMessage("message 2");
    assertDiffs(
        b1,
        b2,
        "message differs for ChangeMessage.Key "
            + c.getId()
            + ",uuid:"
            + " {message 1} != {message 2}");
  }

  @Test
  public void diffChangeMessagesIgnoresUuids() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    cm2.getKey().set("uuid2");

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    // Both are ReviewDb, exact UUID match is required.
    assertDiffs(
        b1,
        b2,
        "ChangeMessage.Key sets differ:"
            + " ["
            + id
            + ",uuid1] only in A; ["
            + id
            + ",uuid2] only in B");

    // One NoteDb, UUIDs are ignored.
    b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
  }

  @Test
  public void diffChangeMessagesWithDifferentCounts() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid2"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 2");

    // Both ReviewDb: Uses same keySet diff as other types.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1, cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1, b2, "ChangeMessage.Key sets differ: [" + id + ",uuid2] only in A; [] only in B");

    // One NoteDb: UUIDs in keys can't be used for comparison, just diff counts.
    b1 =
        new ChangeBundle(
            c, messages(cm1, cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(
        b1, b2, "ChangeMessages differ for Change.Id " + id + "\n" + "Only in A:\n  " + cm2);
    assertDiffs(
        b2, b1, "ChangeMessages differ for Change.Id " + id + "\n" + "Only in B:\n  " + cm2);
  }

  @Test
  public void diffChangeMessagesMixedSourcesWithDifferences() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    cm2.setMessage("message 2");
    ChangeMessage cm3 = clone(cm1);
    cm3.getKey().set("uuid2"); // Differs only in UUID.

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1, cm3), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2, cm3), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    // Implementation happens to pair up cm1 in b1 with cm3 in b2 because it
    // depends on iteration order and doesn't care about UUIDs. The important
    // thing is that there's some diff.
    assertDiffs(
        b1,
        b2,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm3
            + "\n"
            + "Only in B:\n  "
            + cm2);
    assertDiffs(
        b2,
        b1,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm2
            + "\n"
            + "Only in B:\n  "
            + cm3);
  }

  @Test
  public void diffChangeMessagesMixedSourcesAllowsSlop() throws Exception {
    subWindowResolution();
    Change c = TestChanges.newChange(project, accountId);
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    ChangeMessage cm2 = clone(cm1);
    cm2.setWrittenOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "writtenOn differs for ChangeMessage.Key "
            + c.getId()
            + ",uuid1:"
            + " {2009-09-30 17:00:02.0} != {2009-09-30 17:00:03.0}");

    // One NoteDb, slop is allowed.
    b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);

    // But not too much slop.
    superWindowResolution();
    ChangeMessage cm3 = clone(cm1);
    cm3.setWrittenOn(TimeUtil.nowTs());
    b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    ChangeBundle b3 =
        new ChangeBundle(
            c, messages(cm3), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    int id = c.getId().get();
    assertDiffs(
        b1,
        b3,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm1
            + "\n"
            + "Only in B:\n  "
            + cm3);
    assertDiffs(
        b3,
        b1,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm3
            + "\n"
            + "Only in B:\n  "
            + cm1);
  }

  @Test
  public void diffChangeMessagesAllowsNullPatchSetIdFromReviewDb() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid"),
            accountId,
            TimeUtil.nowTs(),
            c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    cm2.setPatchSetId(null);

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);

    // Both are ReviewDb, exact patch set ID match is required.
    assertDiffs(
        b1,
        b2,
        "patchset differs for ChangeMessage.Key "
            + c.getId()
            + ",uuid:"
            + " {"
            + id
            + ",1} != {null}");

    // Null patch set ID on ReviewDb is ignored.
    b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);

    // Null patch set ID on NoteDb is not ignored (but is not realistic).
    b1 =
        new ChangeBundle(
            c, messages(cm1), latest(c), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c, messages(cm2), latest(c), approvals(), comments(), reviewers(), NOTE_DB);
    assertDiffs(
        b1,
        b2,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm1
            + "\n"
            + "Only in B:\n  "
            + cm2);
    assertDiffs(
        b2,
        b1,
        "ChangeMessages differ for Change.Id "
            + id
            + "\n"
            + "Only in A:\n  "
            + cm2
            + "\n"
            + "Only in B:\n  "
            + cm1);
  }

  @Test
  public void diffChangeMessagesIgnoresMessagesOnPatchSetGreaterThanCurrent() throws Exception {
    Change c = TestChanges.newChange(project, accountId);

    PatchSet ps1 = new PatchSet(new PatchSet.Id(c.getId(), 1));
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(TimeUtil.nowTs());
    PatchSet ps2 = new PatchSet(new PatchSet.Id(c.getId(), 2));
    ps2.setRevision(new RevId("badc0feebadc0feebadc0feebadc0feebadc0fee"));
    ps2.setUploader(accountId);
    ps2.setCreatedOn(TimeUtil.nowTs());

    assertThat(c.currentPatchSetId()).isEqualTo(ps1.getId());

    ChangeMessage cm1 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid1"), accountId, TimeUtil.nowTs(), ps1.getId());
    cm1.setMessage("a message");
    ChangeMessage cm2 =
        new ChangeMessage(
            new ChangeMessage.Key(c.getId(), "uuid2"), accountId, TimeUtil.nowTs(), ps2.getId());
    cm2.setMessage("other message");

    ChangeBundle b1 =
        new ChangeBundle(
            c,
            messages(cm1, cm2),
            patchSets(ps1, ps2),
            approvals(),
            comments(),
            reviewers(),
            REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(cm1), patchSets(ps1), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffPatchSetIdSets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    TestChanges.incrementPatchSet(c);

    PatchSet ps1 = new PatchSet(new PatchSet.Id(c.getId(), 1));
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(TimeUtil.nowTs());
    PatchSet ps2 = new PatchSet(new PatchSet.Id(c.getId(), 2));
    ps2.setRevision(new RevId("badc0feebadc0feebadc0feebadc0feebadc0fee"));
    ps2.setUploader(accountId);
    ps2.setCreatedOn(TimeUtil.nowTs());

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps1, ps2), approvals(), comments(), reviewers(), REVIEW_DB);

    assertDiffs(
        b1, b2, "PatchSet.Id sets differ:" + " [] only in A; [" + c.getId() + ",1] only in B");
  }

  @Test
  public void diffPatchSets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchSet ps1 = new PatchSet(c.currentPatchSetId());
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(TimeUtil.nowTs());
    PatchSet ps2 = clone(ps1);
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    ps2.setRevision(new RevId("badc0feebadc0feebadc0feebadc0feebadc0fee"));
    assertDiffs(
        b1,
        b2,
        "revision differs for PatchSet.Id "
            + c.getId()
            + ",1:"
            + " {RevId{deadbeefdeadbeefdeadbeefdeadbeefdeadbeef}}"
            + " != {RevId{badc0feebadc0feebadc0feebadc0feebadc0fee}}");
  }

  @Test
  public void diffPatchSetsMixedSourcesAllowsSlop() throws Exception {
    subWindowResolution();
    Change c = TestChanges.newChange(project, accountId);
    PatchSet ps1 = new PatchSet(c.currentPatchSetId());
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(roundToSecond(TimeUtil.nowTs()));
    PatchSet ps2 = clone(ps1);
    ps2.setCreatedOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "createdOn differs for PatchSet.Id "
            + c.getId()
            + ",1:"
            + " {2009-09-30 17:00:02.0} != {2009-09-30 17:00:03.0}");

    // One NoteDb, slop is allowed.
    b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);

    // But not too much slop.
    superWindowResolution();
    PatchSet ps3 = clone(ps1);
    ps3.setCreatedOn(TimeUtil.nowTs());
    b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), NOTE_DB);
    ChangeBundle b3 =
        new ChangeBundle(
            c, messages(), patchSets(ps3), approvals(), comments(), reviewers(), REVIEW_DB);
    String msg =
        "createdOn differs for PatchSet.Id "
            + c.getId()
            + ",1 in NoteDb vs. ReviewDb:"
            + " {2009-09-30 17:00:02.0} != {2009-09-30 17:00:10.0}";
    assertDiffs(b1, b3, msg);
    assertDiffs(b3, b1, msg);
  }

  @Test
  public void diffPatchSetsIgnoresTrailingNewlinesInPushCertificate() throws Exception {
    subWindowResolution();
    Change c = TestChanges.newChange(project, accountId);
    PatchSet ps1 = new PatchSet(c.currentPatchSetId());
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(roundToSecond(TimeUtil.nowTs()));
    ps1.setPushCertificate("some cert");
    PatchSet ps2 = clone(ps1);
    ps2.setPushCertificate(ps2.getPushCertificate() + "\n\n");

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), NOTE_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);

    b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(), comments(), reviewers(), REVIEW_DB);
    b2 =
        new ChangeBundle(
            c, messages(), patchSets(ps2), approvals(), comments(), reviewers(), NOTE_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffIgnoresPatchSetsGreaterThanCurrent() throws Exception {
    Change c = TestChanges.newChange(project, accountId);

    PatchSet ps1 = new PatchSet(new PatchSet.Id(c.getId(), 1));
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(TimeUtil.nowTs());
    PatchSet ps2 = new PatchSet(new PatchSet.Id(c.getId(), 2));
    ps2.setRevision(new RevId("badc0feebadc0feebadc0feebadc0feebadc0fee"));
    ps2.setUploader(accountId);
    ps2.setCreatedOn(TimeUtil.nowTs());
    assertThat(ps2.getId().get()).isGreaterThan(c.currentPatchSetId().get());

    PatchSetApproval a1 =
        new PatchSetApproval(
            new PatchSetApproval.Key(ps1.getId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());
    PatchSetApproval a2 =
        new PatchSetApproval(
            new PatchSetApproval.Key(ps2.getId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());

    // Both ReviewDb.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(a1), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c,
            messages(),
            patchSets(ps1, ps2),
            approvals(a1, a2),
            comments(),
            reviewers(),
            REVIEW_DB);
    assertNoDiffs(b1, b2);

    // One NoteDb.
    b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(a1), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c,
            messages(),
            patchSets(ps1, ps2),
            approvals(a1, a2),
            comments(),
            reviewers(),
            REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);

    // Both NoteDb.
    b1 =
        new ChangeBundle(
            c, messages(), patchSets(ps1), approvals(a1), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c,
            messages(),
            patchSets(ps1, ps2),
            approvals(a1, a2),
            comments(),
            reviewers(),
            NOTE_DB);
    assertNoDiffs(b1, b2);
  }

  @Test
  public void diffPatchSetApprovalKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    PatchSetApproval a1 =
        new PatchSetApproval(
            new PatchSetApproval.Key(c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());
    PatchSetApproval a2 =
        new PatchSetApproval(
            new PatchSetApproval.Key(c.currentPatchSetId(), accountId, new LabelId("Verified")),
            (short) 1,
            TimeUtil.nowTs());

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a1), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);

    assertDiffs(
        b1,
        b2,
        "PatchSetApproval.Key sets differ:"
            + " ["
            + id
            + "%2C1,100,Code-Review] only in A;"
            + " ["
            + id
            + "%2C1,100,Verified] only in B");
  }

  @Test
  public void diffPatchSetApprovals() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchSetApproval a1 =
        new PatchSetApproval(
            new PatchSetApproval.Key(c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            TimeUtil.nowTs());
    PatchSetApproval a2 = clone(a1);
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a1), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    a2.setValue((short) -1);
    assertDiffs(
        b1,
        b2,
        "value differs for PatchSetApproval.Key "
            + c.getId()
            + "%2C1,100,Code-Review: {1} != {-1}");
  }

  @Test
  public void diffPatchSetApprovalsMixedSourcesAllowsSlop() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    subWindowResolution();
    PatchSetApproval a1 =
        new PatchSetApproval(
            new PatchSetApproval.Key(c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            roundToSecond(TimeUtil.nowTs()));
    PatchSetApproval a2 = clone(a1);
    a2.setGranted(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a1), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "granted differs for PatchSetApproval.Key "
            + c.getId()
            + "%2C1,100,Code-Review:"
            + " {2009-09-30 17:00:07.0} != {2009-09-30 17:00:08.0}");

    // One NoteDb, slop is allowed.
    b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(a1), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);

    // But not too much slop.
    superWindowResolution();
    PatchSetApproval a3 = clone(a1);
    a3.setGranted(TimeUtil.nowTs());
    b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(a1), comments(), reviewers(), NOTE_DB);
    ChangeBundle b3 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a3), comments(), reviewers(), REVIEW_DB);
    String msg =
        "granted differs for PatchSetApproval.Key "
            + c.getId()
            + "%2C1,100,Code-Review in NoteDb vs. ReviewDb:"
            + " {2009-09-30 17:00:07.0} != {2009-09-30 17:00:15.0}";
    assertDiffs(b1, b3, msg);
    assertDiffs(b3, b1, msg);
  }

  @Test
  public void diffPatchSetApprovalsAllowsTruncatedTimestampInNoteDb() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchSetApproval a1 =
        new PatchSetApproval(
            new PatchSetApproval.Key(c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
            (short) 1,
            c.getCreatedOn());
    PatchSetApproval a2 = clone(a1);
    a2.setGranted(
        new Timestamp(
            new DateTime(1900, 1, 1, 0, 0, 0, DateTimeZone.forTimeZone(TimeZone.getDefault()))
                .getMillis()));

    // Both are ReviewDb, exact match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a1), comments(), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "granted differs for PatchSetApproval.Key "
            + c.getId()
            + "%2C1,100,Code-Review:"
            + " {2009-09-30 17:00:00.0} != {1900-01-01 00:00:00.0}");

    // Truncating NoteDb timestamp is allowed.
    b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(a1), comments(), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(a2), comments(), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
    assertNoDiffs(b2, b1);
  }

  @Test
  public void diffReviewers() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    Timestamp now = TimeUtil.nowTs();
    ReviewerSet r1 = reviewers(REVIEWER, new Account.Id(1), now);
    ReviewerSet r2 = reviewers(REVIEWER, new Account.Id(2), now);

    ChangeBundle b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(), r1, REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(), r2, REVIEW_DB);
    assertNoDiffs(b1, b1);
    assertNoDiffs(b2, b2);
    assertDiffs(b1, b2, "reviewer sets differ:" + " [1] only in A;" + " [2] only in B");
  }

  @Test
  public void diffReviewersIgnoresStateAndTimestamp() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    ReviewerSet r1 = reviewers(REVIEWER, new Account.Id(1), TimeUtil.nowTs());
    ReviewerSet r2 = reviewers(CC, new Account.Id(1), TimeUtil.nowTs());

    ChangeBundle b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(), r1, REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(), r2, REVIEW_DB);
    assertNoDiffs(b1, b1);
    assertNoDiffs(b2, b2);
  }

  @Test
  public void diffPatchLineCommentKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    PatchLineComment c1 =
        new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(c.currentPatchSetId(), "filename1"), "uuid1"),
            5,
            accountId,
            null,
            TimeUtil.nowTs());
    PatchLineComment c2 =
        new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(c.currentPatchSetId(), "filename2"), "uuid2"),
            5,
            accountId,
            null,
            TimeUtil.nowTs());

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c1), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c2), reviewers(), REVIEW_DB);

    assertDiffs(
        b1,
        b2,
        "PatchLineComment.Key sets differ:"
            + " ["
            + id
            + ",1,filename1,uuid1] only in A;"
            + " ["
            + id
            + ",1,filename2,uuid2] only in B");
  }

  @Test
  public void diffPatchLineComments() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchLineComment c1 =
        new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(c.currentPatchSetId(), "filename"), "uuid"),
            5,
            accountId,
            null,
            TimeUtil.nowTs());
    PatchLineComment c2 = clone(c1);
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c1), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c2), reviewers(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    c2.setStatus(PatchLineComment.Status.PUBLISHED);
    assertDiffs(
        b1,
        b2,
        "status differs for PatchLineComment.Key " + c.getId() + ",1,filename,uuid: {d} != {P}");
  }

  @Test
  public void diffPatchLineCommentsMixedSourcesAllowsSlop() throws Exception {
    subWindowResolution();
    Change c = TestChanges.newChange(project, accountId);
    PatchLineComment c1 =
        new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(c.currentPatchSetId(), "filename"), "uuid"),
            5,
            accountId,
            null,
            roundToSecond(TimeUtil.nowTs()));
    PatchLineComment c2 = clone(c1);
    c2.setWrittenOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c1), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c2), reviewers(), REVIEW_DB);
    assertDiffs(
        b1,
        b2,
        "writtenOn differs for PatchLineComment.Key "
            + c.getId()
            + ",1,filename,uuid:"
            + " {2009-09-30 17:00:02.0} != {2009-09-30 17:00:03.0}");

    // One NoteDb, slop is allowed.
    b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(c1), reviewers(), NOTE_DB);
    b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c2), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);

    // But not too much slop.
    superWindowResolution();
    PatchLineComment c3 = clone(c1);
    c3.setWrittenOn(TimeUtil.nowTs());
    b1 =
        new ChangeBundle(c, messages(), latest(c), approvals(), comments(c1), reviewers(), NOTE_DB);
    ChangeBundle b3 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c3), reviewers(), REVIEW_DB);
    String msg =
        "writtenOn differs for PatchLineComment.Key "
            + c.getId()
            + ",1,filename,uuid in NoteDb vs. ReviewDb:"
            + " {2009-09-30 17:00:02.0} != {2009-09-30 17:00:10.0}";
    assertDiffs(b1, b3, msg);
    assertDiffs(b3, b1, msg);
  }

  @Test
  public void diffPatchLineCommentsIgnoresCommentsOnInvalidPatchSet() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchLineComment c1 =
        new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(c.currentPatchSetId(), "filename1"), "uuid1"),
            5,
            accountId,
            null,
            TimeUtil.nowTs());
    PatchLineComment c2 =
        new PatchLineComment(
            new PatchLineComment.Key(
                new Patch.Key(new PatchSet.Id(c.getId(), 0), "filename2"), "uuid2"),
            5,
            accountId,
            null,
            TimeUtil.nowTs());

    ChangeBundle b1 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c1, c2), reviewers(), REVIEW_DB);
    ChangeBundle b2 =
        new ChangeBundle(
            c, messages(), latest(c), approvals(), comments(c1), reviewers(), REVIEW_DB);
    assertNoDiffs(b1, b2);
  }

  private static void assertNoDiffs(ChangeBundle a, ChangeBundle b) {
    assertThat(a.differencesFrom(b)).isEmpty();
    assertThat(b.differencesFrom(a)).isEmpty();
  }

  private static void assertDiffs(ChangeBundle a, ChangeBundle b, String first, String... rest) {
    List<String> actual = a.differencesFrom(b);
    if (actual.size() == 1 && rest.length == 0) {
      // This error message is much easier to read.
      assertThat(actual.get(0)).isEqualTo(first);
    } else {
      List<String> expected = new ArrayList<>(1 + rest.length);
      expected.add(first);
      Collections.addAll(expected, rest);
      assertThat(actual).containsExactlyElementsIn(expected).inOrder();
    }
    assertThat(a).isNotEqualTo(b);
  }

  private static List<ChangeMessage> messages(ChangeMessage... ents) {
    return Arrays.asList(ents);
  }

  private static List<PatchSet> patchSets(PatchSet... ents) {
    return Arrays.asList(ents);
  }

  private static List<PatchSet> latest(Change c) {
    PatchSet ps = new PatchSet(c.currentPatchSetId());
    ps.setCreatedOn(c.getLastUpdatedOn());
    return ImmutableList.of(ps);
  }

  private static List<PatchSetApproval> approvals(PatchSetApproval... ents) {
    return Arrays.asList(ents);
  }

  private static ReviewerSet reviewers(Object... ents) {
    checkArgument(ents.length % 3 == 0);
    Table<ReviewerStateInternal, Account.Id, Timestamp> t = HashBasedTable.create();
    for (int i = 0; i < ents.length; i += 3) {
      t.put((ReviewerStateInternal) ents[i], (Account.Id) ents[i + 1], (Timestamp) ents[i + 2]);
    }
    return ReviewerSet.fromTable(t);
  }

  private static List<PatchLineComment> comments(PatchLineComment... ents) {
    return Arrays.asList(ents);
  }

  private static Change clone(Change ent) {
    return clone(CHANGE_CODEC, ent);
  }

  private static ChangeMessage clone(ChangeMessage ent) {
    return clone(CHANGE_MESSAGE_CODEC, ent);
  }

  private static PatchSet clone(PatchSet ent) {
    return clone(PATCH_SET_CODEC, ent);
  }

  private static PatchSetApproval clone(PatchSetApproval ent) {
    return clone(PATCH_SET_APPROVAL_CODEC, ent);
  }

  private static PatchLineComment clone(PatchLineComment ent) {
    return clone(PATCH_LINE_COMMENT_CODEC, ent);
  }

  private static <T> T clone(ProtobufCodec<T> codec, T obj) {
    return codec.decode(codec.encodeToByteArray(obj));
  }
}
