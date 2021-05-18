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
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsEvaluatorIT extends AbstractDaemonTest {
  @Inject SubmitRequirementsEvaluator evaluator;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;

  @Test
  public void singleAtomExpression() throws Exception {
    createBranch(BranchNameKey.create(project, "refs/heads/foo"));

    ChangeInfo changeInfo = createChange("refs/heads/foo", "Fix bug 23");
    ChangeData changeData = getChangeData(changeInfo._number);

    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create("branch:refs/heads/foo");
    SubmitRequirementExpressionResult result =
        evaluator.evaluateExpression(Optional.of(expression), changeData).get();

    assertThat(result.status()).isEqualTo(true);
  }

  @Test
  public void compositeExpression() throws Exception {
    ChangeInfo changeInfo = createChange("refs/heads/master", "Fix bug 23");
    ChangeData changeData = getChangeData(changeInfo._number);

    Optional<SubmitRequirementExpression> expression =
        SubmitRequirementExpression.of(
            String.format(
                "(project:%s AND branch:refs/heads/foo) OR message:\"Fix bug\"", project.get()));

    SubmitRequirementExpressionResult result =
        evaluator.evaluateExpression(expression, changeData).get();

    assertThat(result.status()).isEqualTo(true);

    assertThat(result.getPassingAtoms())
        .containsExactly(
            PredicateResult.builder()
                .predicateString(String.format("project:%s", project.get()))
                .status(true)
                .build(),
            PredicateResult.builder().predicateString("message:\"Fix bug\"").status(true).build());

    assertThat(result.getFailingAtoms())
        .containsExactly(
            PredicateResult.builder()
                .predicateString(String.format("ref:refs/heads/foo"))
                .status(false)
                .build());
  }

  @Test
  public void submitRequirementIsNotApplicable_WhenApplicabilityExpressionIsFalse()
      throws Exception {
    ChangeInfo changeInfo = createChange("refs/heads/master", "Fix bug 23");

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:non-existent-project",
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "");

    ChangeData cd = getChangeData(changeInfo._number);

    SubmitRequirementResult result = evaluator.evaluate(sr, cd);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.NOT_APPLICABLE);
  }

  @Test
  public void submitRequirementIsSatisfied_WhenSubmittabilityExpressionIsTrue() throws Exception {
    ChangeInfo changeInfo = createChange("refs/heads/master", "Fix bug 23");

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "");

    ChangeData cd = getChangeData(changeInfo._number);

    SubmitRequirementResult result = evaluator.evaluate(sr, cd);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
  }

  @Test
  public void submitRequirementIsUnsatisfied_WhenSubmittabilityExpressionIsFalse()
      throws Exception {
    ChangeInfo changeInfo = createChange("refs/heads/master", "Fix bug 23");

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "label:\"code-review=+2\"",
            /* overrideExpr= */ "");

    ChangeData cd = getChangeData(changeInfo._number);

    SubmitRequirementResult result = evaluator.evaluate(sr, cd);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.UNSATISFIED);
  }

  @Test
  public void submitRequirementIsOverridden_WhenOverrideExpressionIsTrue() throws Exception {
    addLabel("build-cop-override");
    ChangeInfo changeInfo = createChange("refs/heads/master", "Fix bug 23");

    voteLabel(changeInfo.changeId, "build-cop-override", 1);

    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "label:\"code-review=+2\"",
            /* overrideExpr= */ "label:\"build-cop-override=+1\"");

    ChangeData cd = getChangeData(changeInfo._number);

    SubmitRequirementResult result = evaluator.evaluate(sr, cd);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.OVERRIDDEN);
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

  private ChangeInfo createChange(String branch, String subject) throws Exception {
    ChangeInput input = new ChangeInput();
    input.branch = branch;
    input.subject = subject;
    input.project = project.get();
    return gApi.changes().create(input).get();
  }

  private SubmitRequirement createSubmitRequirement(
      @Nullable String applicability, String submittability, @Nullable String override) {
    return SubmitRequirement.builder()
        .setName("sr-name")
        .setDescription(Optional.of("sr-description"))
        .setApplicabilityExpression(SubmitRequirementExpression.of(applicability))
        .setSubmittabilityExpression(SubmitRequirementExpression.create(submittability))
        .setOverrideExpression(SubmitRequirementExpression.of(override))
        .setAllowOverrideInChildProjects(false)
        .build();
  }

  private ChangeData getChangeData(Integer changeNumber) {
    ChangeData changeData =
        changeQueryProvider.get().byLegacyChangeId(Change.id(changeNumber)).stream()
            .collect(MoreCollectors.onlyElement());
    indexer.index(changeData);
    changeData =
        changeQueryProvider.get().byLegacyChangeId(Change.id(changeNumber)).stream()
            .collect(MoreCollectors.onlyElement());
    return changeData;
  }
}
