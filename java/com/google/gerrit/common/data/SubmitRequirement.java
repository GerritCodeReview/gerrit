// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.common.data;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Describes a requirement to submit a change. */
public final class SubmitRequirement {
  private static final Predicate<String> TYPE_MATCHER =
      Pattern.compile("^([a-zA-Z0-9\\-_]+)$").asPredicate();

  private final String fallbackText;
  private final String type;
  private final Map<String, String> data;

  public SubmitRequirement(String fallbackText, String type, Map<String, String> data) {
    this.fallbackText = requireNonNull(fallbackText);
    this.type = requireNonNull(type);
    this.data = requireNonNull(data);

    checkTypeName(type);
  }

  public String fallbackText() {
    return fallbackText;
  }

  public String type() {
    return type;
  }

  public Map<String, String> data() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubmitRequirement)) {
      return false;
    }
    SubmitRequirement that = (SubmitRequirement) o;
    return Objects.equals(fallbackText, that.fallbackText)
        && Objects.equals(type, that.type)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fallbackText, type, data);
  }

  @Override
  public String toString() {
    return "SubmitRequirement{"
        + "fallbackText='"
        + fallbackText
        + '\''
        + ", type='"
        + type
        + '\''
        + ", data="
        + data
        + '}';
  }

  private static void checkTypeName(String type) {
    if (!TYPE_MATCHER.test(type)) {
      throw new RuntimeException("SubmitRequirement's type contains non alphanumerical symbols.");
    }
  }
}
