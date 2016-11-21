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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RobotCommentsIT extends AbstractDaemonTest {
  private String changeId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    changeId = changeResult.getChangeId();
  }

  @Test
  public void testAddAndRetrieveRobotComments() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput in = createRobotCommentInput();
    addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes()
        .id(changeId)
        .current()
        .robotComments();

    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void testRetrieveRobotCommentsAsList() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput robotCommentInput = createRobotCommentInput();
    addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = gApi.changes()
        .id(changeId)
        .current()
        .robotCommentsAsList();

    assertThat(robotCommentInfos).hasSize(1);
    RobotCommentInfo robotCommentInfo =
        Iterables.getOnlyElement(robotCommentInfos);
    assertRobotComment(robotCommentInfo, robotCommentInput);
  }

  @Test
  public void testRetrieveSpecificRobotComment() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput robotCommentInput = createRobotCommentInput();
    addRobotComment(changeId, robotCommentInput);

    List<RobotCommentInfo> robotCommentInfos = gApi.changes()
        .id(changeId)
        .current()
        .robotCommentsAsList();
    RobotCommentInfo robotCommentInfo =
        Iterables.getOnlyElement(robotCommentInfos);

    RobotCommentInfo specificRobotCommentInfo = gApi.changes()
        .id(changeId)
        .current()
        .robotComment(robotCommentInfo.id)
        .get();
    assertRobotComment(specificRobotCommentInfo, robotCommentInput);
  }

  @Test
  public void testAddRobotCommentWithoutOptionalFields() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    RobotCommentInput in = createRobotCommentInputWithMandatoryFields();
    addRobotComment(changeId, in);

    Map<String, List<RobotCommentInfo>> out = gApi.changes()
        .id(changeId)
        .current()
        .robotComments();
    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(in.path));
    assertRobotComment(comment, in, false);
  }

  @Test
  public void testRobotCommentsNotSupportedWithoutNoteDb() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();

    RobotCommentInput in = createRobotCommentInput();
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<RobotCommentInput>> robotComments = new HashMap<>();
    robotComments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.robotComments = robotComments;
    reviewInput.message = "comment test";

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("robot comments not supported");
    gApi.changes()
       .id(changeId)
       .current()
       .review(reviewInput);
  }

  private RobotCommentInput createRobotCommentInputWithMandatoryFields() {
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    return in;
  }

  private RobotCommentInput createRobotCommentInput() {
    RobotCommentInput in = createRobotCommentInputWithMandatoryFields();
    in.url = "http://www.happy-robot.com";
    in.properties = new HashMap<>();
    in.properties.put("key1", "value1");
    in.properties.put("key2", "value2");
    return in;
  }

  private void addRobotComment(String targetChangeId,
      RobotCommentInput robotCommentInput) throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments = Collections.singletonMap(robotCommentInput.path,
        Collections.singletonList(robotCommentInput));
    reviewInput.message = "robot comment test";
    gApi.changes()
        .id(targetChangeId)
        .current()
        .review(reviewInput);
  }

  private void assertRobotComment(RobotCommentInfo c,
      RobotCommentInput expected) {
    assertRobotComment(c, expected, true);
  }

  private void assertRobotComment(RobotCommentInfo c,
      RobotCommentInput expected, boolean expectPath) {
    assertThat(c.robotId).isEqualTo(expected.robotId);
    assertThat(c.robotRunId).isEqualTo(expected.robotRunId);
    assertThat(c.url).isEqualTo(expected.url);
    assertThat(c.properties).isEqualTo(expected.properties);
    assertThat(c.line).isEqualTo(expected.line);
    assertThat(c.message).isEqualTo(expected.message);

    assertThat(c.author.email).isEqualTo(admin.email);

    if (expectPath) {
      assertThat(c.path).isEqualTo(expected.path);
    } else {
      assertThat(c.path).isNull();
    }
  }
}
