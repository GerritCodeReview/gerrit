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

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;

public class LabelDefinitionJson {
  public static LabelDefinitionInfo format(Project.NameKey projectName, LabelType labelType) {
    LabelDefinitionInfo label = new LabelDefinitionInfo();
    label.name = labelType.getName();
    label.projectName = projectName.get();
    label.function = labelType.getFunction().getFunctionName();
    label.values =
        labelType.getValues().stream().collect(toMap(LabelValue::formatValue, LabelValue::getText));
    label.defaultValue = labelType.getDefaultValue();
    label.branches = labelType.getRefPatterns() != null ? labelType.getRefPatterns() : null;
    label.canOverride = toBoolean(labelType.isCanOverride());
    label.copyAnyScore = toBoolean(labelType.isCopyAnyScore());
    label.copyMinScore = toBoolean(labelType.isCopyMinScore());
    label.copyMaxScore = toBoolean(labelType.isCopyMaxScore());
    label.copyAllScoresIfNoChange = toBoolean(labelType.isCopyAllScoresIfNoChange());
    label.copyAllScoresIfNoCodeChange = toBoolean(labelType.isCopyAllScoresIfNoCodeChange());
    label.copyAllScoresOnTrivialRebase = toBoolean(labelType.isCopyAllScoresOnTrivialRebase());
    label.copyAllScoresOnMergeFirstParentUpdate =
        toBoolean(labelType.isCopyAllScoresOnMergeFirstParentUpdate());
    label.copyValues = labelType.getCopyValues().isEmpty() ? null : labelType.getCopyValues();
    label.allowPostSubmit = toBoolean(labelType.isAllowPostSubmit());
    label.ignoreSelfApproval = toBoolean(labelType.isIgnoreSelfApproval());
    return label;
  }

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }

  private LabelDefinitionJson() {}
}
