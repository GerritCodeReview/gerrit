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

package com.google.gerrit.server.schema;

import static java.util.stream.Collectors.joining;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Migrates all label configurations of a project to copy conditions.
 *
 * <p>The label configuration in {@code project.config} controls under which conditions approvals
 * should be copied to new patch sets:
 *
 * <ul>
 *   <li>old way: by setting boolean flags and copy values (fields {@code copyAnyScore}, {@code
 *       copyMinScore}, {@code copyMaxScore}, {@code copyAllScoresIfNoChange}, {@code
 *       copyAllScoresIfNoCodeChange}, {@code copyAllScoresOnMergeFirstParentUpdate}, {@code
 *       copyAllScoresOnTrivialRebase}, {@code copyAllScoresIfListOfFilesDidNotChange}, {@code
 *       copyValue})
 *   <li>new way: by setting a query as a copy condition (field {@code copyCondition})
 * </ul>
 *
 * <p>This class updates all label configurations in the {@code project.config} of the given
 * project:
 *
 * <ul>
 *   <li>it stores the conditions under which approvals should be copied to new patchs as a copy
 *       condition query (field {@code copyCondition})
 *   <li>it unsets all deprecated fields to control approval copying (fields {@code copyAnyScore},
 *       {@code copyMinScore}, {@code copyMaxScore}, {@code copyAllScoresIfNoChange}, {@code
 *       copyAllScoresIfNoCodeChange}, {@code copyAllScoresOnMergeFirstParentUpdate}, {@code
 *       copyAllScoresOnTrivialRebase}, {@code copyAllScoresIfListOfFilesDidNotChange}, {@code
 *       copyValue})
 * </ul>
 */
public class MigrateLabelConfigToCopyCondition {
  public static final String COMMIT_MESSAGE = "Migrate label configs to copy conditions";

  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

  @Inject
  public MigrateLabelConfigToCopyCondition(
      GitRepositoryManager repoManager, @GerritPersonIdent PersonIdent serverUser) {
    this.repoManager = repoManager;
    this.serverUser = serverUser;
  }

  /**
   * Executes the migration for the given project.
   *
   * @param projectName the name of the project for which the migration should be executed
   * @throws IOException thrown if an IO error occurs
   * @throws ConfigInvalidException thrown if the existing project.config is invalid and cannot be
   *     parsed
   */
  public void execute(Project.NameKey projectName) throws IOException, ConfigInvalidException {
    ProjectLevelConfig.Bare projectConfig =
        new ProjectLevelConfig.Bare(ProjectConfig.PROJECT_CONFIG);
    try (Repository repo = repoManager.openRepository(projectName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, projectName, repo)) {
      projectConfig.load(projectName, repo);

      Config cfg = projectConfig.getConfig();
      String orgConfigAsText = cfg.toText();
      for (String labelName : cfg.getSubsections(ProjectConfig.LABEL)) {
        String newCopyCondition = computeCopyCondition(cfg, labelName);
        if (!Strings.isNullOrEmpty(newCopyCondition)) {
          cfg.setString(
              ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION, newCopyCondition);
        }

        unsetDeprecatedFields(cfg, labelName);
      }

      if (cfg.toText().equals(orgConfigAsText)) {
        // Config was not changed (ie. none of the label definitions had any deprecated field set).
        return;
      }

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(COMMIT_MESSAGE + "\n");
      projectConfig.commit(md);
    }
  }

  private static String computeCopyCondition(Config cfg, String labelName) {
    List<String> copyConditions = new ArrayList<>();

    ifTrue(cfg, labelName, ProjectConfig.KEY_COPY_ANY_SCORE, () -> copyConditions.add("is:ANY"));
    ifTrue(cfg, labelName, ProjectConfig.KEY_COPY_MIN_SCORE, () -> copyConditions.add("is:MIN"));
    ifTrue(cfg, labelName, ProjectConfig.KEY_COPY_MAX_SCORE, () -> copyConditions.add("is:MAX"));
    forEachSkipNullValues(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_VALUE,
        value -> copyConditions.add("is:" + quoteIfNegative(parseCopyValue(value))));
    ifTrue(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        () -> copyConditions.add("changekind:" + ChangeKind.NO_CHANGE.name()));

    // The default value for copyAllScoresIfNoChange is true, hence if this parameter is not set we
    // need to include "changekind:NO_CHANGE" into the copy condition.
    ifUnset(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
        () -> copyConditions.add("changekind:" + ChangeKind.NO_CHANGE.name()));

    ifTrue(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
        () -> copyConditions.add("changekind:" + ChangeKind.NO_CODE_CHANGE.name()));
    ifTrue(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
        () -> copyConditions.add("changekind:" + ChangeKind.MERGE_FIRST_PARENT_UPDATE.name()));
    ifTrue(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
        () -> copyConditions.add("changekind:" + ChangeKind.TRIVIAL_REBASE.name()));
    ifTrue(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
        () -> copyConditions.add("has:unchanged-files"));

    if (copyConditions.isEmpty()) {
      // No copy conditions need to be added. Simply return the current copy condition as it is.
      // Returning here prevents that OR conditions are reordered and that parentheses are added
      // unnecessarily.
      return cfg.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION);
    }

    ifSet(
        cfg,
        labelName,
        ProjectConfig.KEY_COPY_CONDITION,
        copyCondition -> copyConditions.addAll(splitOrConditions(copyCondition)));

    return copyConditions.stream()
        .map(MigrateLabelConfigToCopyCondition::encloseInParenthesesIfNeeded)
        .sorted()
        // Remove duplicated OR conditions
        .distinct()
        .collect(joining(" OR "));
  }

  private static void ifSet(Config cfg, String labelName, String key, Consumer<String> consumer) {
    Optional.ofNullable(cfg.getString(ProjectConfig.LABEL, labelName, key)).ifPresent(consumer);
  }

  private static void ifUnset(Config cfg, String labelName, String key, Runnable runnable) {
    Optional<String> value =
        Optional.ofNullable(cfg.getString(ProjectConfig.LABEL, labelName, key));
    if (!value.isPresent()) {
      runnable.run();
    }
  }

  private static void ifTrue(Config cfg, String labelName, String key, Runnable runnable) {
    if (cfg.getBoolean(ProjectConfig.LABEL, labelName, key, /* defaultValue= */ false)) {
      runnable.run();
    }
  }

  private static void forEachSkipNullValues(
      Config cfg, String labelName, String key, Consumer<String> consumer) {
    Arrays.stream(cfg.getStringList(ProjectConfig.LABEL, labelName, key))
        .filter(Objects::nonNull)
        .forEach(consumer);
  }

  private static void unsetDeprecatedFields(Config cfg, String labelName) {
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_ANY_SCORE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_MIN_SCORE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_MAX_SCORE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_VALUE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
    cfg.unset(
        ProjectConfig.LABEL,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
    cfg.unset(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    cfg.unset(
        ProjectConfig.LABEL,
        labelName,
        ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE);
  }

  private static ImmutableList<String> splitOrConditions(String copyCondition) {
    if (copyCondition.contains("(") || copyCondition.contains(")")) {
      // cannot parse complex predicate tree
      return ImmutableList.of(copyCondition);
    }

    // split query on OR, this way we can detect and remove duplicate OR conditions later
    return ImmutableList.copyOf(Splitter.on(" OR ").splitToList(copyCondition));
  }

  /**
   * Add parentheses around the given copyCondition if it consists out of 2 or more predicates and
   * if it isn't enclosed in parentheses yet.
   */
  private static String encloseInParenthesesIfNeeded(String copyCondition) {
    if (copyCondition.contains(" ")
        && !(copyCondition.startsWith("(") && copyCondition.endsWith(")"))) {
      return "(" + copyCondition + ")";
    }
    return copyCondition;
  }

  private static short parseCopyValue(String value) {
    return Shorts.checkedCast(PermissionRule.parseInt(value));
  }

  private static String quoteIfNegative(short value) {
    if (value < 0) {
      return "\"" + value + "\"";
    }
    return Integer.toString(value);
  }
}
