// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

/**
 * Controls how submitting a change with a topic behaves.
 *
 * <p>Corresponds to {@code change.submitWholeTopic} in {@code gerrit.config}.
 *
 * <p>Backward compatibility: the historical boolean values {@code false} and {@code true} map to
 * {@link #DISABLED} and {@link #ENFORCED} respectively.
 */
public enum SubmitWholeTopicMode {
  /**
   * Submit only the current change. The "submit whole topic" action is not offered via the UI or
   * the REST API.
   *
   * <p>This is the default and corresponds to the legacy {@code false} value.
   */
  DISABLED,

  /**
   * Submitting a change always submits all changes sharing its topic. The "submit whole topic"
   * action is not separately offered because every submit already implies it.
   *
   * <p>Corresponds to the legacy {@code true} value.
   */
  ENFORCED,

  /**
   * The "submit whole topic" action is available as an explicit opt-in via the UI button and the
   * {@code POST /changes/{id}/submit.topic} REST endpoint, but submitting a single change does
   * <em>not</em> automatically pull in the rest of the topic.
   */
  OPTIONAL;

  /**
   * Returns the {@link SubmitWholeTopicMode} that corresponds to the legacy boolean config value.
   *
   * <p>Use this when reading a plain {@code true}/{@code false} from {@code gerrit.config} to
   * preserve backward compatibility.
   */
  public static SubmitWholeTopicMode fromBoolean(boolean value) {
    return value ? ENFORCED : DISABLED;
  }

  /**
   * Parses {@code submitWholeTopic} from a raw string config value, preserving backward
   * compatibility with the legacy boolean strings {@code "true"} and {@code "false"}.
   *
   * @param value the raw string from {@code gerrit.config}, may be {@code null}
   * @return the matching {@link SubmitWholeTopicMode}, defaulting to {@link #DISABLED} for {@code
   *     null} or unrecognised values
   */
  public static SubmitWholeTopicMode parse(String value) {
    if (value == null) {
      return DISABLED;
    }
    switch (value.trim().toLowerCase()) {
      case "true":
        return ENFORCED;
      case "false":
        return DISABLED;
      default:
        for (SubmitWholeTopicMode mode : values()) {
          if (mode.name().equalsIgnoreCase(value.trim())) {
            return mode;
          }
        }
        return DISABLED;
    }
  }
}
