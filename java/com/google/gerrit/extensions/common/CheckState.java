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

package com.google.gerrit.extensions.common;

/**
 * State of a single check on a change.
 *
 * <p>This state applies to a single check; for the aggregated state associated with a change, see
 * {@link CombinedCheckState}.
 *
 * <p>Ordering is not significant in this class, but for consistency's sake the ordering matches
 * {@code CombinedCheckState} where applicable.
 */
public enum CheckState {
  /**
   * The check terminated and failed.
   *
   * <p>Failure may include the following cases:
   *
   * <ul>
   *   <li>The check completed normally and found a problem with the code in the change.
   *   <li>The check failed to start.
   *   <li>The check started, but failed for some reason not directly related to the code, such as a
   *       setup failure in the checker.
   * </ul>
   */
  FAILED,

  /** The check is relevant to the change, but the checker has not started work. */
  NOT_STARTED,

  /**
   * The checker has acknowledged that it has work to do on the change, and will start work in the
   * future.
   */
  SCHEDULED,

  /** The checker is currently running the check. */
  RUNNING,

  /** The check terminated and succeeded. */
  SUCCESSFUL,

  /** The check is not relevant for the change. */
  NOT_RELEVANT
}
