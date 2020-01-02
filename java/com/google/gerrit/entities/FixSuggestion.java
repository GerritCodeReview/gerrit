// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.entities;

import java.util.List;

public final class FixSuggestion {
  public final String fixId;
  public final String description;
  public final List<FixReplacement> replacements;

  public FixSuggestion(String fixId, String description, List<FixReplacement> replacements) {
    this.fixId = fixId;
    this.description = description;
    this.replacements = replacements;
  }

  @Override
  public String toString() {
    return "FixSuggestion{"
        + "fixId='"
        + fixId
        + '\''
        + ", description='"
        + description
        + '\''
        + ", replacements="
        + replacements
        + '}';
  }

  /** Returns this instance's approximate size in bytes for the purpose of applying size limits. */
  int getApproximateSize() {
    return fixId.length()
        + description.length()
        + replacements.stream().map(FixReplacement::getApproximateSize).reduce(0, Integer::sum);
  }
}
