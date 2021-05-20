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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.StorageConstraint;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A utility class for different operations related to {@link
 * com.google.gerrit.entities.SubmitRequirement}s.
 */
@Singleton
public class SubmitRequirementsUtil {

  @Singleton
  static class Metrics {
    final Counter2<String, String> submitRequirementsMatchingWithLegacy;
    final Counter2<String, String> submitRequirementsMismatchingWithLegacy;
    final Counter2<String, String> legacyNotInSrs;
    final Counter2<String, String> srsNotInLegacy;

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
      legacyNotInSrs =
          metricMaker.newCounter(
              "change/submit_requirements/legacy_not_in_srs",
              new Description(
                      "Total number of times there was a legacy submit requirement result "
                          + "but not a project config requirement with the same name for a change.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("project", Metadata.Builder::projectName).build(),
              Field.ofString("sr_name", Metadata.Builder::submitRequirementName)
                  .description("Submit requirement name")
                  .build());
      srsNotInLegacy =
          metricMaker.newCounter(
              "change/submit_requirements/srs_not_in_legacy",
              new Description(
                      "Total number of times there was a project config submit requirement "
                          + "result but not a legacy requirement with the same name for a change.")
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
  public ImmutableMap<SubmitRequirement, SubmitRequirementResult>
      mergeLegacyAndNonLegacyRequirements(
          Map<SubmitRequirement, SubmitRequirementResult> projectConfigRequirements,
          Map<SubmitRequirement, SubmitRequirementResult> legacyRequirements,
          ChangeData cd) {
    // Cannot use ImmutableMap.Builder here since entries in the map may be overridden.
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
      // If there's no project config requirement with the same name as the legacy requirement
      // then add the legacy SR to the result. There is no mismatch in results in this case.
      if (projectConfigResult == null) {
        result.put(legacy.getKey(), legacy.getValue());
        if (shouldReportMetric(cd)) {
          metrics.legacyNotInSrs.increment(cd.project().get(), srName);
        }
        continue;
      }
      if (matchByStatus(projectConfigResult, legacyResult)) {
        // There exists a project config SR with the same name as the legacy SR, and they are
        // matching in result. No need to include the legacy SR in the output since the project
        // config SR is already there.
        if (shouldReportMetric(cd)) {
          metrics.submitRequirementsMatchingWithLegacy.increment(cd.project().get(), srName);
        }
        continue;
      }
      // There exists a project config SR with the same name as the legacy SR but they are not
      // matching in their result. Increment the mismatch count and add the legacy SR to the result.
      if (shouldReportMetric(cd)) {
        metrics.submitRequirementsMismatchingWithLegacy.increment(cd.project().get(), srName);
      }
      result.put(legacy.getKey(), legacy.getValue());
    }
    Set<String> legacyNames =
        legacyRequirements.keySet().stream()
            .map(SubmitRequirement::name)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    for (String projectConfigSrName : requirementsByName.keySet()) {
      if (!legacyNames.contains(projectConfigSrName) && shouldReportMetric(cd)) {
        metrics.srsNotInLegacy.increment(cd.project().get(), projectConfigSrName);
      }
    }

    return ImmutableMap.copyOf(result);
  }

  /** Validates the name of submit requirements. */
  public static void validateName(@Nullable String name) throws IllegalArgumentException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Empty submit requirement name");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((i == 0 && c == '-')
          || !((c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '-')) {
        throw new IllegalArgumentException(
            String.format(
                "Illegal submit requirement name \"%s\". Name can only consist of "
                    + "alphanumeric characters and -",
                name));
      }
    }
  }

  private static boolean shouldReportMetric(ChangeData cd) {
    // We only care about recording differences in old and new requirements for open changes
    // that did not have their data retrieved from the (potentially stale) change index.
    return cd.change().isNew() && cd.getStorageConstraint() == StorageConstraint.NOTEDB_ONLY;
  }

  /** Returns true if both input results are equal in allowing/disallowing change submission. */
  private static boolean matchByStatus(SubmitRequirementResult r1, SubmitRequirementResult r2) {
    return r1.fulfilled() == r2.fulfilled();
  }
}
