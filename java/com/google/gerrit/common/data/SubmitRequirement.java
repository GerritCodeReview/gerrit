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
import java.util.Optional;

/** Describes a requirement to submit a change. */
public class SubmitRequirement {
  private final String shortReason;
  private final String fullReason;
  @Nullable private final String label;

  private SubmitRequirement(String shortReason, String fullReason, String label) {
    this.shortReason = requireNonNull(shortReason);
    this.fullReason = requireNonNull(fullReason);
    this.label = label;
  }

  public static SubmitRequirement create(
      String shortReason, String fullReason, @Nullable String label) {
    return new SubmitRequirement(shortReason, fullReason, label);
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
}
