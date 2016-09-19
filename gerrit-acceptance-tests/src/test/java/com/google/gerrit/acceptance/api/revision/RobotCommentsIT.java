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
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RobotCommentsIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "notedb.writeJson", value = "true")
  public void comments() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    PushOneCommit.Result r = createChange();
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.url = "http://www.happy-robot.com";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<RobotCommentInput>> robotComments = new HashMap<>();
    robotComments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.robotComments = robotComments;
    reviewInput.message = "comment test";
    gApi.changes()
       .id(r.getChangeId())
       .current()
       .review(reviewInput);

    Map<String, List<RobotCommentInfo>> out = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .robotComments();
    assertThat(out).hasSize(1);
    RobotCommentInfo comment = Iterables.getOnlyElement(out.get(FILE_NAME));
    assertThat(comment.robotId).isEqualTo(in.robotId);
    assertThat(comment.robotRunId).isEqualTo(in.robotRunId);
    assertThat(comment.url).isEqualTo(in.url);
    assertThat(comment.message).isEqualTo(in.message);
    assertThat(comment.author.email).isEqualTo(admin.email);
    assertThat(comment.path).isNull();

    List<RobotCommentInfo> list = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .robotCommentsAsList();
    assertThat(list).hasSize(1);

    RobotCommentInfo comment2 = list.get(0);
    assertThat(comment2.robotId).isEqualTo(in.robotId);
    assertThat(comment2.robotRunId).isEqualTo(in.robotRunId);
    assertThat(comment2.url).isEqualTo(in.url);
    assertThat(comment2.path).isEqualTo(FILE_NAME);
    assertThat(comment2.line).isEqualTo(comment.line);
    assertThat(comment2.message).isEqualTo(comment.message);
    assertThat(comment2.author.email).isEqualTo(comment.author.email);

    RobotCommentInfo comment3 = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .robotComment(comment.id)
        .get();
    assertThat(comment3.robotId).isEqualTo(in.robotId);
    assertThat(comment3.robotRunId).isEqualTo(in.robotRunId);
    assertThat(comment3.url).isEqualTo(in.url);
    assertThat(comment3.path).isEqualTo(FILE_NAME);
    assertThat(comment3.line).isEqualTo(comment.line);
    assertThat(comment3.message).isEqualTo(comment.message);
    assertThat(comment3.author.email).isEqualTo(comment.author.email);
  }

  @Test
  public void robotCommentsNotSupported() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();

    PushOneCommit.Result r = createChange();
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.url = "http://www.happy-robot.com";
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<RobotCommentInput>> robotComments = new HashMap<>();
    robotComments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.robotComments = robotComments;
    reviewInput.message = "comment test";

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("robot comments not supported");
    gApi.changes()
       .id(r.getChangeId())
       .current()
       .review(reviewInput);
  }
}
