package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.server.schema.MigrateLabelFunctionToSubmitRequirement;
import com.google.inject.Inject;
import org.junit.Test;

public class MigrateLabelFunctionToSubmitRequirementIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void migrateBlockingLabel() throws Exception {
    Project.NameKey newProject = projectOperations.newProject().create();
    LabelDefinitionInput def = new LabelDefinitionInput();
    def.name = "Foo";
    def.values = ImmutableMap.of("+1", "Looks Good", " 0", "Don't Know", "-1", "Looks Bad");
    def.function = "MaxWithBlock";
    BatchLabelInput input = new BatchLabelInput();
    input.create = ImmutableList.of(def);
    gApi.projects().name(newProject.get()).labels(input);

    runMigration();

    // verify that refs/meta/config was not touched
    SubmitRequirementInfo foo =
        gApi.projects().name(newProject.get()).submitRequirement("Foo").get();
    assertThat(foo.submittabilityExpression).isEqualTo("something");
    assertThat(true).isTrue();
  }

  @Test
  public void migrateBlockingLabel_maxWithBlock() {}

  @Test
  public void migrateBlockingLabel_maxNoBlock() {}

  @Test
  public void migrateBlockingLabel_anyWithBlock() {}

  @Test
  public void migrateBlockingLabel_maxWithBlock_withIgnoreSelfApproval() {}

  @Test
  public void migrateBlockingLabel_maxNoBlock_withIgnoreSelfApproval() {}

  @Test
  public void migrateNonBlockingLabel_NoBlock() {}

  @Test
  public void migrateNonBlockingLabel_NoOp() {}

  @Test
  public void migrationIsCommittedWithServerIdent() {}

  @Test
  public void projectsWithPrologAreNotMigrated() {}

  private void runMigration() throws Exception {
    new MigrateLabelFunctionToSubmitRequirement(
            projectCache, metaDataUpdateFactory, projectConfigFactory)
        .execute();
  }
}
