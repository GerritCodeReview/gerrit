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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestOnStoreSubmitRequirementResultModifier;
import com.google.gerrit.acceptance.TestOnStoreSubmitRequirementResultModifier.ModificationStrategy;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.OnStoreSubmitRequirementResultModifier;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.ArrayDeque;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link OnStoreSubmitRequirementResultModifier} on the closed changes. */
@NoHttpd
public class OnStoreSubmitRequirementResultModifierIT extends AbstractDaemonTest {

  private static final TestOnStoreSubmitRequirementResultModifier
      TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER =
          new TestOnStoreSubmitRequirementResultModifier();

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(OnStoreSubmitRequirementResultModifier.class)
            .toInstance(TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER);
      }
    };
  }

  @Before
  public void setUp() throws Exception {
    removeDefaultSubmitRequirements();
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.hide(false);
  }

  @Test
  public void submitRequirementStored_canBeOverriddenForMergedChanges() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.OVERRIDE);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).current().submit();

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirementStored_canBeOverriddenForAbandonedChanges() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.OVERRIDE);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).abandon();

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.OVERRIDDEN, /* isLegacy= */ false);
  }

  @Test
  public void submitRequirementStored_notReturnedWhenHidden() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.OVERRIDE);
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.hide(true);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).current().submit();

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(0);

    ChangeNotes notes = notesFactory.create(project, r.getChange().getId());

    SubmitRequirementResult result =
        notes.getSubmitRequirementsResult().stream().collect(MoreCollectors.onlyElement());
    assertThat(result.submitRequirement().name()).isEqualTo("Code-Review");
    assertThat(result.status()).isEqualTo(SubmitRequirementResult.Status.OVERRIDDEN);
    assertThat(result.submittabilityExpressionResult().get().expression().expressionString())
        .isEqualTo("label:Code-Review=MAX AND -label:Code-Review=MIN");
  }

  @Test
  public void overrideToUnsatisfied_unsatisfied_doesNotBlockSubmission() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.FAIL);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).current().submit();

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void overrideToUnsatisfied_doesNotBlockSubmissionWithRetries() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.FAIL);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    TestSubmitInput input = new TestSubmitInput();
    input.generateLockFailures = new ArrayDeque<>(ImmutableList.of(true));
    gApi.changes().id(changeId).current().submit(input);

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  public void overrideToSatisfied_doesNotBypassSubmitRequirement() throws Exception {
    TEST_ON_STORE_SUBMIT_REQUIREMENT_RESULT_MODIFIER.setModificationStrategy(
        ModificationStrategy.PASS);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
    assertThrows(
        ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
  }

  private void assertSubmitRequirementStatus(
      Collection<SubmitRequirementResultInfo> results,
      String requirementName,
      SubmitRequirementResultInfo.Status status,
      boolean isLegacy) {
    assertWithMessage(
            "Could not find submit requirement %s with status %s, legacy=%s, (results = %s)",
            requirementName,
            status,
            isLegacy,
            results.stream()
                .map(r -> String.format("%s=%s, legacy=%s", r.name, r.status, r.isLegacy))
                .collect(toImmutableList()))
        .that(
            results.stream()
                .filter(
                    result ->
                        result.name.equals(requirementName)
                            && result.status == status
                            && result.isLegacy == isLegacy)
                .count())
        .isEqualTo(1);
  }

  private void removeDefaultSubmitRequirements() throws RestApiException {
    gApi.projects().name(allProjects.get()).submitRequirement("No-Unresolved-Comments").delete();
  }
}
