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
import static com.google.gerrit.acceptance.ExtensionRegistry.PLUGIN_NAME;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
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
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.project.SubmitRequirementEvaluationException;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeIsOperandFactory;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class SubmitRequirementsEvaluatorIT extends AbstractDaemonTest {
  @Inject SubmitRequirementsEvaluator evaluator;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeOperations changeOperations;

  private ChangeData changeData;
  private String changeId;

  private static final String FILE_NAME = "file,txt";
  private static final String CONTENT = "line 1\nline 2\n line 3\n";

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
  public void throwingSubmitRequirementPredicate() throws Exception {
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                new ThrowingSubmitRequirementPredicate(),
                ThrowingSubmitRequirementPredicate.OPERAND)) {
      SubmitRequirementExpression expression =
          SubmitRequirementExpression.create(
              String.format("is:%s_%s", ThrowingSubmitRequirementPredicate.OPERAND, PLUGIN_NAME));
      SubmitRequirementExpressionResult result =
          evaluator.evaluateExpression(expression, changeData);
      assertThat(result.status()).isEqualTo(Status.ERROR);
      assertThat(result.errorMessage().get())
          .isEqualTo(ThrowingSubmitRequirementPredicate.ERROR_MESSAGE);
    }
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
  public void globalSubmitRequirementEvaluated() throws Exception {
    SubmitRequirement globalSubmitRequirement =
        createSubmitRequirement(
            /*name=*/ "global-config-requirement",
            /* applicabilityExpr= */ "project:" + project.get(),
            /*submittabilityExpr= */ "is:true",
            /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
            false);
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      SubmitRequirement projectSubmitRequirement =
          createSubmitRequirement(
              /*name=*/ "project-config-requirement",
              /* applicabilityExpr= */ "project:" + project.get(),
              /*submittabilityExpr= */ "is:true",
              /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
              false);
      configSubmitRequirement(project, projectSubmitRequirement);
      Map<SubmitRequirement, SubmitRequirementResult> results =
          evaluator.evaluateAllRequirements(changeData);
      assertThat(results).hasSize(2);
      assertThat(results.get(globalSubmitRequirement).status())
          .isEqualTo(SubmitRequirementResult.Status.SATISFIED);
      assertThat(results.get(projectSubmitRequirement).status())
          .isEqualTo(SubmitRequirementResult.Status.SATISFIED);
    }
  }

  @Test
  public void
      globalSubmitRequirement_duplicateInProjectConfig_overrideAllowed_projectResultReturned()
          throws Exception {
    SubmitRequirement globalSubmitRequirement =
        createSubmitRequirement(
            /*name=*/ "config-requirement",
            /* applicabilityExpr= */ "project:" + project.get(),
            /*submittabilityExpr= */ "is:true",
            /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
            true);
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      SubmitRequirement projectSubmitRequirement =
          createSubmitRequirement(
              /*name=*/ "config-requirement",
              /* applicabilityExpr= */ "project:" + project.get(),
              /*submittabilityExpr= */ "is:true",
              /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
              false);
      configSubmitRequirement(project, projectSubmitRequirement);
      Map<SubmitRequirement, SubmitRequirementResult> results =
          evaluator.evaluateAllRequirements(changeData);
      assertThat(results).hasSize(1);
      assertThat(results.get(projectSubmitRequirement).status())
          .isEqualTo(SubmitRequirementResult.Status.SATISFIED);
    }
  }

  @Test
  public void
      globalSubmitRequirement_duplicateInProjectConfig_overrideNotAllowedAllowed_globalResultReturned()
          throws Exception {
    SubmitRequirement globalSubmitRequirement =
        createSubmitRequirement(
            /*name=*/ "config-requirement",
            /* applicabilityExpr= */ "project:" + project.get(),
            /*submittabilityExpr= */ "is:true",
            /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
            false);
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      SubmitRequirement projectSubmitRequirement =
          createSubmitRequirement(
              /*name=*/ "config-requirement",
              /* applicabilityExpr= */ "project:" + project.get(),
              /*submittabilityExpr= */ "is:true",
              /* overrideExpr= */ "", /*allowOverrideInChildProjects*/
              false);
      configSubmitRequirement(project, projectSubmitRequirement);
      Map<SubmitRequirement, SubmitRequirementResult> results =
          evaluator.evaluateAllRequirements(changeData);
      assertThat(results).hasSize(1);
      assertThat(results.get(globalSubmitRequirement).status())
          .isEqualTo(SubmitRequirementResult.Status.SATISFIED);
    }
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
  public void submitRequirement_alwaysNotApplicable() {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "is:false",
            /* submittabilityExpr= */ "is:false", // redundant
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.NOT_APPLICABLE);
  }

  @Test
  public void submitRequirement_alwaysApplicable() {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "is:true",
            /* submittabilityExpr= */ "is:true",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants.GERRIT_BACKEND_REQUEST_FEATURE_SR_EXPRESSIONS_NOT_EVALUATED)
  public void submittabilityAndOverrideNotEvaluated_whenApplicabilityIsFalse() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:non-existent-project",
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.NOT_APPLICABLE);
    assertThat(result.applicabilityExpressionResult().get().status()).isEqualTo(Status.FAIL);
    assertThat(result.submittabilityExpressionResult().get().status())
        .isEqualTo(Status.NOT_EVALUATED);
    assertThat(result.submittabilityExpressionResult().get().expression().expressionString())
        .isEqualTo("message:\"Fix bug\"");
    assertThat(result.overrideExpressionResult().isPresent()).isFalse();
  }

  @Test
  public void submittabilityAndOverrideAreEmpty_whenApplicabilityIsFalse() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:non-existent-project",
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.NOT_APPLICABLE);
    assertThat(result.applicabilityExpressionResult().get().status()).isEqualTo(Status.FAIL);
    assertThat(result.submittabilityExpressionResult().isPresent()).isFalse();
    assertThat(result.overrideExpressionResult().isPresent()).isFalse();
  }

  @Test
  public void submittabilityAndOverrideEvaluated_whenApplicabilityIsEmpty() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ null,
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "label:\"build-cop-override=-1\"");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
    assertThat(result.applicabilityExpressionResult().isPresent()).isFalse();
    assertThat(result.submittabilityExpressionResult().get().status()).isEqualTo(Status.PASS);
    assertThat(result.overrideExpressionResult().get().status()).isEqualTo(Status.FAIL);
  }

  @Test
  public void submittabilityAndOverrideEvaluated_whenApplicabilityIsTrue() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "label:\"build-cop-override=-1\"");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);

    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
    assertThat(result.applicabilityExpressionResult().get().status()).isEqualTo(Status.PASS);
    assertThat(result.submittabilityExpressionResult().get().status()).isEqualTo(Status.PASS);
    assertThat(result.overrideExpressionResult().get().status()).isEqualTo(Status.FAIL);
  }

  @Test
  public void submittabilityIsEvaluated_whenOverrideApplies() throws Exception {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ null,
            /* submittabilityExpr= */ "message:\"Fix bug\"",
            /* overrideExpr= */ "project:" + project.get());

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, changeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.OVERRIDDEN);

    assertThat(result.applicabilityExpressionResult().isPresent()).isFalse();
    assertThat(result.submittabilityExpressionResult().get().status()).isEqualTo(Status.PASS);
    assertThat(result.overrideExpressionResult().get().status()).isEqualTo(Status.PASS);
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
    assertThat(result.submittabilityExpressionResult().get().failingAtoms())
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
    assertThat(result.submittabilityExpressionResult().get().errorMessage().get())
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
        changeQueryProvider.get().byLegacyChangeId(Change.Id.tryParse(revertId).get()).get(0);
    result = evaluator.evaluateRequirement(sr, revertChangeData);
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
  }

  @Test
  public void byAuthorEmail() throws Exception {
    TestAccount user2 =
        accountCreator.create("Foo", "user@example.com", "User", /* displayName = */ null);
    requestScopeOperations.setApiUser(user2.id());
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project.get(), "master", "Test Change")).get();
    ChangeData cd =
        changeQueryProvider
            .get()
            .byLegacyChangeId(Change.Id.tryParse(Integer.toString(info._number)).get())
            .get(0);

    // Match by email works
    checkSubmitRequirementResult(
        cd,
        /* submittabilityExpr= */ "authoremail:\"^.*@example\\.com\"",
        SubmitRequirementResult.Status.SATISFIED);
    checkSubmitRequirementResult(
        cd,
        /* submittabilityExpr= */ "authoremail:\"^user@.*\\.com\"",
        SubmitRequirementResult.Status.SATISFIED);

    // Match by name does not work
    checkSubmitRequirementResult(
        cd,
        /* submittabilityExpr= */ "authoremail:\"^Foo$\"",
        SubmitRequirementResult.Status.UNSATISFIED);
    checkSubmitRequirementResult(
        cd,
        /* submittabilityExpr= */ "authoremail:\"^User$\"",
        SubmitRequirementResult.Status.UNSATISFIED);
  }

  private void checkSubmitRequirementResult(
      ChangeData cd, String submittabilityExpr, SubmitRequirementResult.Status expectedStatus) {
    SubmitRequirement sr =
        createSubmitRequirement(
            /* applicabilityExpr= */ "project:" + project.get(),
            submittabilityExpr,
            /* overrideExpr= */ "");

    SubmitRequirementResult result = evaluator.evaluateRequirement(sr, cd);
    assertThat(result.status()).isEqualTo(expectedStatus);
  }

  @Test
  public void byFileEdits_deletedContent_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 2\n", ""))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='line 2'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_deletedContent_nonMatching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 1\n", ""))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='line 2'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_addedContent_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT + "line 4\n")
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='line 4'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_addedContent_nonMatching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT + "line 4\n")
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='line 5'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_addedFile_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file("new_file.txt")
            .content("content of the new file")
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^new.*\\\\.txt',withDiffContaining='of the new'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_addedFile_nonMatching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file("new_file.txt")
            .content("content of the new file")
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^new.*\\.txt',withDiffContaining='not_exist'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_modifiedContent_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='three'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_modifiedContent_nonMatching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.txt',withDiffContaining='ten'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_modifiedContentPattern_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^.*\\.txt',withDiffContaining='^.*th[rR]ee$'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_exactMatchingWithFilePath_matching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            String.format("file:\"'%s',withDiffContaining='three'\"", FILE_NAME));

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_exactMatchingWithFilePath_nonMatching() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'non_existent.txt',withDiffContaining='three'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_notMatchingWithFilePath() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    // commit edit only matches with files ending with ".java". Since our modified file name ends
    // with ".txt", the applicability expression will not match.
    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^.*\\.java',withDiffContaining='three'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_escapeSingleQuotes() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line 'three' is modified\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^.*\\.txt',withDiffContaining='line \\'three\\' is'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_doubleEscapeSingleQuote() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            // This will be written to the file as: line \'three\' is modified.
            .content(CONTENT.replace("line 3\n", "line \\'three\\' is modified\n"))
            .childOf()
            .change(parent)
            .create();

    // Users can still provide back-slashes in regexes by escaping them.
    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^.*\\.txt',withDiffContaining='line \\\\'three\\\\' is'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_escapeDoubleQuotes() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line \"three\" is modified\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^.*\\.txt',withDiffContaining='line \\\"three\\\" is'\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void byFileEdits_invalidSyntax() throws Exception {
    Change.Id parent = changeOperations.newChange().file(FILE_NAME).content(CONTENT).create();
    Change.Id childId =
        changeOperations
            .newChange()
            .file(FILE_NAME)
            .content(CONTENT.replace("line 3\n", "line three\n"))
            .childOf()
            .change(parent)
            .create();

    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create(
            "file:\"'^.*\\.txt',withDiffContaining=forgot single quotes\"");

    ChangeData childChangeData = changeQueryProvider.get().byLegacyChangeId(childId).get(0);
    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, childChangeData);
    // If the format is invalid, the operator falls back to the default operator of
    // ChangeQueryBuilder which does not match the change, i.e. returns false.
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void byFileEdits_invalidFilePattern() throws Exception {
    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'^**',withDiffContaining='content'\"");

    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, changeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.ERROR);
    assertThat(srResult.errorMessage().get()).isEqualTo("Invalid file pattern.");
  }

  @Test
  public void byFileEdits_invalidContentPattern() throws Exception {
    SubmitRequirementExpression exp =
        SubmitRequirementExpression.create("file:\"'fileName\\.txt',withDiffContaining='^**'\"");

    SubmitRequirementExpressionResult srResult = evaluator.evaluateExpression(exp, changeData);
    assertThat(srResult.status()).isEqualTo(SubmitRequirementExpressionResult.Status.ERROR);
    assertThat(srResult.errorMessage().get()).isEqualTo("Invalid content pattern.");
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
    return createSubmitRequirement(
        /*name= */ "sr-name",
        applicabilityExpr,
        submittabilityExpr,
        overrideExpr,
        /*allowOverrideInChildProjects=*/ false);
  }

  private SubmitRequirement createSubmitRequirement(
      String name,
      @Nullable String applicabilityExpr,
      String submittabilityExpr,
      @Nullable String overrideExpr,
      boolean allowOverrideInChildProjects) {
    return SubmitRequirement.builder()
        .setName(name)
        .setDescription(Optional.of("sr-description"))
        .setApplicabilityExpression(SubmitRequirementExpression.of(applicabilityExpr))
        .setSubmittabilityExpression(SubmitRequirementExpression.create(submittabilityExpr))
        .setOverrideExpression(SubmitRequirementExpression.of(overrideExpr))
        .setAllowOverrideInChildProjects(allowOverrideInChildProjects)
        .build();
  }

  /** Submit requirement predicate that always throws an error on match. */
  static class ThrowingSubmitRequirementPredicate extends SubmitRequirementPredicate
      implements ChangeIsOperandFactory {

    public static final String OPERAND = "throwing-predicate";

    public static final String ERROR_MESSAGE = "Error is storage";

    public ThrowingSubmitRequirementPredicate() {
      super("is", OPERAND);
    }

    @Override
    public boolean match(ChangeData object) {
      throw new SubmitRequirementEvaluationException(ERROR_MESSAGE);
    }

    @Override
    public int getCost() {
      return 0;
    }

    @Override
    public Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException {
      return this;
    }
  }
}
