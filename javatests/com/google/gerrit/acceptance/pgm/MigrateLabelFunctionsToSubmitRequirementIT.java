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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.SubmitRequirementApi;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement;
import com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement.Status;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Test for {@link com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement}. */
@Sandboxed
public class MigrateLabelFunctionsToSubmitRequirementIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Test
  public void migrateBlockingLabel_maxWithBlock() throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_maxNoBlock() throws Exception {
    createLabel("Foo", "MaxNoBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_anyWithBlock() throws Exception {
    createLabel("Foo", "AnyWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "-label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_maxWithBlock_withIgnoreSelfApproval() throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ true);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX,user=non_uploader AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_maxNoBlock_withIgnoreSelfApproval() throws Exception {
    createLabel("Foo", "MaxNoBlock", /* ignoreSelfApproval= */ true);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX,user=non_uploader",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNonBlockingLabel_noBlock() throws Exception {
    // NoBlock labels are left as is, i.e. we don't create a "submit requirement" for them. Those
    // labels will then be treated as trigger votes in the change page.
    createLabel("Foo", "NoBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.NO_CHANGE);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    // No SR was created for the label. Label will be treated as a trigger vote.
    assertNonExistentSr("Foo");
    // Label function has not changed.
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNonBlockingLabel_noOp() throws Exception {
    // NoOp labels are left as is, i.e. we don't create a "submit requirement" for them. Those
    // labels will then be treated as trigger votes in the change page.
    createLabel("Foo", "NoOp", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    // No SR was created for the label. Label will be treated as a trigger vote.
    assertNonExistentSr("Foo");
    // The NoOp function is converted to NoBlock. Both are same.
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNoBlockLabel_withSingleZeroValue() throws Exception {
    // Labels that have a single "zero" value are skipped in the project. The migrator creates
    // non-applicable SR for these labels.
    createLabel("Foo", "NoBlock", /* ignoreSelfApproval= */ false, ImmutableMap.of("0", "No vote"));
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    // a non-applicable SR was created for the skipped label.
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "is:false",
        /* submittabilityExpression= */ "is:true",
        /* canOverride= */ true);

    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateMaxWithBlockLabel_withSingleZeroValue() throws Exception {
    // Labels that have a single "zero" value are skipped in the project. The migrator creates
    // non-applicable SRs for these labels.
    createLabel(
        "Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false, ImmutableMap.of("0", "No vote"));
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    // a non-applicable SR was created for the skipped label.
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "is:false",
        /* submittabilityExpression= */ "is:true",
        /* canOverride= */ true);

    // The MaxWithBlock function is converted to NoBlock. This has no effect anyway because the
    // label was originally skipped.
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void cannotCreateLabelsWithNoValues() {
    // This test just asserts the server's behaviour for visibility; admins cannot create a label
    // without any defined values.
    Exception thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                createLabel("Foo", "NoBlock", /* ignoreSelfApproval= */ false, ImmutableMap.of()));
    assertThat(thrown).hasMessageThat().isEqualTo("values are required");
  }

  @Test
  public void migrateNonBlockingLabel_patchSetLock_doesNothing() throws Exception {
    createLabel("Foo", "PatchSetLock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.NO_CHANGE);
    // No submit requirement created for the patchset lock label function
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertNonExistentSr(/* srName = */ "Foo");
    assertLabelFunction("Foo", "PatchSetLock");
  }

  @Test
  public void migrationIsCommittedWithServerIdent() throws Exception {
    RevCommit oldMetaCommit = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");

    RevCommit newMetaCommit = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(newMetaCommit).isNotEqualTo(oldMetaCommit);
    assertThat(newMetaCommit.getCommitterIdent().getEmailAddress())
        .isEqualTo(serverIdent.get().getEmailAddress());
  }

  @Test
  public void migrateBlockingLabel_withBranchAttribute() throws Exception {
    createLabelWithBranch(
        "Foo",
        "MaxWithBlock",
        /* ignoreSelfApproval= */ false,
        ImmutableList.of("refs/heads/master"));

    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "branch:\\\"refs/heads/master\\\"",
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_withMultipleBranchAttributes() throws Exception {
    createLabelWithBranch(
        "Foo",
        "MaxWithBlock",
        /* ignoreSelfApproval= */ false,
        ImmutableList.of("refs/heads/master", "refs/heads/develop"));

    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "branch:\\\"refs/heads/master\\\" "
            + "OR branch:\\\"refs/heads/develop\\\"",
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_withRegexBranchAttribute() throws Exception {
    createLabelWithBranch(
        "Foo",
        "MaxWithBlock",
        /* ignoreSelfApproval= */ false,
        ImmutableList.of("^refs/heads/main-.*"));

    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "branch:\\\"^refs/heads/main-.*\\\"",
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateBlockingLabel_withRegexAndNonRegexBranchAttributes() throws Exception {
    createLabelWithBranch(
        "Foo",
        "MaxWithBlock",
        /* ignoreSelfApproval= */ false,
        ImmutableList.of("refs/heads/master", "^refs/heads/main-.*"));

    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "branch:\\\"refs/heads/master\\\" "
            + "OR branch:\\\"^refs/heads/main-.*\\\"",
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrationIsIdempotent() throws Exception {
    String oldRefsConfigId;
    try (Repository repo = repoManager.openRepository(project)) {
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    // Running the migration causes REFS_CONFIG to change.
    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(oldRefsConfigId)
          .isNotEqualTo(repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString());
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }

    // No new SRs will be created. No conflicting submit requirements either since the migration
    // detects that a previous run was made and skips the migration.
    updateUI = runMigration(/* expectedResult= */ Status.PREVIOUSLY_MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);
    // Running the migration a second time won't update REFS_CONFIG.
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(oldRefsConfigId)
          .isEqualTo(repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString());
    }
  }

  @Test
  public void migrationDoesNotCreateANewSubmitRequirement_ifSRAlreadyExists_matchingWithMigration()
      throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    createSubmitRequirement("Foo", "label:Foo=MAX AND -label:Foo=MIN", /* canOverride= */ true);
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    // No new submit requirements are created.
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);
    // No conflicting submit requirements from migration vs. what was previously configured.
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    // The existing SR was left as is.
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
  }

  @Test
  public void
      migrationDoesNotCreateANewSubmitRequirement_ifSRAlreadyExists_mismatchingWithMigration()
          throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    createSubmitRequirement("Foo", "project:" + project, /* canOverride= */ true);
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "project:" + project,
        /* canOverride= */ true);

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    // One conflicting submit requirement between migration vs. what was previously configured.
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(1);

    // The existing SR was left as is.
    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "project:" + project,
        /* canOverride= */ true);
  }

  @Test
  public void migrationResetsBlockingLabel_ifSRAlreadyExists() throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    createSubmitRequirement("Foo", "owner:" + admin.email(), /* canOverride= */ true);

    TestUpdateUI updateUI = runMigration(/* expectedResult= */ Status.MIGRATED);
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);

    // The label function was reset
    assertLabelFunction("Foo", "NoBlock");
  }

  private TestUpdateUI runMigration(Status expectedResult) throws Exception {
    TestUpdateUI updateUi = new TestUpdateUI();
    MigrateLabelFunctionsToSubmitRequirement executor =
        new MigrateLabelFunctionsToSubmitRequirement(repoManager, serverIdent.get());
    Status status = executor.executeMigration(project, updateUi);
    assertThat(status).isEqualTo(expectedResult);
    projectCache.evictAndReindex(project);
    return updateUi;
  }

  private void createLabel(String labelName, String function, boolean ignoreSelfApproval)
      throws Exception {
    createLabel(
        labelName,
        function,
        ignoreSelfApproval,
        ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad"));
  }

  private void createLabel(
      String labelName, String function, boolean ignoreSelfApproval, Map<String, String> values)
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = labelName;
    input.function = function;
    input.ignoreSelfApproval = ignoreSelfApproval;
    input.values = values;
    gApi.projects().name(project.get()).label(labelName).create(input);
  }

  private void createLabelWithBranch(
      String labelName,
      String function,
      boolean ignoreSelfApproval,
      ImmutableList<String> refPatterns)
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = labelName;
    input.function = function;
    input.ignoreSelfApproval = ignoreSelfApproval;
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    input.branches = refPatterns;
    gApi.projects().name(project.get()).label(labelName).create(input);
  }

  @CanIgnoreReturnValue
  private SubmitRequirementApi createSubmitRequirement(
      String name, String submitExpression, boolean canOverride) throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = name;
    input.submittabilityExpression = submitExpression;
    input.allowOverrideInChildProjects = canOverride;
    return gApi.projects().name(project.get()).submitRequirement(name).create(input);
  }

  private void assertLabelFunction(String labelName, String function) throws Exception {
    LabelDefinitionInfo info = gApi.projects().name(project.get()).label(labelName).get();
    assertThat(info.function).isEqualTo(function);
  }

  private void assertNonExistentSr(String srName) {
    ResourceNotFoundException foo =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).submitRequirement("Foo").get());
    assertThat(foo.getMessage()).isEqualTo("Submit requirement '" + srName + "' does not exist");
  }

  private void assertExistentSr(
      String srName,
      String applicabilityExpression,
      String submittabilityExpression,
      boolean canOverride)
      throws Exception {
    SubmitRequirementInfo sr = gApi.projects().name(project.get()).submitRequirement(srName).get();
    assertThat(sr.applicabilityExpression).isEqualTo(applicabilityExpression);
    assertThat(sr.submittabilityExpression).isEqualTo(submittabilityExpression);
    assertThat(sr.allowOverrideInChildProjects).isEqualTo(canOverride);
  }

  private static class TestUpdateUI implements UpdateUI {
    int existingSrsMismatchingWithMigration = 0;
    int newlyCreatedSrs = 0;

    @Override
    public void message(String message) {
      if (message.startsWith("Warning")) {
        existingSrsMismatchingWithMigration += 1;
      } else if (message.startsWith("Project")) {
        newlyCreatedSrs += 1;
      }
    }

    @Override
    public boolean yesno(boolean defaultValue, String message) {
      return false;
    }

    @Override
    public void waitForUser() {}

    @Override
    public String readString(String defaultValue, Set<String> allowedValues, String message) {
      return null;
    }

    @Override
    public boolean isBatch() {
      return false;
    }
  }
}
