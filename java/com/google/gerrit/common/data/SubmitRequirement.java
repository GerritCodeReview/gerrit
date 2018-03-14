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

import com.google.gerrit.common.Nullable;
import java.util.Objects;
import java.util.Optional;

/** Describes a requirement to submit a change. */
public final class SubmitRequirement {
  private final String shortReason;
  private final String fullReason;
  @Nullable private final String label;

  public SubmitRequirement(String shortReason, String fullReason, @Nullable String label) {
    this.shortReason = requireNonNull(shortReason);
    this.fullReason = requireNonNull(fullReason);
    this.label = label;
  }

  public String shortReason() {
    return shortReason;
  }

  public String fullReason() {
    return fullReason;
  }

  public Optional<String> label() {
    return Optional.ofNullable(label);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof SubmitRequirement) {
      SubmitRequirement that = (SubmitRequirement) o;
      return Objects.equals(shortReason, that.shortReason)
          && Objects.equals(fullReason, that.fullReason)
          && Objects.equals(label, that.label);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(shortReason, fullReason, label);
  }

  @Override
  public String toString() {
    return "SubmitRequirement{"
        + "shortReason='"
        + shortReason
        + '\''
        + ", fullReason='"
        + fullReason
        + '\''
        + ", label='"
        + label
        + '\''
        + '}';
  }
}
