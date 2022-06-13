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
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.schema.MigrateLabelConfigToCopyCondition;
import com.google.gerrit.server.schema.NoteDbSchemaVersion;
import com.google.gerrit.server.schema.Schema_185;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import java.util.function.Consumer;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@Sandboxed
public class Schema_185IT extends AbstractDaemonTest {
  private static final String KEY_COPY_ALL_SCORES_IF_NO_CHANGE = "copyAllScoresIfNoChange";
  private static final String KEY_COPY_MIN_SCORE = "copyMinScore";

  @Inject private ProjectOperations projectOperations;
  @Inject private NoteDbSchemaVersion.Arguments args;

  private final TestUpdateUI testUpdateUI = new TestUpdateUI();

  @Test
  public void nothingToMigrate() throws Exception {
    RevCommit oldHeadAllProjects = getHead(allProjects);
    RevCommit oldHeadAllUsers = getHead(allUsers);
    RevCommit oldHeadProject = getHead(project);

    runMigration();

    // All-Projects and All-Users both contain a label definition for Code-Review but without
    // boolean flags, hence those don't need to be migrated (the migration assumes true for
    // copyAllScoresIfNoChange if unset, but the copyCondition already contains
    // 'changekind:NO_CHANGE' so copyCondition doesn't need to be changed).
    assertThatMigrationHasNotRun(allProjects, oldHeadAllProjects);
    assertThatMigrationHasNotRun(allUsers, oldHeadAllUsers);

    // Check that the migration was not executed for the projects that do not contain label
    // definitions.
    assertThatMigrationHasNotRun(project, oldHeadProject);
  }

  @Test
  public void labelConfigsAreMigrated() throws Exception {
    addLabelThatNeedsToBeMigrated(project);

    RevCommit projectOldHead = getHead(project);
    runMigration();
    assertThatMigrationHasRun(project, projectOldHead);
  }

  @Test
  public void upgradeIsIdempotent() throws Exception {
    addLabelThatNeedsToBeMigrated(project);

    // Run the migration to update the label configuration.
    runMigration();

    // Running the migration again, doesn't change anything.
    RevCommit projectOldHead = getHead(project);
    runMigration();
    assertThatMigrationHasNotRun(project, projectOldHead);
  }

  @Test
  public void upgradeIsIdempotent_onlyDefaultFlagIsMigrated() throws Exception {
    addLabelThatNeedsToBeMigratedDueToDefaultFlag(project);

    // Run the migration to update the label configuration.
    runMigration();

    // Running the migration again, doesn't change anything.
    RevCommit projectOldHead = getHead(project);
    runMigration();
    assertThatMigrationHasNotRun(project, projectOldHead);
  }

  @Test
  public void migrateMultipleProjects() throws Exception {
    Project.NameKey project1 = createProjectWithLabelConfigThatNeedsToBeMigrated();
    Project.NameKey project2 = createProjectWithLabelConfigThatNeedsToBeMigrated();
    Project.NameKey project3 = createProjectWithLabelConfigThatNeedsToBeMigrated();

    RevCommit oldHeadProject1 = getHead(project1);
    RevCommit oldHeadProject2 = getHead(project2);
    RevCommit oldHeadProject3 = getHead(project3);

    runMigration();

    assertThatMigrationHasRun(project1, oldHeadProject1);
    assertThatMigrationHasRun(project2, oldHeadProject2);
    assertThatMigrationHasRun(project3, oldHeadProject3);
  }

  @Test
  public void migrationPrintsOutProgress() throws Exception {
    // Create 197 projects so that in total we have 200 projects (197 + All-Projects + All-Users +
    // test project).
    for (int i = 0; i < 197; i++) {
      createProjectWithLabelConfigThatNeedsToBeMigrated();
    }

    runMigration();
    String output = testUpdateUI.getOutput();
    assertThat(output).contains("Migrating label configurations");
    assertThat(output).contains("migrated label configurations of 50% (100/200) projects");
    assertThat(output).contains("migrated label configurations of 100% (200/200) projects");
    assertThat(output).contains("Migrated label configurations of all 200 projects to schema 185");
  }

  @Test
  public void projectsWithInvalidConfigurationAreSkipped() throws Exception {
    Project.NameKey projectWithInvalidConfig = createProjectWithLabelConfigThatNeedsToBeMigrated();
    projectOperations
        .project(projectWithInvalidConfig)
        .forInvalidation()
        .makeProjectConfigInvalid()
        .invalidate();

    Project.NameKey otherProject1 = createProjectWithLabelConfigThatNeedsToBeMigrated();
    Project.NameKey otherProject2 = createProjectWithLabelConfigThatNeedsToBeMigrated();

    RevCommit oldHeadProjectWithInvalidConfig = getHead(projectWithInvalidConfig);
    RevCommit oldHeadOtherProject1 = getHead(otherProject1);
    RevCommit oldHeadOtherProject2 = getHead(otherProject2);

    runMigration();

    assertThatMigrationHasNotRun(projectWithInvalidConfig, oldHeadProjectWithInvalidConfig);
    assertThatMigrationHasRun(otherProject1, oldHeadOtherProject1);
    assertThatMigrationHasRun(otherProject2, oldHeadOtherProject2);

    String output = testUpdateUI.getOutput();
    assertThat(output)
        .contains(
            String.format(
                "WARNING: Skipping migration of label configurations for project %s"
                    + " since its %s file is invalid:",
                projectWithInvalidConfig, ProjectConfig.PROJECT_CONFIG));
  }

  private void runMigration() throws Exception {
    Schema_185 upgrade = new Schema_185();
    upgrade.upgrade(args, testUpdateUI);
  }

  private RevCommit getHead(Project.NameKey project) {
    return projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
  }

  private void assertThatMigrationHasRun(Project.NameKey project, RevCommit oldHead) {
    RevCommit newHead = getHead(project);
    assertThat(getHead(project)).isNotEqualTo(oldHead);
    assertThat(newHead.getShortMessage())
        .isEqualTo(MigrateLabelConfigToCopyCondition.COMMIT_MESSAGE);
  }

  private void assertThatMigrationHasNotRun(Project.NameKey project, RevCommit oldHead) {
    assertThat(getHead(project)).isEqualTo(oldHead);
  }

  private Project.NameKey createProjectWithLabelConfigThatNeedsToBeMigrated() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    addLabelThatNeedsToBeMigrated(project);
    return project;
  }

  private void addLabelThatNeedsToBeMigrated(Project.NameKey project) throws Exception {
    // create a label which needs to be migrated because flags have been set
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }

    updateProjectConfig(
        cfg -> {
          // override the default value
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              /* value= */ false);
          // set random flag
          cfg.setBoolean(
              ProjectConfig.LABEL, LabelId.CODE_REVIEW, KEY_COPY_MIN_SCORE, /* value= */ true);
        });
  }

  private void addLabelThatNeedsToBeMigratedDueToDefaultFlag(Project.NameKey project)
      throws Exception {
    // create a label which needs to be migrated (copyAllScoresIfNoChange is unset, the migration
    // assumes true as default and hence sets copyCondition to "changekind:NO_CHANGE").
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      u.getConfig().upsertLabelType(codeReview.build());
      u.save();
    }
  }

  private void updateProjectConfig(Consumer<Config> configUpdater) throws Exception {
    Config projectConfig = projectOperations.project(project).getConfig();
    configUpdater.accept(projectConfig);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Update label config")
              .add(ProjectConfig.PROJECT_CONFIG, projectConfig.toText()));
    }
    projectCache.evictAndReindex(project);
  }
}
