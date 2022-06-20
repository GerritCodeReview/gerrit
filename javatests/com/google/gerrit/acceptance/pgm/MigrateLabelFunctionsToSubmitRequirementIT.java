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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.inject.Inject;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Test for {@link com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement}. */
@Sandboxed
public class MigrateLabelFunctionsToSubmitRequirementIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  /** Number of labels that are pre-installed with Gerrit. */
  private static final Integer EXISTING_LABEL_CNT = 2;

  @Test
  public void migrateBlockingLabel_maxWithBlock() throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX,user=non_uploader",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNonBlockingLabel_NoBlock() throws Exception {
    createLabel("Foo", "NoBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "is:false",
        /* submittabilityExpression= */ "is:true",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNonBlockingLabel_NoOp() throws Exception {
    createLabel("Foo", "NoBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ "is:false",
        /* submittabilityExpression= */ "is:true",
        /* canOverride= */ true);

    // The NoOp function is converted to NoBlock. Both are same.
    assertLabelFunction("Foo", "NoBlock");
  }

  @Test
  public void migrateNonBlockingLabel_PatchSetLock_doesNothing() throws Exception {
    createLabel("Foo", "PatchSetLock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration();
    // No submit requirement created for the patchset lock label function
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);

    assertNonExistentSr(/* srName = */ "Foo");
    assertLabelFunction("Foo", "PatchSetLock");
  }

  @Test
  public void migrationIsCommittedWithServerIdent() throws Exception {
    RevCommit oldMetaCommit = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
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
  public void migrationIsIdempotent() throws Exception {
    String oldRefsConfigId;
    try (Repository repo = repoManager.openRepository(project)) {
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    // Running the migration causes REFS_CONFIG to change.
    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT + 1);
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(0);
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(oldRefsConfigId)
          .isNotEqualTo(repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString());
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }

    // Running the migration a second time won't update REFS_CONFIG.
    updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(0);

    // The first run created blocking submit requirements for the different labels and did reset
    // the label functions. The second run, seeing that the label functions are now NoBlock,
    // would attempt to create non-applicable SRs for them (the migrator creates non-applicable SRs
    // for non-blocking labels). The migrator will see that the existing SRs (from the first
    // migrator run) are mismatching with what it is trying to create and will emit a warning.
    assertThat(updateUI.existingSrsMismatchingWithMigration).isEqualTo(3);
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT);
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

    TestUpdateUI updateUI = runMigration();
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

    TestUpdateUI updateUI = runMigration();
    assertThat(updateUI.newlyCreatedSrs).isEqualTo(EXISTING_LABEL_CNT);

    // The label function was reset
    assertLabelFunction("Foo", "NoBlock");
  }

  private TestUpdateUI runMigration() throws Exception {
    TestUpdateUI updateUi = new TestUpdateUI();
    MigrateLabelFunctionsToSubmitRequirement executor =
        new MigrateLabelFunctionsToSubmitRequirement(repoManager, serverIdent.get());
    for (Project.NameKey project : projectCache.all()) {
      executor.executeMigration(project, updateUi);
      projectCache.evictAndReindex(project);
    }
    return updateUi;
  }

  private void createLabel(String labelName, String function, boolean ignoreSelfApproval)
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = labelName;
    input.function = function;
    input.ignoreSelfApproval = ignoreSelfApproval;
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    gApi.projects().name(project.get()).label(labelName).create(input);
  }

  private void createSubmitRequirement(String name, String submitExpression, boolean canOverride)
      throws Exception {
    SubmitRequirementInput input = new SubmitRequirementInput();
    input.name = name;
    input.submittabilityExpression = submitExpression;
    input.allowOverrideInChildProjects = canOverride;
    gApi.projects().name(project.get()).submitRequirement(name).create(input);
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
