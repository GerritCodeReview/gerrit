// Copyright (C) 2022 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseTimezone;
import com.google.gerrit.acceptance.VerifyNoPiiInChangeNotes;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseTimezone(timezone = "US/Eastern")
@VerifyNoPiiInChangeNotes(true)
public class SubmitRequirementPredicateIT extends AbstractDaemonTest {

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private SubmitRequirementsEvaluator submitRequirementsEvaluator;
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private AccountOperations accountOperations;

  private final LabelType label =
      label("Custom-Label", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));

  private final LabelType pLabel =
      label("Custom-Label2", value(1, "Positive"), value(0, "No score"));

  @Before
  public void setUp() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(label.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(-1, 1))
        .add(allowLabel(pLabel.getName()).ref("refs/heads/*").group(ANONYMOUS_USERS).range(0, 1))
        .update();
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(label);
      u.getConfig().upsertLabelType(pLabel);
      u.save();
    }
  }

  @Test
  public void distinctVoters_sameUserVotesOnDifferentLabels_fails() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);

    // Same user votes on both labels
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_distinctUsersOnDifferentLabels_passes() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyMaxVotesRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MAX,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyMinVotesRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", -1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MIN,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(c1.toString()).current().review(ReviewInput.reject());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=MIN,count>1\"", c1);
  }

  @Test
  public void distinctVoters_onlyExactValueRespected() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    requestScopeOperations.setApiUser(admin.id());
    approve(c1.toString());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],value=1,count>1\"", c1);
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],value=1,count>1\"", c1);
  }

  @Test
  public void distinctVoters_valueIsOptional() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", -1));
    requestScopeOperations.setApiUser(admin.id());
    assertNotMatching("distinctvoters:\"[Code-Review,Custom-Label],count>1\"", c1);
    recommend(c1.toString());
    assertMatching("distinctvoters:\"[Code-Review,Custom-Label],count>1\"", c1);
  }

  @Test
  public void distinctVoters_moreThanTwoLabels() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label2", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>1\"", c1);
  }

  @Test
  public void distinctVoters_moreThanTwoLabels_moreThanTwoUsers() throws Exception {
    Change.Id c1 = changeOperations.newChange().project(project).create();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label2", 1));
    requestScopeOperations.setApiUser(admin.id());
    recommend(c1.toString());
    assertNotMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>2\"", c1);
    Account.Id tester = accountOperations.newAccount().create();
    requestScopeOperations.setApiUser(tester);
    gApi.changes()
        .id(c1.toString())
        .current()
        .review(ReviewInput.create().label("Custom-Label", 1));
    assertMatching(
        "distinctvoters:\"[Code-Review,Custom-Label,Custom-Label2],value=1,count>2\"", c1);
  }

  private void assertMatching(String requirement, Change.Id change) {
    assertThat(evaluate(requirement, change).status())
        .isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
  }

  private void assertNotMatching(String requirement, Change.Id change) {
    assertThat(evaluate(requirement, change).status())
        .isEqualTo(SubmitRequirementExpressionResult.Status.FAIL);
  }

  private SubmitRequirementExpressionResult evaluate(String requirement, Change.Id change) {
    ChangeData cd = changeDataFactory.create(project, change);
    return submitRequirementsEvaluator.evaluateExpression(
        SubmitRequirementExpression.create(requirement), cd);
  }
}
