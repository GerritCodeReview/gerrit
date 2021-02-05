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

package com.google.gerrit.server.experiments;

import com.google.common.collect.ImmutableSet;

/**
 * Features that can be enabled/disabled on Gerrit (e. g. experiments to research new behavior in
 * the current release).
 *
 * <p>It may depend on the implementation if the result is decided on the per-request basis or not,
 * so the outcomes should not be persisted in {@link @Singleton}.
 */
public interface ExperimentFeatures {

  /**
   * Given the name of the feature, returns if it is enabled on the Gerrit server.
   *
   * <p>Depend on the implementation, it can be more efficient than filtering the results of {@link
   * ExperimentFeatures#getEnabledExperimentFeatures}.
   *
   * @param featureFlag the name of the feature to test.
   * @return if the feature is enabled.
   */
  boolean isFeQatureEnabled(String featureFlag);

  /**
   * Returns the names of the features that are enabled on Gerrit instance (either by default or via
   * gerrit.config).
   */
  ImmutableSet<String> getEnabledExperimentFeatures();
}
