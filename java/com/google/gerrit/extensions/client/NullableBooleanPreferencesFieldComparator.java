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

/**
 * Utility class to compare nullable {@link Boolean} preferences fields.
 *
 * <p>This class only meant to be used for comparing preferences fields that are potentially loaded
 * using {@link com.google.gerrit.server.config.ConfigUtil} (such as {@link GeneralPreferencesInfo},
 * {@link DiffPreferencesInfo} and {@link EditPreferencesInfo}).
 */
public class NullableBooleanPreferencesFieldComparator {

  /**
   * Compare 2 nullable {@link Boolean} preferences fields, regard to {@code null} as {@code false}.
   *
   * <p>{@link com.google.gerrit.server.config.ConfigUtil#loadSection} sets the following values for
   * Boolean fields, relating to {@code null} as {@code false} the same way:
   *
   * <table>
   *   <tr><th> user-def </th> <th> default </th> <th> result </th></tr>
   *   <tr><td> true </td> <td> true </td> <td> true </td></tr>
   *   <tr><td> true </td> <td> false </td> <td> true </td></tr>
   *   <tr><td> true </td> <td> null </td> <td> true </td></tr>
   *   <tr><td> false </td> <td> true </td> <td> false </td></tr>
   *   <tr><td> false </td> <td> false </td> <td> null </td></tr>
   *   <tr><td> false </td> <td> null </td> <td> null </td></tr>
   *   <tr><td> null </td> <td> true </td> <td> true </td></tr>
   *   <tr><td> null </td> <td> false </td> <td> null </td></tr>
   *   <tr><td> null </td> <td> null </td> <td> null </td></tr>
   * </table>
   *
   * When reading the values, the readers always check whether the value is {@code true},
   * practically referring to {@code null} values as {@code false} anyway. Preferences equality
   * methods should reflect this state.
   */
  public static boolean equalBooleanPreferencesFields(@Nullable Boolean a, @Nullable Boolean b) {
    return Objects.equals(
        Objects.requireNonNullElse(a, false), Objects.requireNonNullElse(b, false));
  }
}
