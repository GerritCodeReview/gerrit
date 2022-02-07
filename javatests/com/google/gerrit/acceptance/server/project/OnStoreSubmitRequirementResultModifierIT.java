package com.google.gerrit.acceptance.server.project;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestOnStoreSubmitRequirementResultModifier;
import com.google.gerrit.acceptance.TestOnStoreSubmitRequirementResultModifier.ModificationStrategy;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
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

  private TestOnStoreSubmitRequirementResultModifier testOnStoreSrModifier;

  @Override
  public Module createModule() {
    testOnStoreSrModifier = new TestOnStoreSubmitRequirementResultModifier();
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(OnStoreSubmitRequirementResultModifier.class).toInstance(testOnStoreSrModifier);
      }
    };
  }

  @Before
  public void setUp() throws Exception {
    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.maxCodeReview())
            .setAllowOverrideInChildProjects(false)
            .build());
  }

  @Test
  @GerritConfig(
      name = "experiments.disabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void submitRequirementsNotStored_overrideNoOp() throws Exception {

    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.OVERRIDE);

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
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void submitRequirementStored_canBeOverriddenForMergedChanges() throws Exception {
    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.OVERRIDE);

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
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void submitRequirementStored_canBeOverriddenForAbandonedChanges() throws Exception {
    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.OVERRIDE);

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
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void overrideToUnsatisfied_unsatisfied_doesNotBlockSubmission() throws Exception {
    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.FAIL);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    approve(changeId);

    ChangeInfo change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(1);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ false);

    gApi.changes().id(changeId).current().submit();

    change = gApi.changes().id(changeId).get();
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void overrideToUnsatisfied_doesNotBlockSubmissionWithRetries() throws Exception {
    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.FAIL);

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
    assertThat(change.submitRequirements).hasSize(2);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.SATISFIED, /* isLegacy= */ true);
    assertSubmitRequirementStatus(
        change.submitRequirements, "Code-Review", Status.UNSATISFIED, /* isLegacy= */ false);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value =
          ExperimentFeaturesConstants
              .GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE)
  public void overrideToSatisfied_doesNotBypassSubmitRequirement() throws Exception {
    testOnStoreSrModifier.setModificationStrategy(ModificationStrategy.PASS);

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
}
