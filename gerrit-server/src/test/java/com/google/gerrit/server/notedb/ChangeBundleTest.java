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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.TimeUtil.roundToSecond;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.NOTE_DB;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.REVIEW_DB;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChangeBundleTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private static final ProtobufCodec<Change> CHANGE_CODEC =
      CodecFactory.encoder(Change.class);
  private static final ProtobufCodec<ChangeMessage> CHANGE_MESSAGE_CODEC =
      CodecFactory.encoder(ChangeMessage.class);
  private static final ProtobufCodec<PatchSet> PATCH_SET_CODEC =
      CodecFactory.encoder(PatchSet.class);
  private static final ProtobufCodec<PatchSetApproval>
      PATCH_SET_APPROVAL_CODEC = CodecFactory.encoder(PatchSetApproval.class);
  private static final ProtobufCodec<PatchLineComment>
      PATCH_LINE_COMMENT_CODEC = CodecFactory.encoder(PatchLineComment.class);

  private String systemTimeZone;
  private Project.NameKey project;
  private Account.Id accountId;

  @Before
  public void setUp() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    project = new Project.NameKey("project");
    accountId = new Account.Id(100);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  private void subSecondResolution() {
    TestTimeUtil.setClockStep(100, MILLISECONDS);
    TimeUtil.nowTs();
  }

  @Test
  public void diffChangesDifferentIds() throws Exception {
    Change c1 = TestChanges.newChange(project, accountId);
    int id1 = c1.getId().get();
    Change c2 = TestChanges.newChange(project, accountId);
    int id2 = c2.getId().get();
    ChangeBundle b1 = new ChangeBundle(c1, messages(), patchSets(), approvals(),
        comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c2, messages(), patchSets(), approvals(),
        comments(), REVIEW_DB);

    assertDiffs(b1, b2,
        "changeId differs for Changes: {" + id1 + "} != {" + id2 + "}",
        "createdOn differs for Changes:"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.0}",
        "lastUpdatedOn differs for Changes:"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.0}");
  }

  @Test
  public void diffChangesSameId() throws Exception {
    Change c1 = TestChanges.newChange(
        new Project.NameKey("project"), new Account.Id(100));
    Change c2 = clone(c1);
    ChangeBundle b1 = new ChangeBundle(c1, messages(), patchSets(), approvals(),
        comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c2, messages(), patchSets(), approvals(),
        comments(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    c2.setTopic("topic");
    assertDiffs(b1, b2,
        "topic differs for Change.Id "+ c1.getId() + ": {null} != {topic}");
  }

  @Test
  public void diffChangesMixedSourcesRoundsTimestamp() throws Exception {
    Change c1 = TestChanges.newChange(
        new Project.NameKey("project"), new Account.Id(100));
    subSecondResolution();
    Change c2 = clone(c1);
    c2.setCreatedOn(TimeUtil.nowTs());
    c2.setLastUpdatedOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 = new ChangeBundle(c1, messages(), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c2, messages(), patchSets(),
        approvals(), comments(), REVIEW_DB);
    assertDiffs(b1, b2,
        "createdOn differs for Change.Id " + c1.getId() + ":"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.1}",
        "lastUpdatedOn differs for Change.Id " + c1.getId() + ":"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.2}");

    // One NoteDb, timestamp is rounded.
    b1 = new ChangeBundle(c1, messages(), patchSets(), approvals(),
        comments(), NOTE_DB);
    b2 = new ChangeBundle(c2, messages(), patchSets(), approvals(),
        comments(), REVIEW_DB);
    assertDiffs(b1, b2,
        "createdOn differs for Change.Id " + c1.getId() + ":"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.0}",
        "lastUpdatedOn differs for Change.Id " + c1.getId() + ":"
            + " {2009-09-30 17:00:00.0} != {2009-09-30 17:00:01.0}");
  }

  @Test
  public void diffChangeMessageKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid1"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    ChangeMessage cm2 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid2"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    ChangeBundle b1 = new ChangeBundle(c, messages(cm1), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(cm2), patchSets(),
        approvals(), comments(), REVIEW_DB);

    assertDiffs(b1, b2,
        "ChangeMessage.Key sets differ:"
            + " [" + id + ",uuid1] only in A; [" + id + ",uuid2] only in B");
  }

  @Test
  public void diffChangeMessages() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    ChangeMessage cm1 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    ChangeBundle b1 = new ChangeBundle(c, messages(cm1), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(cm2), patchSets(),
        approvals(), comments(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    cm2.setMessage("message 2");
    assertDiffs(b1, b2,
        "message differs for ChangeMessage.Key " + c.getId() + ",uuid:"
            + " {message 1} != {message 2}");
  }

  @Test
  public void diffChangeMessagesIgnoresUuids() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid1"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    cm2.getKey().set("uuid2");

    ChangeBundle b1 = new ChangeBundle(c, messages(cm1), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(cm2), patchSets(),
        approvals(), comments(), REVIEW_DB);
    // Both are ReviewDb, exact UUID match is required.
    assertDiffs(b1, b2,
        "ChangeMessage.Key sets differ:"
            + " [" + id + ",uuid1] only in A; [" + id + ",uuid2] only in B");

    // One NoteDb, UUIDs are ignored.
    b1 = new ChangeBundle(c, messages(cm1), patchSets(), approvals(),
        comments(), REVIEW_DB);
    b2 = new ChangeBundle(c, messages(cm2), patchSets(), approvals(),
        comments(), NOTE_DB);
    assertNoDiffs(b1, b2);
  }


  @Test
  public void diffChangeMessagesMixedSourcesWithDifferences() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    ChangeMessage cm1 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid1"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    cm1.setMessage("message 1");
    ChangeMessage cm2 = clone(cm1);
    cm2.setMessage("message 2");
    ChangeMessage cm3 = clone(cm1);
    cm3.getKey().set("uuid2"); // Differs only in UUID.

    ChangeBundle b1 = new ChangeBundle(c, messages(cm1, cm3), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(cm2, cm3), patchSets(),
        approvals(), comments(), NOTE_DB);
    assertDiffs(b1, b2,
        "ChangeMessage present 2 times in A but 1 time in B:"
            + " NormalizedChangeMessage{changeId=" + id + ", author=100,"
            + " writtenOn=2009-09-30 17:00:01.0, message=message 1,"
            + " patchset=" + id + ",1}",
        "ChangeMessage present 0 times in A but 1 time in B:"
            + " NormalizedChangeMessage{changeId=" + id + ", author=100,"
            + " writtenOn=2009-09-30 17:00:01.0, message=message 2,"
            + " patchset=" + id + ",1}");
  }

  @Test
  public void diffChangeMessagesMixedSourcesRoundsTimestamp() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    subSecondResolution();
    ChangeMessage cm1 = new ChangeMessage(
        new ChangeMessage.Key(c.getId(), "uuid1"),
        accountId, TimeUtil.nowTs(), c.currentPatchSetId());
    ChangeMessage cm2 = clone(cm1);
    cm2.setWrittenOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 = new ChangeBundle(c, messages(cm1), patchSets(),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(cm2), patchSets(),
        approvals(), comments(), REVIEW_DB);
    assertDiffs(b1, b2,
        "writtenOn differs for ChangeMessage.Key " + c.getId() + ",uuid1:"
            + " {2009-09-30 17:00:01.1} != {2009-09-30 17:00:01.2}");

    // One NoteDb, timestamp is rounded.
    b1 = new ChangeBundle(c, messages(cm1), patchSets(), approvals(),
        comments(), NOTE_DB);
    b2 = new ChangeBundle(c, messages(cm2), patchSets(), approvals(),
        comments(), REVIEW_DB);
    assertNoDiffs(b1, b2);
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

    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(ps2),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(ps1, ps2),
        approvals(), comments(), REVIEW_DB);

    assertDiffs(b1, b2,
        "PatchSet.Id sets differ:"
            + " [] only in A; [" + c.getId() + ",1] only in B");
  }

  @Test
  public void diffPatchSets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchSet ps1 = new PatchSet(c.currentPatchSetId());
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(TimeUtil.nowTs());
    PatchSet ps2 = clone(ps1);
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(ps1),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(ps2),
        approvals(), comments(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    ps2.setRevision(new RevId("badc0feebadc0feebadc0feebadc0feebadc0fee"));
    assertDiffs(b1, b2,
        "revision differs for PatchSet.Id " + c.getId() + ",1:"
            + " {RevId{deadbeefdeadbeefdeadbeefdeadbeefdeadbeef}}"
            + " != {RevId{badc0feebadc0feebadc0feebadc0feebadc0fee}}");
  }

  @Test
  public void diffPatchSetsMixedSourcesRoundsTimestamp() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    subSecondResolution();
    PatchSet ps1 = new PatchSet(c.currentPatchSetId());
    ps1.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    ps1.setUploader(accountId);
    ps1.setCreatedOn(roundToSecond(TimeUtil.nowTs()));
    PatchSet ps2 = clone(ps1);
    ps2.setCreatedOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(ps1),
        approvals(), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(ps2),
        approvals(), comments(), REVIEW_DB);
    assertDiffs(b1, b2,
        "createdOn differs for PatchSet.Id " + c.getId() + ",1:"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:01.2}");

    // One NoteDb, timestamp is rounded.
    b1 = new ChangeBundle(c, messages(), patchSets(ps1), approvals(),
        comments(), NOTE_DB);
    b2 = new ChangeBundle(c, messages(), patchSets(ps2), approvals(),
        comments(), REVIEW_DB);
    assertNoDiffs(b1, b2);
  }

  @Test
  public void diffPatchSetApprovalKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    PatchSetApproval a1 = new PatchSetApproval(
        new PatchSetApproval.Key(
            c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
        (short) 1,
        TimeUtil.nowTs());
    PatchSetApproval a2 = new PatchSetApproval(
        new PatchSetApproval.Key(
            c.currentPatchSetId(), accountId, new LabelId("Verified")),
        (short) 1,
        TimeUtil.nowTs());

    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a1), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a2), comments(), REVIEW_DB);

    assertDiffs(b1, b2,
        "PatchSetApproval.Key sets differ:"
            + " [" + id + "%2C1,100,Code-Review] only in A;"
            + " [" + id + "%2C1,100,Verified] only in B");
  }

  @Test
  public void diffPatchSetApprovals() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchSetApproval a1 = new PatchSetApproval(
        new PatchSetApproval.Key(
            c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
        (short) 1,
        TimeUtil.nowTs());
    PatchSetApproval a2 = clone(a1);
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a1), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a2), comments(), REVIEW_DB);

    assertNoDiffs(b1, b2);

    a2.setValue((short) -1);
    assertDiffs(b1, b2,
        "value differs for PatchSetApproval.Key "
            + c.getId() + "%2C1,100,Code-Review: {1} != {-1}");
  }

  @Test
  public void diffPatchSetApprovalsMixedSourcesRoundsTimestamp()
      throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    subSecondResolution();
    PatchSetApproval a1 = new PatchSetApproval(
        new PatchSetApproval.Key(
            c.currentPatchSetId(), accountId, new LabelId("Code-Review")),
        (short) 1,
        roundToSecond(TimeUtil.nowTs()));
    PatchSetApproval a2 = clone(a1);
    a2.setGranted(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a1), comments(), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(a2), comments(), REVIEW_DB);
    assertDiffs(b1, b2,
        "granted differs for PatchSetApproval.Key "
            + c.getId() + "%2C1,100,Code-Review:"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:01.2}");

    // One NoteDb, timestamp is rounded.
    b1 = new ChangeBundle(c, messages(), patchSets(), approvals(a1),
        comments(), NOTE_DB);
    b2 = new ChangeBundle(c, messages(), patchSets(), approvals(a2),
        comments(), REVIEW_DB);
    assertNoDiffs(b1, b2);
  }

  @Test
  public void diffPatchLineCommentKeySets() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    int id = c.getId().get();
    PatchLineComment c1 = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(c.currentPatchSetId(), "filename1"), "uuid1"),
        5, accountId, null, TimeUtil.nowTs());
    PatchLineComment c2 = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(c.currentPatchSetId(), "filename2"), "uuid2"),
        5, accountId, null, TimeUtil.nowTs());

    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c1), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c2), REVIEW_DB);

    assertDiffs(b1, b2,
        "PatchLineComment.Key sets differ:"
            + " [" + id + ",1,filename1,uuid1] only in A;"
            + " [" + id + ",1,filename2,uuid2] only in B");
  }

  @Test
  public void diffPatchLineComments() throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    PatchLineComment c1 = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(c.currentPatchSetId(), "filename"), "uuid"),
        5, accountId, null, TimeUtil.nowTs());
    PatchLineComment c2 = clone(c1);
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c1), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c2), REVIEW_DB);

    assertNoDiffs(b1, b2);

    c2.setStatus(PatchLineComment.Status.PUBLISHED);
    assertDiffs(b1, b2,
        "status differs for PatchLineComment.Key "
            + c.getId() + ",1,filename,uuid: {d} != {P}");
  }

  @Test
  public void diffPatchLineCommentsMixedSourcesRoundsTimestamp()
      throws Exception {
    Change c = TestChanges.newChange(project, accountId);
    subSecondResolution();
    PatchLineComment c1 = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(c.currentPatchSetId(), "filename"), "uuid"),
        5, accountId, null, roundToSecond(TimeUtil.nowTs()));
    PatchLineComment c2 = clone(c1);
    c2.setWrittenOn(TimeUtil.nowTs());

    // Both are ReviewDb, exact timestamp match is required.
    ChangeBundle b1 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c1), REVIEW_DB);
    ChangeBundle b2 = new ChangeBundle(c, messages(), patchSets(),
        approvals(), comments(c2), REVIEW_DB);
    assertDiffs(b1, b2,
        "writtenOn differs for PatchLineComment.Key "
            + c.getId() + ",1,filename,uuid:"
            + " {2009-09-30 17:00:01.0} != {2009-09-30 17:00:01.2}");

    // One NoteDb, timestamp is rounded.
    b1 = new ChangeBundle(c, messages(), patchSets(), approvals(),
        comments(c1), NOTE_DB);
    b2 = new ChangeBundle(c, messages(), patchSets(), approvals(),
        comments(c2), REVIEW_DB);
    assertNoDiffs(b1, b2);
  }

  private static void assertNoDiffs(ChangeBundle a, ChangeBundle b) {
    assertThat(a.differencesFrom(b)).isEmpty();
    assertThat(b.differencesFrom(a)).isEmpty();
    assertThat(a).isEqualTo(b);
    assertThat(b).isEqualTo(a);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  private static void assertDiffs(ChangeBundle a, ChangeBundle b, String first,
      String... rest) {
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

  private static List<PatchSetApproval> approvals(PatchSetApproval... ents) {
    return Arrays.asList(ents);
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
