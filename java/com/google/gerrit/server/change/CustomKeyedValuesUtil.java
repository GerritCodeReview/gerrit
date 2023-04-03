// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;

public class CustomKeyedValuesUtil {
  public static class InvalidCustomKeyedValueException extends Exception {
    private static final long serialVersionUID = 1L;

    static InvalidCustomKeyedValueException customKeyedValuesMayNotContainEquals() {
      return new InvalidCustomKeyedValueException("custom keys may not contain equals sign");
    }

    static InvalidCustomKeyedValueException customKeyedValuesMayNotContainNewLine() {
      return new InvalidCustomKeyedValueException("custom values may not contain newline");
    }

    InvalidCustomKeyedValueException(String message) {
      super(message);
    }
  }

  static ImmutableMap<String, String> extractCustomKeyedValues(ImmutableMap<String, String> input)
      throws InvalidCustomKeyedValueException {
    if (input == null) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> customKeyedValue : input.entrySet()) {
      if (customKeyedValue.getKey().contains("=")) {
        throw InvalidCustomKeyedValueException.customKeyedValuesMayNotContainEquals();
      }
      if (customKeyedValue.getValue().contains("\n")) {
        throw InvalidCustomKeyedValueException.customKeyedValuesMayNotContainNewLine();
      }
      String key = customKeyedValue.getKey().trim();
      if (key.isEmpty()) {
        continue;
      }
      builder.put(key, customKeyedValue.getValue());
    }
    return builder.build();
  }

  static ImmutableSet<String> extractCustomKeys(ImmutableSet<String> input)
      throws InvalidCustomKeyedValueException {
    if (input == null) {
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (String customKey : input) {
      if (customKey.contains("=")) {
        throw InvalidCustomKeyedValueException.customKeyedValuesMayNotContainEquals();
      }
      String key = customKey.trim();
      if (key.isEmpty()) {
        continue;
      }
      builder.add(key);
    }
    return builder.build();
  }

  private CustomKeyedValuesUtil() {}
}
