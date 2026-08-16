// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

@NoHttpd
public class ChangeJsonBenchmarkIT extends AbstractDaemonTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject private ChangeJson.Factory changeJsonFactory;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void benchmarkChangeListFormattingLatency() throws Exception {
    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/heads/*").group(registeredUsers).range(-2, 2))
        .update();

    int numChanges = 15;

    TestAccount user2 = accountCreator.user2();
    TestAccount reviewer =
        accountCreator.create(
            "benchmark-reviewer", "reviewer@example.com", "Benchmark Reviewer", null);

    for (int i = 0; i < numChanges; i++) {
      // PS1
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo, "Subject " + i, "file" + i + ".txt", "content " + i)
              .to("refs/for/master");
      r.assertOkStatus();
      String changeIdStr = r.getChangeId();

      // Add reviewer and vote on PS1
      gApi.changes().id(changeIdStr).addReviewer(reviewer.email());
      requestScopeOperations.setApiUser(user.id());
      gApi.changes().id(changeIdStr).current().review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));

      // PS2
      requestScopeOperations.setApiUser(admin.id());
      r =
          amendChange(
              changeIdStr,
              "refs/for/master",
              admin,
              testRepo,
              "Subject " + i + " amended",
              "file" + i + ".txt",
              "content " + i + " v2");
      r.assertOkStatus();

      // Vote on PS2
      requestScopeOperations.setApiUser(user2.id());
      gApi.changes().id(changeIdStr).current().review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));

      requestScopeOperations.setApiUser(admin.id());
    }

    List<ChangeData> changeDataList = queryProvider.get().byProject(project);
    assertThat(changeDataList).hasSize(numChanges);

    // Warmup
    for (int i = 0; i < 5; i++) {
      var unused1 = changeJsonFactory.create(LABELS).format(changeDataList);
      var unused2 = changeJsonFactory.create(DETAILED_LABELS).format(changeDataList);
    }

    int iterations = 20;

    // Benchmark basic LABELS
    Stopwatch swLabels = Stopwatch.createStarted();
    List<ChangeInfo> formattedLabels = null;
    for (int i = 0; i < iterations; i++) {
      formattedLabels = changeJsonFactory.create(LABELS).format(changeDataList);
    }
    swLabels.stop();
    long labelsNanos = swLabels.elapsed(TimeUnit.NANOSECONDS) / iterations;
    double labelsMs = labelsNanos / 1_000_000.0;

    // Benchmark DETAILED_LABELS
    Stopwatch swDetailed = Stopwatch.createStarted();
    List<ChangeInfo> formattedDetailed = null;
    for (int i = 0; i < iterations; i++) {
      formattedDetailed = changeJsonFactory.create(DETAILED_LABELS).format(changeDataList);
    }
    swDetailed.stop();
    long detailedNanos = swDetailed.elapsed(TimeUnit.NANOSECONDS) / iterations;
    double detailedMs = detailedNanos / 1_000_000.0;

    System.out.printf(
        "[BENCHMARK] ChangeJson formatting for %d changes: LABELS = %.3f ms, DETAILED_LABELS = %.3f ms (Speedup: %.2fx)%n",
        numChanges, labelsMs, detailedMs, detailedMs / Math.max(labelsMs, 0.001));
    logger.atInfo().log(
        "[BENCHMARK] ChangeJson formatting for %d changes: LABELS = %.3f ms, DETAILED_LABELS = %.3f ms (Speedup: %.2fx)",
        numChanges, labelsMs, detailedMs, detailedMs / Math.max(labelsMs, 0.001));

    // Verify correctness of LABELS output
    assertThat(formattedLabels).hasSize(numChanges);
    for (ChangeInfo ci : formattedLabels) {
      assertThat(ci.labels).isNotNull();
      LabelInfo codeReview = ci.labels.get(LabelId.CODE_REVIEW);
      assertThat(codeReview).isNotNull();
      assertThat(codeReview.approved).isNotNull();
      assertThat(codeReview.approved._accountId).isEqualTo(user2.id().get());
      assertThat(codeReview.all).hasSize(1);
      assertThat(codeReview.all.get(0)._accountId).isEqualTo(user2.id().get());
      assertThat(codeReview.all.get(0).value).isEqualTo(2);
      assertThat(codeReview.all.get(0).permittedVotingRange).isNull();
    }

    // Verify correctness of DETAILED_LABELS output
    assertThat(formattedDetailed).hasSize(numChanges);
    for (ChangeInfo ci : formattedDetailed) {
      assertThat(ci.labels).isNotNull();
      LabelInfo codeReview = ci.labels.get(LabelId.CODE_REVIEW);
      assertThat(codeReview).isNotNull();
      assertThat(codeReview.all).hasSize(3); // admin (reviewer/voter), user (voted ps1), user2 (voted ps2)
      assertThat(codeReview.all.stream().anyMatch(a -> a.permittedVotingRange != null)).isTrue();
    }
  }
}
