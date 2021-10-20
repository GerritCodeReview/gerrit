// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A utility class for different operations related to {@link
 * com.google.gerrit.entities.SubmitRequirement}s.
 */
public class SubmitRequirementsUtil {

  private SubmitRequirementsUtil() {}

  /**
   * Merge legacy and non-legacy submit requirement results. If both input maps have submit
   * requirements with the same name and fulfillment status (according to {@link
   * SubmitRequirementResult#fulfilled()}), we eliminate the entry from the {@code
   * legacyRequirements} input map and only include the one from the {@code
   * projectConfigRequirements} in the result.
   *
   * @param projectConfigRequirements map of {@link SubmitRequirement} to {@link
   *     SubmitRequirementResult} containing results for submit requirements stored in the
   *     project.config.
   * @param legacyRequirements map of {@link SubmitRequirement} to {@link SubmitRequirementResult}
   *     containing the results of converting legacy submit records to submit requirements.
   * @return a map that is the result of merging both input maps, while eliminating requirements
   *     with the same name and status.
   */
  public static Map<SubmitRequirement, SubmitRequirementResult> mergeLegacyAndNonLegacyRequirements(
      Map<SubmitRequirement, SubmitRequirementResult> projectConfigRequirements,
      Map<SubmitRequirement, SubmitRequirementResult> legacyRequirements) {
    Map<SubmitRequirement, SubmitRequirementResult> result = new HashMap<>();
    result.putAll(projectConfigRequirements);
    Map<String, SubmitRequirementResult> requirementsByName =
        projectConfigRequirements.entrySet().stream()
            .collect(Collectors.toMap(sr -> sr.getKey().name().toLowerCase(), sr -> sr.getValue()));
    for (Map.Entry<SubmitRequirement, SubmitRequirementResult> legacy :
        legacyRequirements.entrySet()) {
      String name = legacy.getKey().name().toLowerCase();
      SubmitRequirementResult projectConfigResult = requirementsByName.get(name);
      SubmitRequirementResult legacyResult = legacy.getValue();
      if (projectConfigResult != null && matchByStatus(projectConfigResult, legacyResult)) {
        continue;
      }
      result.put(legacy.getKey(), legacy.getValue());
    }
    return result;
  }

  /** Returns true if both input results are equal in allowing/disallowing change submission. */
  private static boolean matchByStatus(SubmitRequirementResult r1, SubmitRequirementResult r2) {
    return r1.fulfilled() == r2.fulfilled();
  }
}
