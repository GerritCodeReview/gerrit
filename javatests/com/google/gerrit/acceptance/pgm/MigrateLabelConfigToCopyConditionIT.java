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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.schema.MigrateLabelConfigToCopyCondition;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class MigrateLabelConfigToCopyConditionIT extends AbstractDaemonTest {
  private static final ImmutableSet<String> DEPRECATED_FIELDS =
      ImmutableSet.<String>builder()
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_VALUE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE)
          .add(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE)
          .add(
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE)
          .build();

  @Inject private ProjectOperations projectOperations;

  @Before
  public void setup() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      // Overwrite "Code-Review" label that is inherited from All-Projects.
      // This way changes to the "Code Review" label don't affect other tests.
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      u.getConfig().upsertLabelType(codeReview.build());

      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());

      u.save();
    }

    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // overwrite the default value for copyAllScoresIfNoChange which is true for the migration
    updateProjectConfig(
        cfg -> {
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.VERIFIED,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              /* value= */ false);
        });
  }

  @Test
  public void nothingToMigrate_noLabels() throws Exception {
    Project.NameKey projectWithoutLabelDefinitions = projectOperations.newProject().create();
    RevCommit refsMetaConfigHead =
        projectOperations.project(projectWithoutLabelDefinitions).getHead(RefNames.REFS_CONFIG);

    runMigration(projectWithoutLabelDefinitions);

    // verify that refs/meta/config was not touched
    assertThat(
            projectOperations.project(projectWithoutLabelDefinitions).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void noFieldsToMigrate() throws Exception {
    assertThat(projectOperations.project(project).getConfig().getSubsections(ProjectConfig.LABEL))
        .containsExactly(LabelId.CODE_REVIEW, LabelId.VERIFIED);

    // copyAllScoresIfNoChange=false is set in the test setup to override the default value
    assertDeprecatedFieldsUnset(
        LabelId.CODE_REVIEW, MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);
    assertDeprecatedFieldsUnset(
        LabelId.VERIFIED, MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);

    runMigration();

    // verify that copyAllScoresIfNoChange=false (that was set in the test setup to override to
    // default value) was removed
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertDeprecatedFieldsUnset(LabelId.VERIFIED);
  }

  @Test
  public void noFieldsToMigrate_copyConditionExists() throws Exception {
    String copyCondition = "is:MIN";
    setCopyConditionOnCodeReviewLabel(copyCondition);

    runMigration();

    // verify that copyAllScoresIfNoChange=false (that was set in the test setup to override to
    // default value) was removed
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    // verify that the copy condition was not changed
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo(copyCondition);
  }

  @Test
  public void noFieldsToMigrate_complexCopyConditionExists() throws Exception {
    String copyCondition = "is:MIN has:unchanged-files";
    setCopyConditionOnCodeReviewLabel(copyCondition);

    runMigration();

    // verify that copyAllScoresIfNoChange=false (that was set in the test setup to override to
    // default value) was removed
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    // verify that the copy condition was not changed (e.g. no parentheses have been added around
    // the
    // copy condition)
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo(copyCondition);
  }

  @Test
  public void noFieldsToMigrate_nonOrderedCopyConditionExists() throws Exception {
    String copyCondition = "is:MIN OR has:unchanged-files";
    setCopyConditionOnCodeReviewLabel(copyCondition);

    runMigration();

    // verify that copyAllScoresIfNoChange=false (that was set in the test setup to override to
    // default value) was removed
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    // verify that the copy condition was not changed (e.g. the order of OR conditions has not be
    // changed and no parentheses have been added around the copy condition)
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo(copyCondition);
  }

  @Test
  public void migrateCopyAnyScore() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE,
        copyCondition -> assertThat(copyCondition).isEqualTo("is:ANY"));
  }

  @Test
  public void migrateCopyMinScore() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE,
        copyCondition -> assertThat(copyCondition).isEqualTo("is:MIN"));
  }

  @Test
  public void migrateCopyMaxScore() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE,
        copyCondition -> assertThat(copyCondition).isEqualTo("is:MAX"));
  }

  @Test
  public void migrateCopyValues_singleValue() throws Exception {
    testCopyValueMigration(
        ImmutableList.of(1), copyCondition -> assertThat(copyCondition).isEqualTo("is:1"));
  }

  @Test
  public void migrateCopyValues_negativeValue() throws Exception {
    testCopyValueMigration(
        ImmutableList.of(-1), copyCondition -> assertThat(copyCondition).isEqualTo("is:\"-1\""));
  }

  @Test
  public void migrateCopyValues_multipleValues() throws Exception {
    testCopyValueMigration(
        ImmutableList.of(-1, 1),
        copyCondition -> assertThat(copyCondition).isEqualTo("is:\"-1\" OR is:1"));
  }

  @Test
  public void migrateCopyValues_manyValues() throws Exception {
    testCopyValueMigration(
        ImmutableList.of(-2, -1, 1, 2),
        copyCondition ->
            assertThat(copyCondition).isEqualTo("is:\"-1\" OR is:\"-2\" OR is:1 OR is:2"));
  }

  @Test
  public void migrateCopyAllScoresIfNoCange() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        copyCondition ->
            assertThat(copyCondition).isEqualTo("changekind:" + ChangeKind.NO_CHANGE.toString()));
  }

  @Test
  public void migrateCopyAllScoresIfNoCodeCange() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        copyCondition ->
            assertThat(copyCondition)
                .isEqualTo("changekind:" + ChangeKind.NO_CODE_CHANGE.toString()));
  }

  @Test
  public void migrateCopyAllScoresOnMergeFirstParentUpdate() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        copyCondition ->
            assertThat(copyCondition)
                .isEqualTo("changekind:" + ChangeKind.MERGE_FIRST_PARENT_UPDATE.toString()));
  }

  @Test
  public void migrateCopyAllScoresOnTrivialRebase() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        copyCondition ->
            assertThat(copyCondition)
                .isEqualTo("changekind:" + ChangeKind.TRIVIAL_REBASE.toString()));
  }

  @Test
  public void migrateCopyAllScoresIfListOfFilesDidNotChange() throws Exception {
    testFlagMigration(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        copyCondition -> assertThat(copyCondition).isEqualTo("has:unchanged-files"));
  }

  @Test
  public void migrateDefaultValues() throws Exception {
    // remove copyAllScoresIfNoChange=false that was set in the test setup to override to default
    // value
    unset(LabelId.CODE_REVIEW, MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    // expect that the copy condition was set to "changekind:NO_CHANGE" since
    // copyAllScoresIfNoChange was not set and has true as default value
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("changekind:NO_CHANGE");
  }

  @Test
  public void migrateDefaultValues_copyConditionExists() throws Exception {
    setCopyConditionOnCodeReviewLabel("is:MIN");

    // remove copyAllScoresIfNoChange=false that was set in the test setup to override to default
    // value
    unset(LabelId.CODE_REVIEW, MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);

    // expect that the copy condition includes "changekind:NO_CHANGE" since
    // copyAllScoresIfNoChange was not set and has true as default value
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("changekind:NO_CHANGE OR is:MIN");
  }

  @Test
  public void migrateAll() throws Exception {
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE);
    setCopyValuesOnCodeReviewLabel(-2, -1, 1, 2);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo(
            "changekind:MERGE_FIRST_PARENT_UPDATE"
                + " OR changekind:NO_CHANGE"
                + " OR changekind:NO_CODE_CHANGE"
                + " OR changekind:TRIVIAL_REBASE"
                + " OR has:unchanged-files"
                + " OR is:\"-1\""
                + " OR is:\"-2\""
                + " OR is:1"
                + " OR is:2"
                + " OR is:ANY"
                + " OR is:MAX"
                + " OR is:MIN");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_mutualllyExclusive() throws Exception {
    setCopyConditionOnCodeReviewLabel("is:ANY");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("is:ANY OR is:MIN");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_noDuplicatePredicate()
      throws Exception {
    setCopyConditionOnCodeReviewLabel("is:ANY");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("is:ANY OR is:MIN");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_noDuplicatePredicates()
      throws Exception {
    setCopyConditionOnCodeReviewLabel("is:ANY OR is:MIN");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("is:ANY OR is:MAX OR is:MIN");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_complexCopyCondition_v1()
      throws Exception {
    setCopyConditionOnCodeReviewLabel("is:ANY changekind:TRIVIAL_REBASE");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo("(is:ANY changekind:TRIVIAL_REBASE) OR changekind:TRIVIAL_REBASE OR is:ANY");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_complexCopyCondition_v2()
      throws Exception {
    setCopyConditionOnCodeReviewLabel(
        "is:ANY AND (changekind:TRIVIAL_REBASE OR changekind:NO_CODE_CHANGE)");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo(
            "(is:ANY AND (changekind:TRIVIAL_REBASE OR changekind:NO_CODE_CHANGE)) OR"
                + " changekind:TRIVIAL_REBASE OR is:ANY");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_noUnnecessaryParenthesesAdded()
      throws Exception {
    setCopyConditionOnCodeReviewLabel("(is:ANY changekind:TRIVIAL_REBASE)");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo("(is:ANY changekind:TRIVIAL_REBASE) OR changekind:TRIVIAL_REBASE OR is:ANY");
  }

  @Test
  public void migrationMergesFlagsIntoExistingCopyCondition_existingCopyConditionIsNotParseable()
      throws Exception {
    setCopyConditionOnCodeReviewLabel("NOT-PARSEABLE");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo("NOT-PARSEABLE OR changekind:TRIVIAL_REBASE OR is:ANY");
  }

  @Test
  public void
      migrationMergesFlagsIntoExistingCopyCondition_existingComplexCopyConditionIsNotParseable()
          throws Exception {
    setCopyConditionOnCodeReviewLabel("NOT PARSEABLE");
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE);
    setFlagOnCodeReviewLabel(
        MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel())
        .isEqualTo("(NOT PARSEABLE) OR changekind:TRIVIAL_REBASE OR is:ANY");
  }

  @Test
  public void migrateMultipleLabels() throws Exception {
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);

    setFlagOnVerifiedLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    setFlagOnVerifiedLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE);

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("changekind:NO_CHANGE OR is:MIN");

    assertDeprecatedFieldsUnset(LabelId.VERIFIED);
    assertThat(getCopyConditionOfVerifiedLabel()).isEqualTo("changekind:TRIVIAL_REBASE OR is:MAX");
  }

  @Test
  public void deprecatedFlagsThatAreSetToFalseAreUnset() throws Exception {
    // set all flags to false
    updateProjectConfig(
        cfg -> {
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ANY_SCORE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
              /* value= */ false);
          cfg.setBoolean(
              ProjectConfig.LABEL,
              LabelId.CODE_REVIEW,
              MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
              /* value= */ false);
        });
  }

  @Test
  public void emptyCopyValueParameterIsUnset() throws Exception {
    updateProjectConfig(
        cfg ->
            cfg.setString(
                ProjectConfig.LABEL,
                LabelId.CODE_REVIEW,
                MigrateLabelConfigToCopyCondition.KEY_COPY_VALUE,
                /* value= */ ""));

    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
  }

  @Test
  public void migrationCreatesASingleCommit() throws Exception {
    // Set flags on 2 labels (the migrations for both labels are expected to be done in a single
    // commit)
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);
    setFlagOnVerifiedLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MAX_SCORE);

    RevCommit refsMetaConfigHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);

    runMigration();

    // verify that the new commit in refs/meta/config is a successor of the old head
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG).getParent(0))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void commitMessageIsDistinct() throws Exception {
    // Set a flag so that the migration has to do something.
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);

    runMigration();

    // Verify that the commit message is distinct (e.g. this is important in case there is an issue
    // with the migration, having a distinct commit message allows to identify the commit that was
    // done for the migration and would allow to revert it)
    RevCommit refsMetaConfigHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(refsMetaConfigHead.getShortMessage())
        .isEqualTo(MigrateLabelConfigToCopyCondition.COMMIT_MESSAGE);
  }

  @Test
  public void gerritIsAuthorAndCommitterOfTheMigrationCommit() throws Exception {
    // Set a flag so that the migration has to do something.
    setFlagOnCodeReviewLabel(MigrateLabelConfigToCopyCondition.KEY_COPY_MIN_SCORE);

    runMigration();

    RevCommit refsMetaConfigHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(refsMetaConfigHead.getAuthorIdent().getEmailAddress())
        .isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(refsMetaConfigHead.getAuthorIdent().getName())
        .isEqualTo(serverIdent.get().getName());
    assertThat(refsMetaConfigHead.getCommitterIdent())
        .isEqualTo(refsMetaConfigHead.getAuthorIdent());
  }

  @Test
  public void migrationFailsIfProjectConfigIsNotParseable() throws Exception {
    projectOperations.project(project).forInvalidation().makeProjectConfigInvalid().invalidate();
    RevCommit refsMetaConfigHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);

    ConfigInvalidException exception =
        assertThrows(ConfigInvalidException.class, () -> runMigration());
    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Invalid config file project.config in project %s", project));

    // verify that refs/meta/config was not touched
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void migrateWhenProjectConfigIsMissing() throws Exception {
    deleteProjectConfig();
    RevCommit refsMetaConfigHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);

    runMigration();

    // verify that refs/meta/config was not touched (e.g. project.config was not created)
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG))
        .isEqualTo(refsMetaConfigHead);
  }

  @Test
  public void migrateWhenRefsMetaConfigIsMissing() throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo.delete(RefNames.REFS_CONFIG);
    }

    runMigration();

    // verify that refs/meta/config was not created
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      assertThat(testRepo.getRepository().exactRef(RefNames.REFS_CONFIG)).isNull();
    }
  }

  @Test
  public void migrationIsIdempotent_copyAllScoresIfNoChangeIsUnset() throws Exception {
    testMigrationIsIdempotent(/* copyAllScoresIfNoChangeValue= */ null);
  }

  @Test
  public void migrationIsIdempotent_copyAllScoresIfNoChangeIsFalse() throws Exception {
    testMigrationIsIdempotent(/* copyAllScoresIfNoChangeValue= */ false);
  }

  @Test
  public void migrationIsIdempotent_copyAllScoresIfNoChangeIsTrue() throws Exception {
    testMigrationIsIdempotent(/* copyAllScoresIfNoChangeValue= */ true);
  }

  private void testMigrationIsIdempotent(@Nullable Boolean copyAllScoresIfNoChangeValue)
      throws Exception {
    updateProjectConfig(
        cfg -> {
          if (copyAllScoresIfNoChangeValue != null) {
            cfg.setBoolean(
                ProjectConfig.LABEL,
                LabelId.CODE_REVIEW,
                MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
                copyAllScoresIfNoChangeValue);
            cfg.setBoolean(
                ProjectConfig.LABEL,
                LabelId.VERIFIED,
                MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
                copyAllScoresIfNoChangeValue);
          } else {
            cfg.unset(
                ProjectConfig.LABEL,
                LabelId.CODE_REVIEW,
                MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);
            cfg.unset(
                ProjectConfig.LABEL,
                LabelId.VERIFIED,
                MigrateLabelConfigToCopyCondition.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);
          }
        });

    // Run the migration to update the label configuration.
    runMigration();

    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    assertDeprecatedFieldsUnset(LabelId.VERIFIED);

    // default value for copyAllScoresIfNoChangeValue is true
    if (copyAllScoresIfNoChangeValue == null || copyAllScoresIfNoChangeValue) {
      assertThat(getCopyConditionOfCodeReviewLabel()).isEqualTo("changekind:NO_CHANGE");
    } else {
      assertThat(getCopyConditionOfCodeReviewLabel()).isNull();
    }

    // Running the migration again doesn't change anything.
    RevCommit head = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    runMigration();
    assertThat(projectOperations.project(project).getHead(RefNames.REFS_CONFIG)).isEqualTo(head);
  }

  private void testFlagMigration(String key, Consumer<String> copyConditionValidator)
      throws Exception {
    setFlagOnCodeReviewLabel(key);
    runMigration();
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    copyConditionValidator.accept(getCopyConditionOfCodeReviewLabel());
  }

  private void testCopyValueMigration(List<Integer> values, Consumer<String> copyConditionValidator)
      throws Exception {
    setCopyValuesOnCodeReviewLabel(values.toArray(new Integer[0]));
    runMigration();
    assertDeprecatedFieldsUnset(LabelId.CODE_REVIEW);
    copyConditionValidator.accept(getCopyConditionOfCodeReviewLabel());
  }

  private void runMigration() throws Exception {
    runMigration(project);
  }

  private void runMigration(Project.NameKey project) throws Exception {
    new MigrateLabelConfigToCopyCondition(repoManager, serverIdent.get()).execute(project);
  }

  private void setFlagOnCodeReviewLabel(String key) throws Exception {
    setFlag(LabelId.CODE_REVIEW, key);
  }

  private void setFlagOnVerifiedLabel(String key) throws Exception {
    setFlag(LabelId.VERIFIED, key);
  }

  private void setFlag(String labelName, String key) throws Exception {
    updateProjectConfig(
        cfg -> cfg.setBoolean(ProjectConfig.LABEL, labelName, key, /* value= */ true));
  }

  private void unset(String labelName, String key) throws Exception {
    updateProjectConfig(cfg -> cfg.unset(ProjectConfig.LABEL, labelName, key));
  }

  private void setCopyValuesOnCodeReviewLabel(Integer... values) throws Exception {
    setCopyValues(LabelId.CODE_REVIEW, values);
  }

  private void setCopyValues(String labelName, Integer... values) throws Exception {
    updateProjectConfig(
        cfg ->
            cfg.setStringList(
                ProjectConfig.LABEL,
                labelName,
                MigrateLabelConfigToCopyCondition.KEY_COPY_VALUE,
                Arrays.stream(values).map(Object::toString).collect(toImmutableList())));
  }

  private void setCopyConditionOnCodeReviewLabel(String copyCondition) throws Exception {
    setCopyCondition(LabelId.CODE_REVIEW, copyCondition);
  }

  private void setCopyCondition(String labelName, String copyCondition) throws Exception {
    updateProjectConfig(
        cfg ->
            cfg.setString(
                ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION, copyCondition));
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

  private void deleteProjectConfig() throws Exception {
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
              .rm(ProjectConfig.PROJECT_CONFIG));
    }
    projectCache.evictAndReindex(project);
  }

  private void assertDeprecatedFieldsUnset(String labelName, String... excludedFields) {
    for (String field :
        Sets.difference(DEPRECATED_FIELDS, Sets.newHashSet(Arrays.asList(excludedFields)))) {
      assertUnset(labelName, field);
    }
  }

  private void assertUnset(String labelName, String key) {
    assertThat(
            projectOperations.project(project).getConfig().getNames(ProjectConfig.LABEL, labelName))
        .doesNotContain(key);
  }

  private String getCopyConditionOfCodeReviewLabel() {
    return getCopyCondition(LabelId.CODE_REVIEW);
  }

  private String getCopyConditionOfVerifiedLabel() {
    return getCopyCondition(LabelId.VERIFIED);
  }

  private String getCopyCondition(String labelName) {
    return projectOperations
        .project(project)
        .getConfig()
        .getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION);
  }
}
