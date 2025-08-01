// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static java.util.stream.Collectors.toMap;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;

public class LabelDefinitionJson {
  /**
   * Formats the given {@link LabelType} as a {@link LabelDefinitionInfo}.
   *
   * @param projectName the name of the project that defines the label, {@code null} if the label is
   *     globally defined (by implementing the {@link LabelType} extension point)
   * @param labelType the label type that should be formatted
   */
  public static LabelDefinitionInfo format(
      @Nullable Project.NameKey projectName, LabelType labelType) {
    LabelDefinitionInfo label = new LabelDefinitionInfo();
    label.name = labelType.getName();
    label.description = labelType.getDescription().orElse(null);
    label.projectName = projectName != null ? projectName.get() : null;
    label.function = labelType.getFunction().getFunctionName();
    label.values =
        labelType.getValues().stream().collect(toMap(LabelValue::formatValue, LabelValue::getText));
    label.defaultValue = labelType.getDefaultValue();
    label.branches = labelType.getRefPatterns() != null ? labelType.getRefPatterns() : null;
    label.canOverride = toBoolean(labelType.isCanOverride());
    label.copyCondition = labelType.getCopyCondition().orElse(null);
    label.allowPostSubmit = toBoolean(labelType.isAllowPostSubmit());
    label.ignoreSelfApproval = toBoolean(labelType.isIgnoreSelfApproval());
    return label;
  }

  @Nullable
  private static Boolean toBoolean(boolean v) {
    return v ? Boolean.TRUE : null;
  }

  private LabelDefinitionJson() {}
}
