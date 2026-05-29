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

package com.google.gerrit.server.project;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.inject.Singleton;

/** Converts a {@link SubmitRequirement} to a {@link SubmitRequirementInfo}. */
@Singleton
public class SubmitRequirementJson {
  /**
   * Formats the given {@link SubmitRequirement} as a {@link SubmitRequirementInfo}.
   *
   * @param projectName the name of the project that defines the submit requirement, {@code null} if
   *     the submit requirement is globally defined (by implementing the {@link SubmitRequirement}
   *     extension point)
   * @param sr the submit requirement that should be formatted
   */
  public static SubmitRequirementInfo format(
      @Nullable Project.NameKey projectName, SubmitRequirement sr) {
    SubmitRequirementInfo info = new SubmitRequirementInfo();
    info.name = sr.name();
    info.projectName = projectName != null ? projectName.get() : null;
    info.description = sr.description().orElse(null);
    if (sr.applicabilityExpression().isPresent()) {
      info.applicabilityExpression = sr.applicabilityExpression().get().expressionString();
    }
    if (sr.overrideExpression().isPresent()) {
      info.overrideExpression = sr.overrideExpression().get().expressionString();
    }
    info.submittabilityExpression = sr.submittabilityExpression().expressionString();
    info.allowOverrideInChildProjects = sr.allowOverrideInChildProjects();
    return info;
  }
}
