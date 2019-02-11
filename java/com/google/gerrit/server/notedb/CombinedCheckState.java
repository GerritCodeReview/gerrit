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

package com.google.gerrit.server.notedb;

import com.google.common.collect.ImmutableListMultimap;
import java.util.Map;

/**
 * Combined state of multiple checks on a change.
 *
 * <p>This state combines multiple {@link CheckState}s together with the required/optional bit
 * associated with each check.
 *
 * <p>Ordering is not significant in this class, but for consistency's sake the ordering matches
 * {@code CheckState} where applicable.
 */
public enum CombinedCheckState {
  /** All relevant checks terminated, and at least one required check failed. */
  FAILED,

  /**
   * All relevant checks terminated, and at least one optional check failed, but no required checks
   * failed.
   */
  WARNING,

  /**
   * At least one relevant check is in a non-terminated state ({@code CheckState#NOT_STARTED},
   * {@code CheckState#SCHEDULED}, {@code CheckState#RUNNING}). Some checks may have terminated,
   * whether successfully or unsuccessfully.
   */
  IN_PROGRESS,

  /** All relevant checks terminated successfully. */
  SUCCESSFUL,

  /** No checks are relevant to this change. */
  NOT_RELEVANT;

  public static CombinedCheckState combine(
      ImmutableListMultimap<CheckState, Boolean> statesAndRequired) {
    int failedRequiredCount = 0;
    int failedOptionalCount = 0;
    int relevantCount = 0;
    for (Map.Entry<CheckState, Boolean> e : statesAndRequired.entries()) {
      CheckState state = e.getKey();
      switch (state) {
        case NOT_STARTED:
        case SCHEDULED:
        case RUNNING:
          return IN_PROGRESS;
        case FAILED:
          if (e.getValue()) {
            failedRequiredCount++;
          } else {
            failedOptionalCount++;
          }
          break;
        case SUCCESSFUL:
          relevantCount++;
          break;
        case NOT_RELEVANT:
          break;
        default:
          throw new IllegalStateException("invalid state: " + state);
      }
    }
    if (failedRequiredCount > 0) {
      return FAILED;
    }
    if (failedOptionalCount > 0) {
      return WARNING;
    }
    if (relevantCount > 0) {
      return SUCCESSFUL;
    }
    return NOT_RELEVANT;
  }
}
