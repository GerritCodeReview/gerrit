// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import java.util.Objects;

/** Utility class to compare nullable {@link Boolean} preferences fields. */
public class NullableBooleanComparator {

  /**
   * Compare 2 nullable {@link Boolean} preferences fields, regard to {@code null} as {@code false}.
   * In case of no user specified value and a {@code false} default, {@link
   * com.google.gerrit.server.config.ConfigUtil#loadSection} sets the result field value to {@code
   * null}. When reading the values, the readers always check whether the value is {@code true},
   * practically referring to {@code null} values as {@code false} anyway.
   */
  static boolean equalBooleanPreferencesFields(@Nullable Boolean a, @Nullable Boolean b) {
    return Objects.equals(
        Objects.requireNonNullElse(a, false), Objects.requireNonNullElse(b, false));
  }
}
