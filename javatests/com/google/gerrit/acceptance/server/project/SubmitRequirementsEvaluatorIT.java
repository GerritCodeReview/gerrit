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

import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsEvaluatorIT extends AbstractDaemonTest {
  @Inject SubmitRequirementsEvaluator evaluator;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;

  @Test
  public void singleAtomExpression() throws Exception {
    createBranch(BranchNameKey.create(project, "refs/heads/foo"));

    ChangeInput input = new ChangeInput();
    input.branch = "refs/heads/foo";
    input.project = project.get();
    input.subject = "Fix a bug";
    ChangeInfo changeInfo = gApi.changes().create(input).get();

    ChangeData changeData =
        changeQueryProvider.get().byLegacyChangeId(Change.id(changeInfo._number)).stream()
            .collect(MoreCollectors.onlyElement());

    indexer.index(changeData);

    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create("branch:refs/heads/foo");
    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

    assertThat(result.status()).isEqualTo(true);
  }

  @Test
  public void compositeExpression() throws Exception {
    ChangeInput input = new ChangeInput();
    input.branch = "refs/heads/master";
    input.subject = "Fix bug 23";
    input.project = project.get();
    ChangeInfo changeInfo = gApi.changes().create(input).get();

    ChangeData changeData =
        changeQueryProvider.get().byLegacyChangeId(Change.id(changeInfo._number)).stream()
            .collect(MoreCollectors.onlyElement());
    indexer.index(changeData);

    SubmitRequirementExpression expression =
        SubmitRequirementExpression.create(
            String.format(
                "(project:%s AND branch:refs/heads/foo) OR message:\"Fix bug\"", project.get()));

    SubmitRequirementExpressionResult result = evaluator.evaluateExpression(expression, changeData);

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
}
