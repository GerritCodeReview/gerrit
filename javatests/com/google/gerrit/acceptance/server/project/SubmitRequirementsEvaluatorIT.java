// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsEvaluatorIT extends AbstractDaemonTest {
  @Inject SubmitRequirementsEvaluator evaluator;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;

  private ChangeData changeData;
  private String changeId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result pushResult =
        createChange(testRepo, "refs/heads/master", "Fix a bug", "file.txt", "content", "topic");
    changeData = pushResult.getChange();
    changeId = pushResult.getChangeId();
  }

  @Test
  public void invalidExpression() throws Exception {
    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create("invalid_field:invalid_value");
    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

    assertThat(result.status()).isEqualTo(Status.ERROR);
    assertThat(result.errorMessage().get())
        .isEqualTo("Unsupported operator invalid_field:invalid_value");
  }

  @Test
  public void expressionWithPassingPredicate() throws Exception {
    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create("branch:refs/heads/master");
    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

    assertThat(result.status()).isEqualTo(Status.PASS);
    assertThat(result.errorMessage()).isEqualTo(Optional.empty());
  }

  @Test
  public void expressionWithFailingPredicate() throws Exception {
    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create("branch:refs/heads/foo");
    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

    assertThat(result.status()).isEqualTo(Status.FAIL);
    assertThat(result.errorMessage()).isEqualTo(Optional.empty());
  }

  @Test
  public void compositeExpression() throws Exception {
    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create(
            String.format(
                "(project:%s AND branch:refs/heads/foo) OR message:\"Fix a bug\"", project.get()));

    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

    assertThat(result.status()).isEqualTo(Status.PASS);

    assertThat(result.passingAtoms())
        .containsExactly(String.format("project:%s", project.get()), "message:\"Fix a bug\"");

    assertThat(result.failingAtoms()).containsExactly(String.format("branch:refs/heads/foo"));
  }

  @Test
  public void submitRequirementIsNotApplicable_whenApplicabilityExpressionIsFalse()
      throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:non-existent-project",
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.NOT_APPLICABLE);
  }

  @Test
  public void submitRequirementIsSatisfied_whenSubmittabilityExpressionIsTrue() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "message:\"Fix a bug\"",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
  }

  @Test
  public void submitRequirementIsUnsatisfied_whenSubmittabilityExpressionIsFalse()
      throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "label:\"Code-Review=+2\"",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.UNSATISFIED);
    assertThat(result.submittabilityExpressionResult().failingAtoms())
        .containsExactly("label:\"Code-Review=+2\"");
  }

  @Test
  public void submitRequirementIsOverridden_whenOverrideExpressionIsTrue() throws Exception {
    addLabel("build-cop-override");
    voteLabel(changeId, "build-cop-override", 1);

    // Reload change data after applying the vote
    changeData =
        changeQueryProvider.get().byLegacyChangeId(changeData.getId()).stream()
            .collect(MoreCollectors.onlyElement());

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "label:\"Code-Review=+2\"",
            /* overrideExpr= */ "label:\"build-cop-override=+1\"");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.OVERRIDDEN);
  }

  @Test
  public void submitRequirementIsError_whenApplicabilityExpressionHasInvalidSyntax()
      throws Exception {
    addLabel("build-cop-override");

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "invalid_field:invalid_value",
            /* submittabilityExpr= */ "label:\"Code-Review=+2\"",
            /* overrideExpr= */ "label:\"build-cop-override=+1\"");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.ERROR);
    assertThat(result.applicabilityExpressionResult().get().errorMessage().get())
        .isEqualTo("Unsupported operator invalid_field:invalid_value");
  }

  @Test
  public void submitRequirementIsError_whenSubmittabilityExpressionHasInvalidSyntax()
      throws Exception {
    addLabel("build-cop-override");

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "invalid_field:invalid_value",
            /* overrideExpr= */ "label:\"build-cop-override=+1\"");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.ERROR);
    assertThat(result.submittabilityExpressionResult().errorMessage().get())
        .isEqualTo("Unsupported operator invalid_field:invalid_value");
  }

  @Test
  public void submitRequirementIsError_whenOverrideExpressionHasInvalidSyntax() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "label:\"Code-Review=+2\"",
            /* overrideExpr= */ "invalid_field:invalid_value");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.ERROR);
    assertThat(result.overrideExpressionResult().get().errorMessage().get())
        .isEqualTo("Unsupported operator invalid_field:invalid_value");
  }

  @Test
  public void byPureRevert() throws Exception {
    testRepo.reset("HEAD~1");
    PushOneCommit.Result pushResult =
        createChange(testRepo, "refs/heads/master", "Fix a bug", "file.txt", "content", "topic");
    changeData = pushResult.getChange();
    changeId = pushResult.getChangeId();

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "is:pure-revert",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.UNSATISFIED);
    approve(changeId);
    gApi.changes().id(changeId).current().submit();

    ChangeInfo changeInfo = gApi.changes().id(changeId).revert().get();
    String revertId = Integer.toString(changeInfo._number);
    ChangeData revertChangeData =
        changeQueryProvider.get().byLegacyChangeId(Change.Id.parse(revertId)).get(0);
    result = evaluator.evaluateRequirement(sr, revertChangeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
  }

  private void voteLabel(String changeId, String labelName, int score) throws RestApiException {
    gApi.changes().id(changeId).current().review(new ReviewInput().label(labelName, score));
  }

  private void addLabel(String labelName) throws Exception {
    configLabel(
        project,
        labelName,
        LabelFunction.NO_OP,
        value(1, "ok"),
        value(0, "No score"),
        value(-1, "Needs work"));

    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(labelName).ref("refs/heads/master").group(REGISTERED_USERS).range(-1, +1))
        .update();
  }

  private SubmitRequirement createSubmitRequirement(
      @Nullable String applicabilityExpr,
      String submittabilityExpr,
      @Nullable String overrideExpr) {
    return SubmitRequirement.builder()
        .setName("sr-name")
        .setDescription(Optional.of("sr-description"))
        .setApplicabilityExpression(SubmitRequirementExpression.of(applicabilityExpr))
        .setSubmittabilityExpression(SubmitRequirementExpression.create(submittabilityExpr))
        .setOverrideExpression(SubmitRequirementExpression.of(overrideExpr))
        .setAllowOverrideInChildProjects(false)
        .build();
  }
}
