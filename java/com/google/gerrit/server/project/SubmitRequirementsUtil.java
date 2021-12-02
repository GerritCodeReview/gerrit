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

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A utility class for different operations related to {@link
 * com.google.gerrit.entities.SubmitRequirement}s.
 */
@Singleton
public class SubmitRequirementsUtil {

  @Singleton
  static class Metrics {
    final Counter2 submitRequirementsMatchingWithLegacy;
    final Counter2 submitRequirementsMismatchingWithLegacy;

    @Inject
    Metrics(MetricMaker metricMaker) {
      submitRequirementsMatchingWithLegacy =
          metricMaker.newCounter(
              "change/submit_requirements/matching_with_legacy",
              new Description(
                      "Total number of times there was a legacy and non-legacy "
                          + "submit requirements with the same name for a change, "
                          + "and the evaluation of both requirements had the same result "
                          + "w.r.t. change submittability.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("project", Metadata.Builder::projectName).build(),
              Field.ofString("sr_name", Metadata.Builder::submitRequirementName)
                  .description("Submit requirement name")
                  .build());
      submitRequirementsMismatchingWithLegacy =
          metricMaker.newCounter(
              "change/submit_requirements/mismatching_with_legacy",
              new Description(
                      "Total number of times there was a legacy and non-legacy "
                          + "submit requirements with the same name for a change, "
                          + "and the evaluation of both requirements had a different result "
                          + "w.r.t. change submittability.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("project", Metadata.Builder::projectName).build(),
              Field.ofString("sr_name", Metadata.Builder::submitRequirementName)
                  .description("Submit requirement name")
                  .build());
    }
  }

  private final Metrics metrics;

  @Inject
  public SubmitRequirementsUtil(Metrics metrics) {
    this.metrics = metrics;
  }

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
  public Map<SubmitRequirement, SubmitRequirementResult> mergeLegacyAndNonLegacyRequirements(
      Map<SubmitRequirement, SubmitRequirementResult> projectConfigRequirements,
      Map<SubmitRequirement, SubmitRequirementResult> legacyRequirements,
      Project.NameKey project) {
    Map<SubmitRequirement, SubmitRequirementResult> result = new HashMap<>();
    result.putAll(projectConfigRequirements);
    Map<String, SubmitRequirementResult> requirementsByName =
        projectConfigRequirements.entrySet().stream()
            .collect(Collectors.toMap(sr -> sr.getKey().name().toLowerCase(), sr -> sr.getValue()));
    for (Map.Entry<SubmitRequirement, SubmitRequirementResult> legacy :
        legacyRequirements.entrySet()) {
      String srName = legacy.getKey().name().toLowerCase();
      SubmitRequirementResult projectConfigResult = requirementsByName.get(srName);
      SubmitRequirementResult legacyResult = legacy.getValue();
      if (projectConfigResult != null && matchByStatus(projectConfigResult, legacyResult)) {
        metrics.submitRequirementsMatchingWithLegacy.increment(project.get(), srName);
        continue;
      }
      metrics.submitRequirementsMismatchingWithLegacy.increment(project.get(), srName);
      result.put(legacy.getKey(), legacy.getValue());
    }
    return result;
  }

  /** Returns true if both input results are equal in allowing/disallowing change submission. */
  private static boolean matchByStatus(SubmitRequirementResult r1, SubmitRequirementResult r2) {
    return r1.fulfilled() == r2.fulfilled();
  }
}
