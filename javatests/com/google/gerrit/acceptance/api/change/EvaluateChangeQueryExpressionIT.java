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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestExtensions.TestSubmitRule;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.EvaluateChangeQueryExpressionResultInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.index.query.MatchResult;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeIsOperandFactory;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Integration tests for the {@link
 * com.google.gerrit.server.restapi.change.EvaluateChangeQueryExpression} REST endpoint.
 */
public class EvaluateChangeQueryExpressionIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void missingExpression() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.changes()
                    .id(changeIdentifier)
                    .evaluateChangeQueryExpression()
                    .withExpression(null)
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("expression is required");
  }

  @Test
  public void emptyExpression() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.changes()
                    .id(changeIdentifier)
                    .evaluateChangeQueryExpression()
                    .withExpression("")
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("expression is required");
  }

  @Test
  public void nonParseableExpression() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.changes()
                    .id(changeIdentifier)
                    .evaluateChangeQueryExpression()
                    .withExpression("[INVALID]")
                    .get());
    assertThat(exception).hasMessageThat().contains("invalid query expression:");
  }

  @Test
  public void unsupportedOperator() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.changes()
                    .id(changeIdentifier)
                    .evaluateChangeQueryExpression()
                    .withExpression("foo:bar")
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("invalid query expression: Unsupported operator foo:bar");
  }

  @Test
  public void singleAtomExpressionThatMatches() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    EvaluateChangeQueryExpressionResultInfo info =
        gApi.changes()
            .id(changeIdentifier)
            .evaluateChangeQueryExpression()
            .withExpression("is:open")
            .get();
    assertThat(info.status).isTrue();
    assertThat(info.passingAtoms).containsExactly("is:open");
    assertThat(info.failingAtoms).isEmpty();
    assertThat(info.atomExplanations).isNull();
  }

  @Test
  public void singleAtomExpressionThatDoesNotMatch() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    EvaluateChangeQueryExpressionResultInfo info =
        gApi.changes()
            .id(changeIdentifier)
            .evaluateChangeQueryExpression()
            .withExpression("is:closed")
            .get();
    assertThat(info.status).isFalse();
    assertThat(info.passingAtoms).isEmpty();
    assertThat(info.failingAtoms).containsExactly("is:closed");
    assertThat(info.atomExplanations).isNull();
  }

  @Test
  public void multiAtomExpressionThatMatches() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    changeOperations.change(changeIdentifier).newVote().codeReviewApproval().create();
    EvaluateChangeQueryExpressionResultInfo info =
        gApi.changes()
            .id(changeIdentifier)
            .evaluateChangeQueryExpression()
            .withExpression("is:open label:Code-Review+2")
            .get();
    assertThat(info.status).isTrue();
    assertThat(info.passingAtoms).containsExactly("is:open", "label:Code-Review+2");
    assertThat(info.failingAtoms).isEmpty();
    assertThat(info.atomExplanations).isNull();
  }

  @Test
  public void multiAtomExpressionThatDoesNotMatch() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    EvaluateChangeQueryExpressionResultInfo info =
        gApi.changes()
            .id(changeIdentifier)
            .evaluateChangeQueryExpression()
            .withExpression("is:closed label:Code-Review+2")
            .get();
    assertThat(info.status).isFalse();
    assertThat(info.passingAtoms).isEmpty();
    assertThat(info.failingAtoms).containsExactly("is:closed", "label:Code-Review+2");
    assertThat(info.atomExplanations).isNull();

    changeOperations.change(changeIdentifier).newVote().codeReviewApproval().create();
    info =
        gApi.changes()
            .id(changeIdentifier)
            .evaluateChangeQueryExpression()
            .withExpression("is:closed label:Code-Review+2")
            .get();
    assertThat(info.status).isFalse();
    assertThat(info.passingAtoms).containsExactly("label:Code-Review+2");
    assertThat(info.failingAtoms).containsExactly("is:closed");
    assertThat(info.atomExplanations).isNull();
  }

  @Test
  public void withAtomExplanation() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();

    // Register an "is:foo" predicate that never matches and has an atom explanation.
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new ChangeIsOperandFactory() {
                  @Override
                  public Predicate<ChangeData> create(ChangeQueryBuilder builder)
                      throws QueryParseException {
                    return new OperatorPredicate<>("is", "foo") {
                      @Override
                      public boolean isMatchable() {
                        return true;
                      }

                      @Override
                      public Matchable<ChangeData> asMatchable() {
                        return new Matchable<>() {
                          @Override
                          public boolean match(ChangeData object) {
                            return false;
                          }

                          @Override
                          public MatchResult matchResult(ChangeData changeData) {
                            return new MatchResult(match(changeData), "this atom never matches");
                          }

                          @Override
                          public int getCost() {
                            return 0;
                          }
                        };
                      }
                    };
                  }
                },
                "foo")) {
      String atom = String.format("is:foo_%s", ExtensionRegistry.PLUGIN_NAME);
      EvaluateChangeQueryExpressionResultInfo info =
          gApi.changes()
              .id(changeIdentifier)
              .evaluateChangeQueryExpression()
              .withExpression(atom)
              .get();
      assertThat(info.status).isFalse();
      assertThat(info.passingAtoms).isEmpty();
      assertThat(info.failingAtoms).containsExactly(atom);
      assertThat(info.atomExplanations).containsExactly(atom, "this atom never matches");

      info =
          gApi.changes()
              .id(changeIdentifier)
              .evaluateChangeQueryExpression()
              .withExpression("is:open " + atom)
              .get();
      assertThat(info.status).isFalse();
      assertThat(info.passingAtoms).containsExactly("is:open");
      assertThat(info.failingAtoms).containsExactly(atom);
      assertThat(info.atomExplanations).containsExactly(atom, "this atom never matches");
    }
  }

  @Test
  public void evaluatngIsSubmittableInvokesSubmitRulesOnce() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    changeOperations.change(changeIdentifier).newVote().codeReviewApproval().create();

    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      EvaluateChangeQueryExpressionResultInfo info =
          gApi.changes()
              .id(changeIdentifier)
              .evaluateChangeQueryExpression()
              .withExpression("is:submittable")
              .get();
      assertThat(info.status).isTrue();
      assertThat(info.passingAtoms).containsExactly("is:submittable");
      assertThat(info.failingAtoms).isEmpty();
      assertThat(info.atomExplanations).isNull();
    }
    assertThat(testSubmitRule.count()).isEqualTo(1);
  }

  @Test
  public void evaluatingIsSubmittableUsingTheIndexDoesntInvokeSubmitRules() throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    changeOperations.change(changeIdentifier).newVote().codeReviewApproval().create();

    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      EvaluateChangeQueryExpressionResultInfo info =
          gApi.changes()
              .id(changeIdentifier)
              .evaluateChangeQueryExpression()
              .withExpression("is:submittable")
              .useIndex()
              .get();
      assertThat(info.status).isTrue();
      assertThat(info.passingAtoms).containsExactly("is:submittable");
      assertThat(info.failingAtoms).isEmpty();
      assertThat(info.atomExplanations).isNull();
    }
    assertThat(testSubmitRule.count()).isEqualTo(0);
  }

  @Test
  public void
      evaluatingExpressionThatDoesntRequireCheckingTheChangeSubmittabilityDoesntInvokesSubmitRules()
          throws Exception {
    ChangeIdentifier changeIdentifier = changeOperations.newChange().create();
    changeOperations.change(changeIdentifier).newVote().codeReviewApproval().create();

    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      EvaluateChangeQueryExpressionResultInfo info =
          gApi.changes()
              .id(changeIdentifier)
              .evaluateChangeQueryExpression()
              .withExpression("is:open")
              .get();
      assertThat(info.status).isTrue();
      assertThat(info.passingAtoms).containsExactly("is:open");
      assertThat(info.failingAtoms).isEmpty();
      assertThat(info.atomExplanations).isNull();
    }
    assertThat(testSubmitRule.count()).isEqualTo(0);
  }
}
