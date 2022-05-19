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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/**
 * Validates modifications to label configurations in the {@code project.config} file that is stored
 * in {@code refs/meta/config}.
 */
@ExtensionPoint
public interface LabelConfigValidatorChecker {

  /**
   * Validates modifications to the label configuration.
   *
   * @param newConfig new project configuration.
   * @param oldConfig old project configuration (null for initial commits, i.e. old config does not
   *     exist).
   * @return a list of project config validation error messages. Empty if the validation is
   *     successful.
   */
  List<String> validate(Config newConfig, @Nullable Config oldConfig);

  /**
   * Return true if the project config field "LABEL.{labelName}.{key}" was added or modified in the
   * {@code newConfig}.
   */
  default boolean flagChangedOrNewlySet(
      Config newConfig, @Nullable Config oldConfig, String labelName, String key) {
    if (oldConfig == null) {
      return newConfig.getNames(ProjectConfig.LABEL, labelName).contains(key);
    }

    // Use getString rather than getBoolean so that we do not have to deal with values that cannot
    // be parsed as a boolean.
    String oldValue = oldConfig.getString(ProjectConfig.LABEL, labelName, key);
    String newValue = newConfig.getString(ProjectConfig.LABEL, labelName, key);
    return newValue != null && !newValue.equals(oldValue);
  }
}
