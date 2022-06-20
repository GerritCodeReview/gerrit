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
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Test for {@link com.google.gerrit.server.schema.MigrateLabelFunctionsToSubmitRequirement}. */
public class MigrateLabelFunctionsToSubmitRequirementIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  private Project.NameKey newProject;

  @Before
  public void setup() {
    newProject = projectOperations.newProject().create();
  }

  @Test
  public void migrateBlockingLabel_maxWithBlock() throws Exception {
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    runMigration();

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

    runMigration();

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

    runMigration();

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

    runMigration();

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

    runMigration();

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

    runMigration();

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

    runMigration();

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

    runMigration();

    assertNonExistentSr(/* srName = */ "Foo");
    assertLabelFunction("Foo", "PatchSetLock");
  }

  @Test
  public void migrationIsCommittedWithServerIdent() throws Exception {
    String oldRefsConfigId;
    try (Repository repo = repoManager.openRepository(newProject)) {
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    runMigration();

    assertExistentSr(
        /* srName */ "Foo",
        /* applicabilityExpression= */ null,
        /* submittabilityExpression= */ "label:Foo=MAX AND -label:Foo=MIN",
        /* canOverride= */ true);
    assertLabelFunction("Foo", "NoBlock");

    try (Repository repo = repoManager.openRepository(newProject);
        RevWalk rw = new RevWalk(repo)) {
      Ref metaConfig = repo.exactRef(RefNames.REFS_CONFIG);
      String newRefsConfigId = metaConfig.getObjectId().name();
      assertThat(oldRefsConfigId).isNotEqualTo(newRefsConfigId);
      RevCommit revCommit = rw.parseCommit(metaConfig.getObjectId());
      assertThat(revCommit.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
    }
  }

  @Test
  public void migrationIsIdempotent() throws Exception {
    String oldRefsConfigId;
    try (Repository repo = repoManager.openRepository(newProject)) {
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }
    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");

    // Running the migration causes REFS_CONFIG to change.
    runMigration();
    try (Repository repo = repoManager.openRepository(newProject)) {
      assertThat(oldRefsConfigId)
          .isNotEqualTo(repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString());
      oldRefsConfigId = repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString();
    }

    // Running the migration a second time won't update REFS_CONFIG.
    runMigration();
    try (Repository repo = repoManager.openRepository(newProject)) {
      assertThat(oldRefsConfigId)
          .isEqualTo(repo.exactRef(RefNames.REFS_CONFIG).getObjectId().toString());
    }
  }

  @Test
  public void migrationIsSkippedIfAtLeastOneProjectHasProlog() throws Exception {
    // Create a rules.pl file in the default AbstractDaemonTest project.
    // If at least one project in the gerrit installation has a rules.pl file, the migration
    // will be skipped.
    createRulesPl();

    createLabel("Foo", "MaxWithBlock", /* ignoreSelfApproval= */ false);
    assertNonExistentSr(/* srName = */ "Foo");
    assertLabelFunction("Foo", "MaxWithBlock");

    runMigration();

    // No submit requirement was created. Label function is still same.
    assertNonExistentSr(/* srName = */ "Foo");
    assertLabelFunction("Foo", "MaxWithBlock");
  }

  private void runMigration() throws Exception {
    new MigrateLabelFunctionsToSubmitRequirement(
            projectCache, metaDataUpdateFactory, projectConfigFactory)
        .execute();
  }

  private void createRulesPl() throws Exception {
    String rulesContent =
        "submit_rule(submit(CR)) :-\n  " + "gerrit:max_with_block(-2, 2, 'Code-Review', CR).";
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .author(admin.newIdent())
          .committer(admin.newIdent())
          .add("rules.pl", rulesContent)
          .message("Modify rules.pl")
          .create();
    }
    projectCache.evict(project);
  }

  private void createLabel(String labelName, String function, boolean ignoreSelfApproval)
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.name = labelName;
    input.function = function;
    input.ignoreSelfApproval = ignoreSelfApproval;
    input.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    gApi.projects().name(newProject.get()).label(labelName).create(input);
  }

  private void assertLabelFunction(String labelName, String function) throws Exception {
    LabelDefinitionInfo info = gApi.projects().name(newProject.get()).label(labelName).get();
    assertThat(info.function).isEqualTo(function);
  }

  private void assertNonExistentSr(String srName) {
    ResourceNotFoundException foo =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(newProject.get()).submitRequirement("Foo").get());
    assertThat(foo.getMessage()).isEqualTo("Submit requirement '" + srName + "' does not exist");
  }

  private void assertExistentSr(
      String srName,
      String applicabilityExpression,
      String submittabilityExpression,
      boolean canOverride)
      throws Exception {
    SubmitRequirementInfo sr =
        gApi.projects().name(newProject.get()).submitRequirement(srName).get();
    assertThat(sr.applicabilityExpression).isEqualTo(applicabilityExpression);
    assertThat(sr.submittabilityExpression).isEqualTo(submittabilityExpression);
    assertThat(sr.allowOverrideInChildProjects).isEqualTo(canOverride);
  }
}
