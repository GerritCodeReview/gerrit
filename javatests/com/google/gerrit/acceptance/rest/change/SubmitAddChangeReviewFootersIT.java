// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitAddChangeReviewFootersIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  @TestProjectInput(submitType = SubmitType.CHERRY_PICK)
  @GerritConfig(name = "change.addChangeReviewFootersToCommitMessage", value = "false")
  public void submitWithSkipApprovals() throws Throwable {
    // Grant submit permission
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change.getChangeId()).current().submit();

    RevCommit head = projectOperations.project(project).getHead("master");
    testRepo.getRevWalk().parseBody(head);

    // Check footers
    assertWithMessage(head.getFullMessage())
        .that(head.getFooterLines(FooterConstants.CHANGE_ID))
        .isNotEmpty();
    assertWithMessage(head.getFullMessage())
        .that(head.getFooterLines(FooterConstants.REVIEWED_ON))
        .isNotEmpty();
    assertThat(head.getFooterLines(FooterConstants.REVIEWED_BY)).isEmpty();
    assertThat(head.getFooterLines(FooterConstants.TESTED_BY)).isEmpty();

    // Verify it was actually the change we submitted (just in case)
    assertThat(head.getShortMessage()).isEqualTo("test commit");
  }

  @Test
  @TestProjectInput(submitType = SubmitType.CHERRY_PICK)
  @GerritConfig(name = "change.addChangeReviewFootersToCommitMessage", value = "true")
  public void submitWithAddApprovals() throws Throwable {
    // Grant submit permission
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change.getChangeId()).current().submit();

    RevCommit head = projectOperations.project(project).getHead("master");
    testRepo.getRevWalk().parseBody(head);

    // Check footers
    assertWithMessage(head.getFullMessage())
        .that(head.getFooterLines(FooterConstants.CHANGE_ID))
        .isNotEmpty();
    assertWithMessage(head.getFullMessage())
        .that(head.getFooterLines(FooterConstants.REVIEWED_ON))
        .isNotEmpty();
    assertWithMessage(head.getFullMessage())
        .that(head.getFooterLines(FooterConstants.REVIEWED_BY))
        .isNotEmpty();
  }
}
