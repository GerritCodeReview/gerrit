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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/**
 * Validates modifications to label configurations in the {@code project.config} file that is stored
 * * in {@code refs/meta/config}.
 *
 * <p>Rejects modifications to the {@link ProjectConfig#KEY_COPY_VALUE} field.
 */
public class CopyValuesLabelConfigValidator implements LabelConfigValidatorChecker {
  @Override
  public List<String> validate(Config newConfig, @Nullable Config oldConfig) {
    List<String> validationMessages = new ArrayList<>();
    for (String labelName : newConfig.getSubsections(ProjectConfig.LABEL)) {
      if (copyValuesChangedOrNewlySet(newConfig, oldConfig, labelName)) {
        validationMessages.add(
            String.format(
                "Parameter '%s.%s.%s' is deprecated and cannot be set,"
                    + " use 'is:<copy-value>' in '%s.%s.%s' instead.",
                ProjectConfig.LABEL,
                labelName,
                ProjectConfig.KEY_COPY_VALUE,
                ProjectConfig.LABEL,
                labelName,
                ProjectConfig.KEY_COPY_CONDITION));
      }
    }
    return validationMessages;
  }

  private static boolean copyValuesChangedOrNewlySet(
      Config newConfig, @Nullable Config oldConfig, String labelName) {
    if (oldConfig == null) {
      return newConfig
          .getNames(ProjectConfig.LABEL, labelName)
          .contains(ProjectConfig.KEY_COPY_VALUE);
    }

    // Ignore the order in which the copy values are defined in the new and old config, since the
    // order doesn't matter for this parameter.
    ImmutableSet<String> oldValues =
        ImmutableSet.copyOf(
            oldConfig.getStringList(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_VALUE));
    ImmutableSet<String> newValues =
        ImmutableSet.copyOf(
            newConfig.getStringList(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_VALUE));
    return !newValues.isEmpty() && !Sets.difference(newValues, oldValues).isEmpty();
  }
}
