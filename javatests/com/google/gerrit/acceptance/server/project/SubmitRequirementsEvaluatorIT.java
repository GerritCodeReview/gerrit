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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsEvaluatorIT extends AbstractDaemonTest {
  @Inject SubmitRequirementsEvaluator evaluator;

  private ChangeData changeData;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result pushResult =
        createChange(testRepo, "refs/heads/master", "Fix a bug", "file.txt", "content", "topic");
    changeData = pushResult.getChange();
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

    assertThat(result.getPassingAtoms())
        .containsExactly(
            PredicateResult.builder()
                .predicateString(String.format("project:%s", project.get()))
                .status(true)
                .build(),
            PredicateResult.builder()
                .predicateString("message:\"Fix a bug\"")
                .status(true)
                .build());

    assertThat(result.getFailingAtoms())
        .containsExactly(
            PredicateResult.builder()
                // TODO(ghareeb): querying "branch:" creates a RefPredicate. Fix names so that they
                // match
                .predicateString(String.format("ref:refs/heads/foo"))
                .status(false)
                .build());
  }
}
