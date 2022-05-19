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

package com.google.gerrit.server.project.validator;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/**
 * Validates modifications to label configurations in the {@code project.config} file that is stored
 * * in {@code refs/meta/config}.
 *
 * <p>Rejects modifications to deprecated label flags (fields {@code copyAnyScore}, {@code
 * copyMinScore}, {@code copyMaxScore}, {@code copyAllScoresIfNoChange}, {@code
 * copyAllScoresIfNoCodeChange}, {@code copyAllScoresOnMergeFirstParentUpdate}, {@code
 * copyAllScoresOnTrivialRebase}, {@code copyAllScoresIfListOfFilesDidNotChange}, {@code
 * copyValue}).
 *
 * <p>Updates that unset the deprecated fields or that don't touch them are allowed.
 */
public class DeprecatedFlagsLabelConfigValidator implements LabelConfigValidatorChecker {
  // Map of deprecated boolean flags to the predicates that should be used in the copy condition
  // instead.
  private static final ImmutableMap<String, String> DEPRECATED_FLAGS =
      ImmutableMap.<String, String>builder()
          .put(ProjectConfig.KEY_COPY_ANY_SCORE, "is:ANY")
          .put(ProjectConfig.KEY_COPY_MIN_SCORE, "is:MIN")
          .put(ProjectConfig.KEY_COPY_MAX_SCORE, "is:MAX")
          .put(
              ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              "changekind:" + ChangeKind.NO_CHANGE.name())
          .put(
              ProjectConfig.KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
              "changekind:" + ChangeKind.NO_CODE_CHANGE.name())
          .put(
              ProjectConfig.KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
              "changekind:" + ChangeKind.MERGE_FIRST_PARENT_UPDATE.name())
          .put(
              ProjectConfig.KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
              "changekind:" + ChangeKind.TRIVIAL_REBASE.name())
          .put(
              ProjectConfig.KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE,
              "has:unchanged-files")
          .build();

  @Override
  public List<String> validate(Config newConfig, @Nullable Config oldConfig) {
    List<String> validationMessages = new ArrayList<>();
    for (String labelName : newConfig.getSubsections(ProjectConfig.LABEL)) {
      for (String deprecatedFlag : DEPRECATED_FLAGS.keySet()) {
        if (flagChangedOrNewlySet(newConfig, oldConfig, labelName, deprecatedFlag)) {
          validationMessages.add(
              String.format(
                  "Parameter '%s.%s.%s' is deprecated and cannot be set,"
                      + " use '%s' in '%s.%s.%s' instead.",
                  ProjectConfig.LABEL,
                  labelName,
                  deprecatedFlag,
                  DEPRECATED_FLAGS.get(deprecatedFlag),
                  ProjectConfig.LABEL,
                  labelName,
                  ProjectConfig.KEY_COPY_CONDITION));
        }
      }
    }
    return validationMessages;
  }
}
