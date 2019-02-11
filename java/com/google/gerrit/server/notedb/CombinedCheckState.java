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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
  /** At least one required check failed; other checks may have passed, or still be running. */
  FAILED,

  /**
   * All relevant checks terminated, and at least one optional check failed, but no required checks
   * failed.
   */
  WARNING,

  /**
   * At least one relevant check is in a non-terminated state ({@link CheckState#NOT_STARTED},
   * {@link CheckState#SCHEDULED}, {@link CheckState#RUNNING}), and no required checks failed. Some
   * optional checks may have failed.
   */
  IN_PROGRESS,

  /** All relevant checks terminated successfully. */
  SUCCESSFUL,

  /** No checks are relevant to this change. */
  NOT_RELEVANT;

  private static final ImmutableMap<String, CombinedCheckState> STATES;

  static {
    Map<String, CombinedCheckState> states = new HashMap<>();
    for (CombinedCheckState state : values()) {
      String name = state.name().toLowerCase(Locale.US);
      states.put(name, state);
      states.put(CharMatcher.is('_').removeFrom(name), state);
    }
    STATES = ImmutableMap.copyOf(states);
  }

  /**
   * Parses the given string to a state, throwing {@link IllegalArgumentException} if no match is
   * found.
   *
   * @see #tryParse(String)
   */
  public static CombinedCheckState parse(String value) {
    return tryParse(value)
        .orElseThrow(() -> new IllegalArgumentException("invalid CombinedCheckState: " + value));
  }

  /**
   * Attempts to parse the given string to a state.
   *
   * <p>The input string is case-insensitive and may or may not contain underscores. For example,
   * all of {@code FOO_BAR_BAZ}, {@code foo_bar_baz}, {@code FOOBARBAZ}, and {@code foobarbaz}
   * resolve to an enum value named {@code FOO_BAR_BAZ}; but {@code FOO_BARBAZ} and {@code
   * FO_OBAR_BAZ} do not.
   *
   * @param value input string; must not be null.
   * @return optional containing the state corresponding to {@code value}, or empty if none exists.
   */
  public static Optional<CombinedCheckState> tryParse(String value) {
    // Allow "not_relevant" and "notrelevant", but exclude "not_rel_evant".
    return Optional.ofNullable(STATES.get(value.toLowerCase(Locale.US)));
  }

  /**
   * Combines multiple per-check states into a single combined state.
   *
   * <p>See documentation of specific enum values for precise semantics.
   *
   * @param statesAndRequired map of state to a list of booleans, one per check, indicating whether
   *     that particular check is required in the context of a particular change.
   * @return combined state.
   */
  public static CombinedCheckState combine(
      ImmutableListMultimap<CheckState, Boolean> statesAndRequired) {
    int inProgressCount = 0;
    int failedOptionalCount = 0;
    int successfulCount = 0;
    for (Map.Entry<CheckState, Boolean> e : statesAndRequired.entries()) {
      CheckState state = e.getKey();
      switch (state) {
        case NOT_STARTED:
        case SCHEDULED:
        case RUNNING:
          inProgressCount++;
          break;
        case FAILED:
          if (e.getValue()) {
            return CombinedCheckState.FAILED;
          } else {
            failedOptionalCount++;
          }
          break;
        case SUCCESSFUL:
          successfulCount++;
          break;
        case NOT_RELEVANT:
          break;
        default:
          throw new IllegalStateException("invalid state: " + state);
      }
    }
    if (inProgressCount > 0) {
      return IN_PROGRESS;
    }
    if (failedOptionalCount > 0) {
      return WARNING;
    }
    if (successfulCount > 0) {
      return SUCCESSFUL;
    }
    return NOT_RELEVANT;
  }

  public String toIndexString() {
    return CharMatcher.is('_').removeFrom(name().toLowerCase(Locale.US));
  }
}
