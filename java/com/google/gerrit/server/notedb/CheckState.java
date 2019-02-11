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

import static java.util.Comparator.naturalOrder;

import java.util.Arrays;
import java.util.Locale;

/**
 * State of checks on a change.
 *
 * <p>The state may apply to a single check on a change, or it may be an aggregate state combining
 * the state of multiple checks into a single aggregate state.
 *
 * <p>When aggregating multiple states, the lowest valued state is used, according to the natural
 * order. Aggregating the empty set results in {@link #NOT_RELEVANT}.
 *
 * <p>There are no specified transitions between states; checker implementations can update the
 * state for checks at will.
 */
public enum CheckState {
  /** The check terminated and failed. */
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
  NOT_RELEVANT;

  public static CheckState parse(String value) {
    return valueOf(value.toUpperCase(Locale.US));
  }

  public static CheckState combine(CheckState... states) {
    return Arrays.stream(states).min(naturalOrder()).orElse(NOT_RELEVANT);
  }
}
