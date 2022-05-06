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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseTimezone;
import com.google.gerrit.acceptance.VerifyNoPiiInChangeNotes;
import com.google.gerrit.acceptance.testsuite.change.IndexOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.LegacySubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.common.SubmitRequirementExpressionInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.httpd.raw.IndexPreloadingUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

@NoHttpd
@UseTimezone(timezone = "US/Eastern")
@VerifyNoPiiInChangeNotes(true)
public class SubmitRequirementIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private IndexOperations.Change changeIndexOperations;

  @Test
  public void submitRecords() throws Exception {
    PushOneCommit.Result r = createChange();
    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      String changeId = r.getChangeId();

      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRecords).hasSize(2);
      // Check the default submit record for the code-review label
      SubmitRecordInfo codeReviewRecord = Iterables.get(change.submitRecords, 0);
      assertThat(codeReviewRecord.ruleName).isEqualTo("gerrit~DefaultSubmitRule");
      assertThat(codeReviewRecord.status).isEqualTo(SubmitRecordInfo.Status.NOT_READY);
      assertThat(codeReviewRecord.labels).hasSize(1);
      SubmitRecordInfo.Label label = Iterables.getOnlyElement(codeReviewRecord.labels);
      assertThat(label.label).isEqualTo("Code-Review");
      assertThat(label.status).isEqualTo(SubmitRecordInfo.Label.Status.NEED);
      assertThat(label.appliedBy).isNull();
      // Check the custom test record created by the TestSubmitRule
      SubmitRecordInfo testRecord = Iterables.get(change.submitRecords, 1);
      assertThat(testRecord.ruleName).isEqualTo("testSubmitRule");
      assertThat(testRecord.status).isEqualTo(SubmitRecordInfo.Status.OK);
      assertThat(testRecord.requirements)
          .containsExactly(new LegacySubmitRequirementInfo("OK", "fallback text", "type"));
      assertThat(testRecord.labels).hasSize(1);
      SubmitRecordInfo.Label testLabel = Iterables.getOnlyElement(testRecord.labels);
      assertThat(testLabel.label).isEqualTo("label");
      assertThat(testLabel.status).isEqualTo(SubmitRecordInfo.Label.Status.OK);
      assertThat(testLabel.appliedBy).isNull();

      voteLabel(changeId, "Code-Review", 2);
      // Code review record is satisfied after voting +2
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRecords).hasSize(2);
      codeReviewRecord = Iterables.get(change.submitRecords, 0);
      assertThat(codeReviewRecord.ruleName).isEqualTo("gerrit~DefaultSubmitRule");
      assertThat(codeReviewRecord.status).isEqualTo(SubmitRecordInfo.Status.OK);
      assertThat(codeReviewRecord.labels).hasSize(1);
      label = Iterables.getOnlyElement(codeReviewRecord.labels);
      assertThat(label.label).isEqualTo("Code-Review");
      assertThat(label.status).isEqualTo(SubmitRecordInfo.Label.Status.OK);
      assertThat(label.appliedBy._accountId).isEqualTo(admin.id().get());
    }
  }

  @Test
  public void checkSubmitRequirement_satisfied() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    SubmitRequirementInput in =
        createSubmitRequirementInput(
            "Code-Review", /* submittabilityExpression= */ "label:Code-Review=+2");

    SubmitRequirementResultInfo result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.UNSATISFIED);

    voteLabel(changeId, "Code-Review", 2);
    result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.SATISFIED);
  }

  @Test
  public void checkSubmitRequirement_notApplicable() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    SubmitRequirementInput in =
        createSubmitRequirementInput(
            "Code-Review",
            /* applicableIf= */ "branch:non-existent",
            /* submittableIf= */ "label:Code-Review=+2",
            /* overrideIf= */ null);

    SubmitRequirementResultInfo result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.NOT_APPLICABLE);

    voteLabel(changeId, "Code-Review", 2);
    result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.NOT_APPLICABLE);
  }

  @Test
  public void checkSubmitRequirement_overridden() throws Exception {
    configLabel("Override-Label", LabelFunction.NO_OP); // label function has no effect
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("Override-Label")
                .ref("refs/heads/master")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    SubmitRequirementInput in =
        createSubmitRequirementInput(
            "Code-Review",
            /* applicableIf= */ null,
            /* submittableIf= */ "label:Code-Review=+2",
            /* overrideIf= */ "label:Override-Label=+1");

    SubmitRequirementResultInfo result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.UNSATISFIED);

    voteLabel(changeId, "Code-Review", 2);
    result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.SATISFIED);

    voteLabel(changeId, "Override-Label", 1);
    result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.OVERRIDDEN);
  }

  @Test
  public void checkSubmitRequirement_error() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    SubmitRequirementInput in =
        createSubmitRequirementInput("Code-Review", /* submittabilityExpression= */ "!!!");

    SubmitRequirementResultInfo result = gApi.changes().id(changeId).checkSubmitRequirement(in);
    assertThat(result.status).isEqualTo(SubmitRequirementResultInfo.Status.ERROR);
  }

  @Test
  public void submitRequirement_withLabelEqualsMax() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=MAX"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_withLabelEqualsMax_fromNonUploader() throws Exception {
    configLabel("my-label", LabelFunction.NO_OP); // label function has no effect
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("my-label").ref("refs/heads/master").group(REGISTERED_USERS).range(-1, 1))
        .update();
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("my-label")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:my-label=MAX,user=non_uploader"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    // The second requirement is coming from the legacy code-review label function
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.UNSATISFIED, /* isLegacy= */ false);

    // Voting with a max vote as the uploader will not satisfy the submit requirement.
    voteLabel(changeId, "my-label", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.UNSATISFIED, /* isLegacy= */ false);

    // Voting as a non-uploader will satisfy the submit requirement.
    requestScopeOperations.setApiUser(user.id());
    voteLabel(changeId, "my-label", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_withLabelEqualsMinBlockingSubmission() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("-label:Code-Review=MIN"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Requirement is satisfied because there are no votes
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement (coming from the label function definition) is not satisfied. We return
    // both legacy and non-legacy requirements in this case since their statuses are not identical.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);

    voteLabel(changeId, "Code-Review", -1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Requirement is still satisfied because -1 is not the max negative value
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);

    voteLabel(changeId, "Code-Review", -2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // Requirement is now unsatisfied because -2 is the max negative value
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_withMaxWithBlock_ignoringSelfApproval() throws Exception {
    configLabel("my-label", LabelFunction.MAX_WITH_BLOCK);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("my-label").ref("refs/heads/master").group(REGISTERED_USERS).range(-1, 1))
        .update();

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("my-label")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create(
                    "label:my-label=MAX,user=non_uploader -label:my-label=MIN"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // Create the change as admin
    requestScopeOperations.setApiUser(admin.id());
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    // Admin (a.k.a uploader) adds a -1 min vote. This is going to block submission.
    voteLabel(changeId, "my-label", -1);
    ChangeInfo change = gApi.changes().id(changeId).get();
    // The other requirement is coming from the code-review label function
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.UNSATISFIED, /* isLegacy= */ false);

    // user (i.e. non_uploader) votes 1. Requirement is still blocking because of -1 of uploader.
    requestScopeOperations.setApiUser(user.id());
    voteLabel(changeId, "my-label", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.UNSATISFIED, /* isLegacy= */ false);

    // Admin (a.k.a uploader) removes -1. Now requirement is fulfilled.
    requestScopeOperations.setApiUser(admin.id());
    voteLabel(changeId, "my-label", 0);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "my-label", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_withLabelEqualsAny() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=ANY"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Legacy and non-legacy requirements have mismatching status. Both are returned from the API.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirementIsSatisfied_whenSubmittabilityExpressionIsFulfilled()
      throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Verified")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Verified=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Verified", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Verified", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirementIsNotApplicable_whenApplicabilityExpressionIsNotFulfilled()
      throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setApplicabilityExpression(SubmitRequirementExpression.of("project:foo"))
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.NOT_APPLICABLE, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirementIsOverridden_whenOverrideExpressionIsFulfilled() throws Exception {
    configLabel("build-cop-override", LabelFunction.NO_BLOCK);
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("build-cop-override")
                .ref("refs/heads/master")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "build-cop-override", 1);

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_overriddenInChildProjectWithStricterRequirement() throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Override submit requirement in child project (requires Code-Review=+2 instead of +1)
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_overriddenInChildProjectWithLessStrictRequirement()
      throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Override submit requirement in child project (requires Code-Review=+1 instead of +2)
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement: override in child project applies
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement that is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_overriddenInChildProjectAsDisabled() throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Custom-Requirement")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Override submit requirement in child project (requires Code-Review=+1 instead of +2)
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Custom-Requirement")
            .setApplicabilityExpression(SubmitRequirementExpression.of("is:false"))
            .setSubmittabilityExpression(SubmitRequirementExpression.create("is:false"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements,
        "Custom-Requirement",
        Status.NOT_APPLICABLE,
        /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_inheritedFromParentProject() throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_overriddenSRInParentProjectIsInheritedByChildProject()
      throws Exception {
    // Define submit requirement in root project.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Override submit requirement in parent project (requires Code-Review=+2 instead of +1).
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    Project.NameKey child = createProjectOverAPI("child", project, true, /* submitType= */ null);
    TestRepository<InMemoryRepository> childRepo = cloneProject(child);
    PushOneCommit.Result r = createChange(childRepo);
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_ignoredInChildProject_ifParentDoesNotAllowOverride()
      throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // Override submit requirement in child project (requires Code-Review=+2 instead of +1).
    // Will have no effect since parent does not allow override.
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement: override in child project was ignored
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_ignoredInChildProject_ifParentAddsSRThatDoesNotAllowOverride()
      throws Exception {
    // Submit requirement in child project (requires Code-Review=+1)
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement that is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);

    // Add stricter non-overridable submit requirement in parent project (requires Code-Review=+2,
    // instead of Code-Review=+1)
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_ignoredInChildProject_ifParentMakesSRNonOverridable()
      throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Override submit requirement in child project (requires Code-Review=+1 instead of +2)
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement: override in child project applies
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement that is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);

    // Disallow overriding the submit requirement in the parent project.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_ignoredInGrandChildProject_ifGrandParentDoesNotAllowOverride()
      throws Exception {
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    Project.NameKey grandChild =
        createProjectOverAPI("grandChild", project, true, /* submitType= */ null);

    // Override submit requirement in grand child project (requires Code-Review=+2 instead of +1).
    // Will have no effect since grand parent does not allow override.
    configSubmitRequirement(
        grandChild,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    TestRepository<InMemoryRepository> grandChildRepo = cloneProject(grandChild);
    PushOneCommit.Result r = createChange(grandChildRepo);
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement: override in grand child project was ignored
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_overrideOverideExpression() throws Exception {
    // Define submit requirement in root project.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Create Code-Review-Override label
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = "NoOp";
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    gApi.projects().name(project.get()).label("Code-Review-Override").create(input).get();

    // Allow to vote on the Code-Review-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Code-Review-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    // Override submit requirement in project (requires Code-Review-Override+1 as override instead
    // of build-cop-override+1).
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:Code-Review-Override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review-Override", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Code-Review-Override+1 was enough to fulfill the override expression of the requirement
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirementThatOverridesParentSubmitRequirementTakesEffectImmediately()
      throws Exception {
    // Define submit requirement in root project that ignores self approvals from the uploader.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=MAX,user=non_uploader"))
            .setAllowOverrideInChildProjects(true)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    // Apply a self approval from the uploader.
    voteLabel(changeId, "Code-Review", 2);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Code-Review+2 is ignored since it's a self approval from the uploader
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Already satisfied since it
    // doesn't ignore self approvals.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);

    // since the change is not submittable we expect the submit action to be not returned
    assertThat(gApi.changes().id(changeId).current().actions()).doesNotContainKey("submit");

    // Override submit requirement in project (allow uploaders to self approve).
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=MAX"))
            .setAllowOverrideInChildProjects(true)
            .build());

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // the self approval from the uploader is no longer ignored, hence the submit requirement is
    // satisfied now
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    // since the change is submittable now we expect the submit action to be returned
    Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();
    assertThat(actions).containsKey("submit");
    ActionInfo submitAction = actions.get("submit");
    assertThat(submitAction.enabled).isTrue();
  }

  @Test
  public void
      submitRequirementThatOverridesParentSubmitRequirementTakesEffectImmediately_staleIndex()
          throws Exception {
    // Define submit requirement in root project that ignores self approvals from the uploader.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:Code-Review=MAX,user=non_uploader"))
            .setAllowOverrideInChildProjects(true)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    // Apply a self approval from the uploader.
    voteLabel(changeId, "Code-Review", 2);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // Code-Review+2 is ignored since it's a self approval from the uploader
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Already satisfied since it
    // doesn't ignore self approvals.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);

    // since the change is not submittable we expect the submit action to be not returned
    assertThat(gApi.changes().id(changeId).current().actions()).doesNotContainKey("submit");

    // disable change index writes so that the change in the index gets stale when the new submit
    // requirement is added
    try (AutoCloseable ignored = changeIndexOperations.disableWrites()) {
      // Override submit requirement in project (allow uploaders to self approve).
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(
                  SubmitRequirementExpression.create("label:Code-Review=MAX"))
              .setAllowOverrideInChildProjects(true)
              .build());

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      // the self approval from the uploader is no longer ignored, hence the submit requirement is
      // satisfied now
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

      // since the change is submittable now we expect the submit action to be returned
      Map<String, ActionInfo> actions = gApi.changes().id(changeId).current().actions();
      assertThat(actions).containsKey("submit");
      ActionInfo submitAction = actions.get("submit");
      assertThat(submitAction.enabled).isTrue();
    }
  }

  @Test
  public void submitRequirement_partiallyOverriddenSRIsIgnored() throws Exception {
    // Create build-cop-override label
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.function = "NoOp";
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    gApi.projects().name(project.get()).label("build-cop-override").create(input).get();

    // Allow to vote on the build-cop-override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("build-cop-override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    // Define submit requirement in root project.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Create Code-Review-Override label
    gApi.projects().name(project.get()).label("Code-Review-Override").create(input).get();

    // Allow to vote on the Code-Review-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Code-Review-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    // Override submit requirement in project (requires Code-Review-Override+1 as override instead
    // of build-cop-override+1), but do not set all required properties (submittability expression
    // is missing). We update the project.config file directly in the remote repository, since
    // trying to push such a submit requirement would be rejected by the commit validation.
    projectOperations
        .project(project)
        .forInvalidation()
        .addProjectConfigUpdater(
            config ->
                config.setString(
                    ProjectConfig.SUBMIT_REQUIREMENT,
                    "Code-Review",
                    ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
                    "label:Code-Review-Override=+1"))
        .invalidate();

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review-Override", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // The override expression in the project is satisfied, but it's ignored since the SR is
    // incomplete.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "build-cop-override", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // The submit requirement is overridden now (the override expression in the child project is
    // ignored)
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_storedForClosedChanges() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      voteLabel(changeId, "Code-Review", 2);

      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      ChangeNotes notes = notesFactory.create(project, r.getChange().getId());

      SubmitRequirementResult result =
          notes.getSubmitRequirementsResult().stream().collect(MoreCollectors.onlyElement());
      assertSubmitRequirementResult(
          result,
          "Code-Review",
          SubmitRequirementResult.Status.SATISFIED,
          /* submitExpr= */ "label:Code-Review=MAX",
          SubmitRequirementExpressionResult.Status.PASS);

      // Adding comments does not affect the stored SRs.
      addComment(r.getChangeId(), /* file= */ "foo");
      notes = notesFactory.create(project, r.getChange().getId());
      result = notes.getSubmitRequirementsResult().stream().collect(MoreCollectors.onlyElement());
      assertSubmitRequirementResult(
          result,
          "Code-Review",
          SubmitRequirementResult.Status.SATISFIED,
          /* submitExpr= */ "label:Code-Review=MAX",
          SubmitRequirementExpressionResult.Status.PASS);
      assertThat(notes.getHumanComments()).hasSize(1);
    }
  }

  @Test
  public void submitRequirement_storedForAbandonedChanges() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      voteLabel(changeId, "Code-Review", 2);
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

      gApi.changes().id(r.getChangeId()).abandon();
      ChangeNotes notes = notesFactory.create(project, r.getChange().getId());
      SubmitRequirementResult result =
          notes.getSubmitRequirementsResult().stream().collect(MoreCollectors.onlyElement());
      assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
      assertThat(result.submittabilityExpressionResult().get().status())
          .isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
      assertThat(result.submittabilityExpressionResult().get().expression().expressionString())
          .isEqualTo("label:Code-Review=MAX");
    }
  }

  @Test
  public void submitRequirement_loadedFromTheLatestRevisionNoteForClosedChanges() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      // Abandon change. Submit requirements get stored in the revision note of patch-set 1.
      gApi.changes().id(changeId).abandon();
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Restore the change.
      gApi.changes().id(changeId).restore();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Upload a second patch-set, fulfill the CR submit requirement.
      amendChange(changeId, "refs/for/master", user, repo);
      change = gApi.changes().id(changeId).get();
      assertThat(change.revisions).hasSize(2);
      voteLabel(changeId, "Code-Review", 2);
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

      // Abandon the change.
      gApi.changes().id(changeId).abandon();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void submitRequirement_abandonRestoreUpdateMerge() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      // Abandon change. Submit requirements get stored in the revision note of patch-set 1.
      gApi.changes().id(changeId).abandon();
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Restore the change.
      gApi.changes().id(changeId).restore();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Update the change.
      amendChange(changeId, "refs/for/master", user, repo);
      change = gApi.changes().id(changeId).get();
      assertThat(change.revisions).hasSize(2);
      voteLabel(changeId, "Code-Review", 2);
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

      // Merge the change.
      gApi.changes().id(changeId).current().submit();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void submitRequirement_returnsEmpty_forAbandonedChangeWithPreviouslyStoredSRs()
      throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      // Abandon change. Submit requirements get stored in the revision note of patch-set 1.
      gApi.changes().id(changeId).abandon();
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Clear SRs for the project and update code-review label to be non-blocking.
      clearSubmitRequirements(project);
      LabelType cr =
          TestLabels.codeReview().toBuilder().setFunction(LabelFunction.NO_BLOCK).build();
      try (ProjectConfigUpdate u = updateProject(project)) {
        u.getConfig().upsertLabelType(cr);
        u.save();
      }

      // Restore the change. No SRs apply.
      gApi.changes().id(changeId).restore();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).isEmpty();

      // Abandon the change. Still, no SRs apply.
      gApi.changes().id(changeId).abandon();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).isEmpty();
    }
  }

  @Test
  public void submitRequirement_returnsEmpty_forMergedChangeWithPreviouslyStoredSRs()
      throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      // Abandon change. Submit requirements get stored in the revision note of patch-set 1.
      gApi.changes().id(changeId).abandon();
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Clear SRs for the project and update code-review label to be non-blocking.
      clearSubmitRequirements(project);
      LabelType cr =
          TestLabels.codeReview().toBuilder().setFunction(LabelFunction.NO_BLOCK).build();
      try (ProjectConfigUpdate u = updateProject(project)) {
        u.getConfig().upsertLabelType(cr);
        u.save();
      }

      // Restore the change. No SRs apply.
      gApi.changes().id(changeId).restore();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).isEmpty();

      // Merge the change. Still, no SRs apply.
      gApi.changes().id(changeId).current().submit();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).isEmpty();
    }
  }

  @Test
  public void submitRequirement_withMultipleAbandonAndRestore() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();

      // Abandon change. Submit requirements get stored in the revision note of patch-set 1.
      gApi.changes().id(changeId).abandon();
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Restore the change.
      gApi.changes().id(changeId).restore();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Abandon the change again.
      gApi.changes().id(changeId).abandon();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Restore, vote CR=+2, and abandon again. Make sure the requirement is now satisfied.
      gApi.changes().id(changeId).restore();
      voteLabel(changeId, "Code-Review", 2);
      gApi.changes().id(changeId).abandon();
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void submitRequirement_retrievedFromNoteDbForAbandonedChanges() throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      Project.NameKey project = createProjectForPush(submitType);
      TestRepository<InMemoryRepository> repo = cloneProject(project);
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());

      PushOneCommit.Result r =
          createChange(repo, "master", "Add a file", "foo", "content", "topic");
      String changeId = r.getChangeId();
      voteLabel(changeId, "Code-Review", 2);
      gApi.changes().id(changeId).abandon();

      // Add another submit requirement. This will not get returned for the abandoned change, since
      // we return the state of the SR results when the change was abandoned.
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("New-Requirement")
              .setSubmittabilityExpression(SubmitRequirementExpression.create("-has:unresolved"))
              .setAllowOverrideInChildProjects(false)
              .build());
      ChangeInfo changeInfo =
          gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
      assertThat(changeInfo.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          changeInfo.submitRequirements,
          "Code-Review",
          Status.SATISFIED,
          /* isLegacy= */ false,
          /* submittabilityCondition= */ "label:Code-Review=MAX");

      // Restore the change, the new requirement will show up
      gApi.changes().id(changeId).restore();
      changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
      assertThat(changeInfo.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          changeInfo.submitRequirements,
          "Code-Review",
          Status.SATISFIED,
          /* isLegacy= */ false,
          /* submittabilityCondition= */ "label:Code-Review=MAX");
      assertSubmitRequirementStatus(
          changeInfo.submitRequirements,
          "New-Requirement",
          Status.SATISFIED,
          /* isLegacy= */ false,
          /* submittabilityCondition= */ "-has:unresolved");

      // Abandon again, make sure the new requirement was persisted
      gApi.changes().id(changeId).abandon();
      changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
      assertThat(changeInfo.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          changeInfo.submitRequirements,
          "Code-Review",
          Status.SATISFIED,
          /* isLegacy= */ false,
          /* submittabilityCondition= */ "label:Code-Review=MAX");
      assertSubmitRequirementStatus(
          changeInfo.submitRequirements,
          "New-Requirement",
          Status.SATISFIED,
          /* isLegacy= */ false,
          /* submittabilityCondition= */ "-has:unresolved");
    }
  }

  @Test
  public void submitRequirement_retrievedFromNoteDbForClosedChanges() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).current().submit();

    // Add new submit requirement
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Verified")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Verified=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // The new "Verified" submit requirement is not returned, since this change is closed
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void
      submitRequirements_returnOneEntryForMatchingLegacyAndNonLegacyResultsWithTheSameName_ifLegacySubmitRecordsAreEnabled()
          throws Exception {
    // Configure a legacy submit requirement: label with a max with block function
    configLabel("build-cop-override", LabelFunction.MAX_WITH_BLOCK);
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("build-cop-override")
                .ref("refs/heads/master")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // Configure a submit requirement with the same name.
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("build-cop-override")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create(
                    "label:build-cop-override=MAX -label:build-cop-override=MIN"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // Create a change. Vote to fulfill all requirements.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    voteLabel(changeId, "build-cop-override", 1);
    voteLabel(changeId, "Code-Review", 2);

    // Project has two legacy requirements: Code-Review and bco, and a non-legacy requirement: bco.
    // Only non-legacy bco is returned.
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements,
        "build-cop-override",
        Status.SATISFIED,
        /* isLegacy= */ false,
        /* submittabilityCondition= */ "label:build-cop-override=MAX -label:build-cop-override=MIN");
    assertThat(change.submittable).isTrue();

    // Merge the change. Submit requirements are still the same.
    gApi.changes().id(changeId).current().submit();
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements,
        "build-cop-override",
        Status.SATISFIED,
        /* isLegacy= */ false,
        /* submittabilityCondition= */ "label:build-cop-override=MAX -label:build-cop-override=MIN");
  }

  @Test
  public void
      submitRequirements_returnTwoEntriesForMismatchingLegacyAndNonLegacyResultsWithTheSameName_ifLegacySubmitRecordsAreEnabled()
          throws Exception {
    // Configure a legacy submit requirement: label with a max with block function
    configLabel("build-cop-override", LabelFunction.MAX_WITH_BLOCK);
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("build-cop-override")
                .ref("refs/heads/master")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // Configure a submit requirement with the same name.
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("build-cop-override")
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("label:build-cop-override=MIN"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // Create a change
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    voteLabel(changeId, "build-cop-override", 1);
    voteLabel(changeId, "Code-Review", 2);

    // Project has two legacy requirements: Code-Review and bco, and a non-legacy requirement: bco.
    // Two instances of bco will be returned since their status is not matching.
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(3);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements,
        "build-cop-override",
        Status.SATISFIED,
        /* isLegacy= */ true,
        // MAX_WITH_BLOCK function was translated to a submittability expression.
        /* submittabilityCondition= */ "label:build-cop-override=MAX -label:build-cop-override=MIN");
    assertSubmitRequirementStatus(
        change.submitRequirements,
        "build-cop-override",
        Status.UNSATISFIED,
        /* isLegacy= */ false,
        /* submittabilityCondition= */ "label:build-cop-override=MIN");
    assertThat(change.submittable).isFalse();
  }

  @Test
  public void submitRequirements_skippedIfLegacySRIsBasedOnOptionalLabel() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    SubmitRule r1 =
        createSubmitRule("r1", SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.MAY);
    try (Registration registration = extensionRegistry.newRegistration().add(r1)) {
      ChangeInfo change = gApi.changes().id(changeId).get();
      Collection<SubmitRequirementResultInfo> submitRequirements = change.submitRequirements;
      assertThat(submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
    }
  }

  @Test
  public void submitRequirement_notSkippedIfLegacySRIsBasedOnNonOptionalLabel() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    SubmitRule r1 =
        createSubmitRule("r1", SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.OK);
    try (Registration registration = extensionRegistry.newRegistration().add(r1)) {
      ChangeInfo change = gApi.changes().id(changeId).get();
      Collection<SubmitRequirementResultInfo> submitRequirements = change.submitRequirements;
      assertThat(submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          submitRequirements, "CR", Status.SATISFIED, /* isLegacy= */ true);
    }
  }

  @Test
  public void submitRequirements_returnForLegacySubmitRecords_ifEnabled() throws Exception {
    configLabel("build-cop-override", LabelFunction.MAX_WITH_BLOCK);
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("build-cop-override")
                .ref("refs/heads/master")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // 1. Project has two legacy requirements: Code-Review and bco. Both unsatisfied.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "build-cop-override", Status.UNSATISFIED, /* isLegacy= */ true);

    // 2. Vote +1 on bco. bco becomes satisfied
    voteLabel(changeId, "build-cop-override", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "build-cop-override", Status.SATISFIED, /* isLegacy= */ true);

    // 3. Vote +1 on Code-Review. Code-Review becomes satisfied
    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "build-cop-override", Status.SATISFIED, /* isLegacy= */ true);

    // 4. Merge the change. Submit requirements status is presented from NoteDb.
    gApi.changes().id(changeId).current().submit();
    change = gApi.changes().id(changeId).get();
    // Legacy submit records are returned as submit requirements.
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "build-cop-override", Status.SATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_backFilledFromIndexForActiveChanges() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 2);

    // Query the change. ChangeInfo is back-filled from the change index.
    List<ChangeInfo> changeInfos =
        gApi.changes()
            .query()
            .withQuery("project:{" + project.get() + "} (status:open OR status:closed)")
            .withOptions(ImmutableSet.of(ListChangesOption.SUBMIT_REQUIREMENTS))
            .get();
    assertThat(changeInfos).hasSize(1);
    assertSubmitRequirementStatus(
        changeInfos.get(0).submitRequirements,
        "Code-Review",
        Status.SATISFIED,
        /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_backFilledFromIndexForClosedChanges() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 2);
    gApi.changes().id(changeId).current().submit();

    // Query the change. ChangeInfo is back-filled from the change index.
    List<ChangeInfo> changeInfos =
        gApi.changes()
            .query()
            .withQuery("project:{" + project.get() + "} (status:open OR status:closed)")
            .withOptions(ImmutableSet.of(ListChangesOption.SUBMIT_REQUIREMENTS))
            .get();
    assertThat(changeInfos).hasSize(1);
    assertSubmitRequirementStatus(
        changeInfos.get(0).submitRequirements,
        "Code-Review",
        Status.SATISFIED,
        /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_applicabilityExpressionIsAlwaysHidden() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setApplicabilityExpression(SubmitRequirementExpression.of("branch:refs/heads/master"))
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 2);
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    SubmitRequirementResultInfo requirement =
        changeInfo.submitRequirements.stream().collect(MoreCollectors.onlyElement());
    assertSubmitRequirementExpression(
        requirement.applicabilityExpressionResult,
        /* expression= */ null,
        /* passingAtoms= */ null,
        /* failingAtoms= */ null,
        /* fulfilled= */ true);
    assertThat(requirement.submittabilityExpressionResult).isNotNull();
  }

  @Test
  public void submitRequirement_nonApplicable_submittabilityAndOverrideNotEvaluated()
      throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setApplicabilityExpression(
                SubmitRequirementExpression.of("branch:refs/heads/non-existent"))
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("project:" + project.get()))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 2);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertSubmitRequirementStatus(
        changeInfo.submitRequirements, "Code-Review", Status.NOT_APPLICABLE, /* isLegacy= */ false);
    SubmitRequirementResultInfo requirement =
        changeInfo.submitRequirements.stream().collect(MoreCollectors.onlyElement());
    assertSubmitRequirementExpression(
        requirement.applicabilityExpressionResult,
        /* expression= */ null,
        /* passingAtoms= */ null,
        /* failingAtoms= */ null,
        /* fulfilled= */ false);
    assertThat(requirement.submittabilityExpressionResult).isNull();
    assertThat(requirement.overrideExpressionResult).isNull();
  }

  @Test
  public void submitRequirement_emptyApplicable_submittabilityAndOverrideEvaluated()
      throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setApplicabilityExpression(Optional.empty())
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("project:non-existent"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 2);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertSubmitRequirementStatus(
        changeInfo.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    SubmitRequirementResultInfo requirement =
        changeInfo.submitRequirements.stream().collect(MoreCollectors.onlyElement());
    assertThat(requirement.applicabilityExpressionResult).isNull();
    assertSubmitRequirementExpression(
        requirement.submittabilityExpressionResult,
        /* expression= */ SubmitRequirementExpression.maxCodeReview().expressionString(),
        /* passingAtoms= */ ImmutableList.of(
            SubmitRequirementExpression.maxCodeReview().expressionString()),
        /* failingAtoms= */ ImmutableList.of(),
        /* fulfilled= */ true);
    assertSubmitRequirementExpression(
        requirement.overrideExpressionResult,
        /* expression= */ "project:non-existent",
        /* passingAtoms= */ ImmutableList.of(),
        /* failingAtoms= */ ImmutableList.of("project:non-existent"),
        /* fulfilled= */ false);
  }

  @Test
  public void submitRequirement_overriden_submittabilityEvaluated() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setApplicabilityExpression(Optional.empty())
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("project:" + project.get()))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    voteLabel(changeId, "Code-Review", 1);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertSubmitRequirementStatus(
        changeInfo.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
    SubmitRequirementResultInfo requirement =
        changeInfo.submitRequirements.stream()
            .filter(sr -> !sr.isLegacy)
            .collect(MoreCollectors.onlyElement());
    assertThat(requirement.applicabilityExpressionResult).isNull();
    assertSubmitRequirementExpression(
        requirement.submittabilityExpressionResult,
        /* expression= */ SubmitRequirementExpression.maxCodeReview().expressionString(),
        /* passingAtoms= */ ImmutableList.of(),
        /* failingAtoms= */ ImmutableList.of(
            SubmitRequirementExpression.maxCodeReview().expressionString()),
        /* fulfilled= */ false);
    assertSubmitRequirementExpression(
        requirement.overrideExpressionResult,
        /* expression= */ "project:" + project.get(),
        /* passingAtoms= */ ImmutableList.of("project:" + project.get()),
        /* failingAtoms= */ ImmutableList.of(),
        /* fulfilled= */ true);
  }

  @Test
  public void submitRequirements_eliminatesDuplicatesForLegacyNonMatchingSRs() throws Exception {
    // If a custom/prolog submit rule emits the same label name multiple times, we merge these into
    // a single submit requirement result: in this test, we have two different submit rules that
    // return the same label name, one as "OK" and the other as "NEED". The submit requirements
    // API favours the blocking entry and returns one SR result with status=UNSATISFIED.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    SubmitRule r1 =
        createSubmitRule("r1", SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.OK);
    SubmitRule r2 =
        createSubmitRule("r2", SubmitRecord.Status.NOT_READY, "CR", SubmitRecord.Label.Status.NEED);
    try (Registration registration = extensionRegistry.newRegistration().add(r1).add(r2)) {
      ChangeInfo change = gApi.changes().id(changeId).get();
      Collection<SubmitRequirementResultInfo> submitRequirements = change.submitRequirements;
      assertThat(submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          submitRequirements, "CR", Status.UNSATISFIED, /* isLegacy= */ true);
    }
  }

  @Test
  public void submitRequirements_eliminatesDuplicatesForLegacyMatchingSRs() throws Exception {
    // If a custom/prolog submit rule emits the same label name multiple times, we merge these into
    // a single submit requirement result: in this test, we have two different submit rules that
    // return the same label name, but both are fulfilled (i.e. they both allow submission). The
    // submit requirements API returns one SR result with status=SATISFIED.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    SubmitRule r1 =
        createSubmitRule("r1", SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.OK);
    SubmitRule r2 =
        createSubmitRule("r2", SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.MAY);
    try (Registration registration = extensionRegistry.newRegistration().add(r1).add(r2)) {
      ChangeInfo change = gApi.changes().id(changeId).get();
      Collection<SubmitRequirementResultInfo> submitRequirements = change.submitRequirements;
      assertThat(submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          submitRequirements, "CR", Status.SATISFIED, /* isLegacy= */ true);
    }
  }

  @Test
  public void submitRequirements_eliminatesMultipleDuplicatesForLegacyMatchingSRs()
      throws Exception {
    // If a custom/prolog submit rule emits the same label name multiple times, we merge these into
    // a single submit requirement result: in this test, we have five different submit rules that
    // return the same label name, all with an "OK" status. The submit requirements API returns
    // a single SR result with status=SATISFIED.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    try (Registration registration = extensionRegistry.newRegistration()) {
      IntStream.range(0, 5)
          .forEach(
              i ->
                  registration.add(
                      createSubmitRule(
                          "r" + i, SubmitRecord.Status.OK, "CR", SubmitRecord.Label.Status.OK)));
      ChangeInfo change = gApi.changes().id(changeId).get();
      Collection<SubmitRequirementResultInfo> submitRequirements = change.submitRequirements;
      assertThat(submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          submitRequirements, "CR", Status.SATISFIED, /* isLegacy= */ true);
    }
  }

  @Test
  public void submitRequirement_duplicateSubmitRequirement_sameCase() throws Exception {
    // Define 2 submit requirements with exact same name but different submittability expression.
    try (TestRepository<Repository> repo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = repo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = repo.getRevWalk().parseCommit(ref.getObjectId());
      RevObject blob = repo.get(head.getTree(), ProjectConfig.PROJECT_CONFIG);
      byte[] data = repo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
      String projectConfig = RawParseUtils.decode(data);

      repo.update(
          RefNames.REFS_CONFIG,
          repo.commit()
              .parent(head)
              .message("Set project config")
              .add(
                  ProjectConfig.PROJECT_CONFIG,
                  projectConfig
                      // JGit parses this as a list value:
                      // submit-requirement.Code-Review.submittableIf =
                      //     [label:Code-Review=+2, label:Code-Review=+1]
                      // if getString is used to read submittableIf JGit returns the last value
                      // (label:Code-Review=+1)
                      + "[submit-requirement \"Code-Review\"]\n"
                      + "    submittableIf = label:Code-Review=+2\n"
                      + "[submit-requirement \"Code-Review\"]\n"
                      + "    submittableIf = label:Code-Review=+1\n"));
    }
    projectCache.evict(project);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // The submit requirement is fulfilled now, since label:Code-Review=+1 applies as submittability
    // expression (see comment above)
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_duplicateSubmitRequirement_differentCase() throws Exception {
    // Define 2 submit requirements with same name but different case and different submittability
    // expression.
    try (TestRepository<Repository> repo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = repo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = repo.getRevWalk().parseCommit(ref.getObjectId());
      RevObject blob = repo.get(head.getTree(), ProjectConfig.PROJECT_CONFIG);
      byte[] data = repo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
      String projectConfig = RawParseUtils.decode(data);

      repo.update(
          RefNames.REFS_CONFIG,
          repo.commit()
              .parent(head)
              .message("Set project config")
              .add(
                  ProjectConfig.PROJECT_CONFIG,
                  projectConfig
                      // ProjectConfig processes the submit requirements in the order in which they
                      // appear (1. Code-Review, 2. code-review) and ignores any further submit
                      // requirement if its name case-insensitively matches the name of a submit
                      // requirement that has already been seen. This means the Code-Review submit
                      // requirement applies and the code-review submit requirement is ignored.
                      + "[submit-requirement \"Code-Review\"]\n"
                      + "    submittableIf = label:Code-Review=+2\n"
                      + "[submit-requirement \"code-review\"]\n"
                      + "    submittableIf = label:Code-Review=+1\n"));
    }
    projectCache.evict(project);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // Still not satisfied since the Code-Review submit requirement with label:Code-Review=+2 as
    // submittability expression applies (see comment above).
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // The submit requirement is fulfilled now, since label:Code-Review=+2 applies as submittability
    // expression (see comment above)
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirement_overrideInheritedSRWithDifferentNameCasing() throws Exception {
    // Define submit requirement in root project and allow override.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(true)
            .build());

    // Define a submit requirement with the same name in the child project that differs by case and
    // has a different submittability expression (requires Code-Review=+1 instead of +2).
    // This overrides the inherited submit requirement with the same name, although the case is
    // different.
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("code-review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "code-review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    // +1 was enough to fulfill the requirement since the override applies
    assertSubmitRequirementStatus(
        change.submitRequirements, "code-review", Status.SATISFIED, /* isLegacy= */ false);
    // Legacy requirement is coming from the label MaxWithBlock function. Still unsatisfied.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_cannotOverrideNonOverridableInheritedSRWithDifferentNameCasing()
      throws Exception {
    // Define submit requirement in root project and disallow override.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    // Define a submit requirement with the same name in the child project that differs by case and
    // has a different submittability expression (requires Code-Review=+1 instead of +2).
    // This is ignored since the inherited submit requirement with the same name (different case)
    // disallows overriding.
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("code-review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setOverrideExpression(SubmitRequirementExpression.of("label:build-cop-override=+1"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 1);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    // Still not satisfied since the override is ignored.
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    voteLabel(changeId, "Code-Review", 2);
    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void globalSubmitRequirement_storedForClosedChanges() throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("global-submit-requirement")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(false)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          change.submitRequirements,
          "global-submit-requirement",
          Status.UNSATISFIED,
          /* isLegacy= */ false);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);

      voteLabel(changeId, "Code-Review", 2);
      gApi.changes().id(changeId).topic("test");

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          change.submitRequirements,
          "global-submit-requirement",
          Status.SATISFIED,
          /* isLegacy= */ false);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);

      gApi.changes().id(changeId).current().submit();

      ChangeNotes notes = notesFactory.create(project, r.getChange().getId());
      SubmitRequirementResult result =
          notes.getSubmitRequirementsResult().stream().collect(MoreCollectors.onlyElement());
      assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.SATISFIED);
      assertThat(result.submittabilityExpressionResult().get().status())
          .isEqualTo(SubmitRequirementExpressionResult.Status.PASS);
      assertThat(result.submittabilityExpressionResult().get().expression().expressionString())
          .isEqualTo("topic:test");
    }
  }

  @Test
  public void projectSubmitRequirementDuplicatesGlobal_overrideNotAllowed_globalEvaluated()
      throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("CoDe-reView")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(false)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      // Vote does not satisfy submit requirement, because the global definition is evaluated.
      voteLabel(changeId, "CoDe-reView", 2);

      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.UNSATISFIED, /* isLegacy= */ false);
      // In addition, the legacy submit requirement is emitted, since the status mismatch
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);

      // Setting the topic satisfies the global definition.
      gApi.changes().id(changeId).topic("test");

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void projectSubmitRequirementDuplicatesGlobal_overrideAllowed_projectRequirementEvaluated()
      throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("CoDe-reView")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(true)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      configSubmitRequirement(
          project,
          SubmitRequirement.builder()
              .setName("Code-Review")
              .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
              .setAllowOverrideInChildProjects(false)
              .build());
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      // Setting the topic does not satisfy submit requirement, because the project definition is
      // evaluated.
      gApi.changes().id(changeId).topic("test");

      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      // There is no mismatch with legacy submit requirement, so the single result is emitted.
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

      // Voting satisfies the project definition.
      voteLabel(changeId, "Code-Review", 2);
      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void legacySubmitRequirementDuplicatesGlobal_statusMatches_globalReturned()
      throws Exception {
    // The behaviour does not depend on AllowOverrideInChildProject in global submit requirement.
    testLegacySubmitRequirementDuplicatesGlobalStatusMatches(/*allowOverrideInChildProject=*/ true);
    testLegacySubmitRequirementDuplicatesGlobalStatusMatches(
        /*allowOverrideInChildProject=*/ false);
  }

  private void testLegacySubmitRequirementDuplicatesGlobalStatusMatches(
      boolean allowOverrideInChildProject) throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("CoDe-reView")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(allowOverrideInChildProject)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      // Both are evaluated, but only the global is returned, since both are unsatisfied
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.UNSATISFIED, /* isLegacy= */ false);

      // Both are evaluated, but only the global is returned, since both are satisfied
      voteLabel(changeId, "Code-Review", 2);
      gApi.changes().id(changeId).topic("test");

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void legacySubmitRequirementWithIgnoreSelfApproval() throws Exception {
    LabelType verified =
        label(LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    verified = verified.toBuilder().setIgnoreSelfApproval(true).build();
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(verified);
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(verified.getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // The DefaultSubmitRule emits an "OK" submit record for Verified, while the
    // ignoreSelfApprovalRule emits a "NEED" submit record. The "submit requirements" adapter merges
    // both results and returns the blocking one only.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes().id(changeId).addReviewer(user.id().toString());

    voteLabel(changeId, verified.getName(), +1);
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    Collection<SubmitRequirementResultInfo> submitRequirements = changeInfo.submitRequirements;
    assertSubmitRequirementStatus(
        submitRequirements, "Verified", Status.UNSATISFIED, /* isLegacy= */ true);
  }

  @Test
  public void legacySubmitRequirementDuplicatesGlobal_statusDoesNotMatch_bothRecordsReturned()
      throws Exception {
    // The behaviour does not depend on AllowOverrideInChildProject in global submit requirement.
    testLegacySubmitRequirementDuplicatesGlobalStatusDoesNotMatch(
        /*allowOverrideInChildProject=*/ true);
    testLegacySubmitRequirementDuplicatesGlobalStatusDoesNotMatch(
        /*allowOverrideInChildProject=*/ false);
  }

  private void testLegacySubmitRequirementDuplicatesGlobalStatusDoesNotMatch(
      boolean allowOverrideInChildProject) throws Exception {
    SubmitRequirement globalSubmitRequirement =
        SubmitRequirement.builder()
            .setName("CoDe-reView")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("topic:test"))
            .setAllowOverrideInChildProjects(allowOverrideInChildProject)
            .build();
    try (Registration registration =
        extensionRegistry.newRegistration().add(globalSubmitRequirement)) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      // Both are evaluated, but only the global is returned, since both are unsatisfied
      ChangeInfo change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(1);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.UNSATISFIED, /* isLegacy= */ false);

      // Both are evaluated and both are returned, since result mismatch
      voteLabel(changeId, "Code-Review", 2);

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.UNSATISFIED, /* isLegacy= */ false);

      gApi.changes().id(changeId).topic("test");
      gApi.changes().id(changeId).reviewer(admin.id().toString()).deleteVote(LabelId.CODE_REVIEW);

      change = gApi.changes().id(changeId).get();
      assertThat(change.submitRequirements).hasSize(2);
      assertThat(change.submitRequirements).hasSize(2);
      assertSubmitRequirementStatus(
          change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ true);
      assertSubmitRequirementStatus(
          change.submitRequirements, "CoDe-reView", Status.SATISFIED, /* isLegacy= */ false);
    }
  }

  @Test
  public void submitRequirements_disallowsTheIsSubmittableOperator() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Wrong-Req")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("is:submittable"))
            .setAllowOverrideInChildProjects(false)
            .build());

    ChangeInfo change = gApi.changes().id(changeId).get();
    SubmitRequirementResultInfo srResult =
        change.submitRequirements.stream()
            .filter(sr -> sr.name.equals("Wrong-Req"))
            .collect(MoreCollectors.onlyElement());
    assertThat(srResult.status).isEqualTo(Status.ERROR);
    assertThat(srResult.submittabilityExpressionResult.errorMessage)
        .isEqualTo("Operator 'is:submittable' cannot be used in submit requirement expressions.");
  }

  @Test
  public void submitRequirements_forcedByDirectSubmission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(REGISTERED_USERS))
        .update();

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("My-Requirement")
            // Submit requirement is always unsatisfied, but we are going to bypass it.
            .setSubmittabilityExpression(SubmitRequirementExpression.create("is:false"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    pushFactory.create(admin.newIdent(), testRepo, changeId).to("refs/for/master%submit");

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "My-Requirement", Status.FORCED, /* isLegacy= */ false);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.FORCED, /* isLegacy= */ true);
  }

  @Test
  public void submitRequirement_evaluatedWithInternalUserCredentials() throws Exception {
    GroupInput in = new GroupInput();
    in.name = "invisible-group";
    in.visibleToAll = false;
    in.ownerId = adminGroupUuid().get();
    gApi.groups().create(in);

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("My-Requirement")
            .setApplicabilityExpression(SubmitRequirementExpression.of("ownerin:invisible-group"))
            .setSubmittabilityExpression(SubmitRequirementExpression.create("is:true"))
            .setAllowOverrideInChildProjects(false)
            .build());

    requestScopeOperations.setApiUser(user.id());
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    SubmitRequirementResultInfo srResult =
        change.submitRequirements.stream()
            .filter(sr -> sr.name.equals("My-Requirement"))
            .collect(MoreCollectors.onlyElement());
    assertThat(srResult.status).isEqualTo(Status.NOT_APPLICABLE);
  }

  @Test
  public void submitRequirements_submittedTogetherWithoutLegacySubmitRequirements()
      throws Exception {
    // Add a code review submit requirement and mark the 'Code-Review' label function to be
    // non-blocking.
    configSubmitRequirement(
        allProjects,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(true)
            .build());

    LabelType cr = TestLabels.codeReview().toBuilder().setFunction(LabelFunction.NO_BLOCK).build();
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(cr);
      u.save();
    }

    // Create two changes in a chain.
    createChange();
    PushOneCommit.Result r2 = createChange();

    // Make sure the CR requirement is unsatisfied.
    String changeId = r2.getChangeId();
    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);

    List<ChangeInfo> changeInfos = gApi.changes().id(changeId).submittedTogether();
    assertThat(changeInfos).hasSize(2);
    assertThat(
            changeInfos.stream()
                .map(c -> c.submittable)
                .distinct()
                .collect(MoreCollectors.onlyElement()))
        .isFalse();
  }

  @Test
  public void queryChangesBySubmitRequirementResultUsingTheLabelPredicate() throws Exception {
    // Create a non-blocking label and a submit-requirement that necessitates voting on this label.
    configLabel("LC", LabelFunction.NO_OP);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("LC").ref("refs/heads/master").group(REGISTERED_USERS).range(-1, 1))
        .update();
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("LC")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:LC=MAX"))
            .setAllowOverrideInChildProjects(false)
            .build());

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    List<ChangeInfo> changeInfos = gApi.changes().query("label:LC=NEED").get();
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(changeId);
    assertThat(gApi.changes().query("label:LC=OK").get()).isEmpty();
    // case does not matter
    changeInfos = gApi.changes().query("label:lc=NEED").get();
    assertThat(changeInfos).hasSize(1);
    assertThat(changeInfos.get(0).changeId).isEqualTo(changeId);

    voteLabel(r.getChangeId(), "LC", +1);
    changeInfos = gApi.changes().query("label:LC=OK").get();
    assertThat(changeInfos.get(0).changeId).isEqualTo(changeId);
    assertThat(gApi.changes().query("label:LC=NEED").get()).isEmpty();
  }

  @Test
  public void queryingChangesWithSubmitRequirementOptionDoesNotTouchDatabase() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            // Always not submittable
            .setSubmittabilityExpression(SubmitRequirementExpression.create("is:false"))
            .setAllowOverrideInChildProjects(false)
            .build());

    requestScopeOperations.setApiUser(admin.id());
    PushOneCommit.Result r1 = createChange();
    gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .review(ReviewInput.approve());

    ChangeInfo changeInfo = gApi.changes().id(r1.getChangeId()).get();
    assertThat(changeInfo.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        changeInfo.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy = */ false);
    assertSubmitRequirementStatus(
        changeInfo.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy = */ true);

    requestScopeOperations.setApiUser(user.id());
    try (AutoCloseable ignored = disableNoteDb()) {
      List<ChangeInfo> changeInfos =
          gApi.changes()
              .query()
              .withQuery("project:{" + project.get() + "} (status:open OR status:closed)")
              .withOptions(
                  new ImmutableSet.Builder<ListChangesOption>()
                      .addAll(IndexPreloadingUtil.DASHBOARD_OPTIONS)
                      .add(ListChangesOption.SUBMIT_REQUIREMENTS)
                      .build())
              .get();
      assertThat(changeInfos).hasSize(1);
      assertSubmitRequirementStatus(
          changeInfos.get(0).submitRequirements,
          "Code-Review",
          Status.UNSATISFIED,
          /* isLegacy = */ false);
      assertSubmitRequirementStatus(
          changeInfos.get(0).submitRequirements,
          "Code-Review",
          Status.SATISFIED,
          /* isLegacy = */ true);
    }
  }

  private void voteLabel(String changeId, String labelName, int score) throws RestApiException {
    gApi.changes().id(changeId).current().review(new ReviewInput().label(labelName, score));
  }

  private void assertSubmitRequirementResult(
      SubmitRequirementResult result,
      String srName,
      SubmitRequirementResult.Status status,
      String submitExpr,
      SubmitRequirementExpressionResult.Status submitStatus) {
    assertThat(result.submitRequirement().name()).isEqualTo(srName);
    assertThat(result.status()).isEqualTo(status);
    assertThat(result.submittabilityExpressionResult().get().expression().expressionString())
        .isEqualTo(submitExpr);
    assertThat(result.submittabilityExpressionResult().get().status()).isEqualTo(submitStatus);
  }

  private void assertSubmitRequirementStatus(
      Collection<SubmitRequirementResultInfo> results,
      String requirementName,
      SubmitRequirementResultInfo.Status status,
      boolean isLegacy,
      String submittabilityCondition) {
    for (SubmitRequirementResultInfo result : results) {
      if (result.name.equals(requirementName)
          && result.status == status
          && result.isLegacy == isLegacy
          && result.submittabilityExpressionResult.expression.equals(submittabilityCondition)) {
        return;
      }
    }
    throw new AssertionError(
        String.format(
            "Could not find submit requirement %s with status %s (results = %s)",
            requirementName,
            status,
            results.stream()
                .map(r -> String.format("%s=%s", r.name, r.status))
                .collect(toImmutableList())));
  }

  private void assertSubmitRequirementStatus(
      Collection<SubmitRequirementResultInfo> results,
      String requirementName,
      SubmitRequirementResultInfo.Status status,
      boolean isLegacy) {
    for (SubmitRequirementResultInfo result : results) {
      if (result.name.equals(requirementName)
          && result.status == status
          && result.isLegacy == isLegacy) {
        return;
      }
    }
    throw new AssertionError(
        String.format(
            "Could not find submit requirement %s with status %s (results = %s)",
            requirementName,
            status,
            results.stream()
                .map(r -> String.format("%s=%s, legacy=%s", r.name, r.status, r.isLegacy))
                .collect(toImmutableList())));
  }

  private void assertSubmitRequirementExpression(
      SubmitRequirementExpressionInfo result,
      @Nullable String expression,
      @Nullable List<String> passingAtoms,
      @Nullable List<String> failingAtoms,
      boolean fulfilled) {
    assertThat(result.expression).isEqualTo(expression);
    if (passingAtoms == null) {
      assertThat(result.passingAtoms).isNull();
    } else {
      assertThat(result.passingAtoms).containsExactlyElementsIn(passingAtoms);
    }
    if (failingAtoms == null) {
      assertThat(result.failingAtoms).isNull();
    } else {
      assertThat(result.failingAtoms).containsExactlyElementsIn(failingAtoms);
    }
    assertThat(result.fulfilled).isEqualTo(fulfilled);
  }

  private Project.NameKey createProjectForPush(SubmitType submitType) throws Exception {
    Project.NameKey project = projectOperations.newProject().submitType(submitType).create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(adminGroupUuid()))
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/*").group(adminGroupUuid()))
        .update();
    return project;
  }

  private static SubmitRule createSubmitRule(
      String ruleName,
      SubmitRecord.Status srStatus,
      String labelName,
      SubmitRecord.Label.Status labelStatus) {
    return changeData -> {
      SubmitRecord r = new SubmitRecord();
      r.ruleName = ruleName;
      r.status = srStatus;
      SubmitRecord.Label label = new SubmitRecord.Label();
      label.label = labelName;
      label.status = labelStatus;
      r.labels = Arrays.asList(label);
      return Optional.of(r);
    };
  }

  /** Returns a hard-coded submit record containing all fields. */
  private static class TestSubmitRule implements SubmitRule {
    @Override
    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
      SubmitRecord record = new SubmitRecord();
      record.ruleName = "testSubmitRule";
      record.status = SubmitRecord.Status.OK;
      SubmitRecord.Label label = new SubmitRecord.Label();
      label.label = "label";
      label.status = SubmitRecord.Label.Status.OK;
      record.labels = Arrays.asList(label);
      record.requirements =
          Arrays.asList(
              LegacySubmitRequirement.builder()
                  .setType("type")
                  .setFallbackText("fallback text")
                  .build());
      return Optional.of(record);
    }
  }

  private static SubmitRequirementInput createSubmitRequirementInput(
      String name, String submittabilityExpression) {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = name;
    input.submittabilityExpression = submittabilityExpression;
    return input;
  }

  private static SubmitRequirementInput createSubmitRequirementInput(
      String name, String applicableIf, String submittableIf, String overrideIf) {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = name;
    input.applicabilityExpression = applicableIf;
    input.submittabilityExpression = submittableIf;
    input.overrideExpression = overrideIf;
    return input;
  }

  private void addComment(String changeId, String file) throws Exception {
    ReviewInput in = new ReviewInput();
    CommentInput ci = new CommentInput();
    ci.path = file;
    ci.message = "message";
    ci.line = 1;
    in.comments = ImmutableMap.of("foo", ImmutableList.of(ci));
    gApi.changes().id(changeId).current().review(in);
  }
}
