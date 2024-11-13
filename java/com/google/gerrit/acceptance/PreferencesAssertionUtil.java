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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.extensions.client.NullableBooleanPreferencesFieldComparator.equalBooleanPreferencesFields;
import static com.google.gerrit.server.config.ConfigUtil.skipField;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Utility class for preferences assertion. */
public class PreferencesAssertionUtil {
  /** Asserts preferences classes equality, ignoring the specified fields. */
  public static <T> void assertPrefs(T actual, T expected, String... fieldsToExclude)
      throws IllegalArgumentException, IllegalAccessException {
    Set<String> excludedFields = new HashSet<>(Arrays.asList(fieldsToExclude));
    for (Field field : actual.getClass().getDeclaredFields()) {
      if (excludedFields.contains(field.getName()) || skipField(field)) {
        continue;
      }
      Object actualVal = field.get(actual);
      Object expectedVal = field.get(expected);
      if (field.getType().isAssignableFrom(Boolean.class)) {
        assertWithMessage("%s [actual: %s, expected: %s]", field.getName(), actualVal, expectedVal)
            .that(equalBooleanPreferencesFields((Boolean) expectedVal, (Boolean) actualVal))
            .isTrue();
        continue;
      }
      assertWithMessage(field.getName()).that(actualVal).isEqualTo(expectedVal);
    }
  }
}
