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

public final class FixReplacement {
  public final String path;
  public final Comment.Range range;
  public final String replacement;

  public FixReplacement(String path, Comment.Range range, String replacement) {
    this.path = path;
    this.range = range;
    this.replacement = replacement;
  }

  @Override
  public String toString() {
    return "FixReplacement{"
        + "path='"
        + path
        + '\''
        + ", range="
        + range
        + ", replacement='"
        + replacement
        + '\''
        + '}';
  }

  /** Returns this instance's approximate size in bytes for the purpose of applying size limits. */
  int getApproximateSize() {
    return path.length() + replacement.length();
  }
}
