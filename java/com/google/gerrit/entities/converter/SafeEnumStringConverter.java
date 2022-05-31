// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.entities.converter;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.Iterables;
import java.util.EnumSet;
import java.util.Set;

/**
 * Converts a string to an enum value with the same name. If the string is not the name of any enum
 * constant in the specified enum, then the first value is returned as a fallback.
 */
public class SafeEnumStringConverter<T extends Enum<T>> {
  private Converter<String, T> enumConverter;
  private Set<T> values;

  public SafeEnumStringConverter(Class<T> t) {
    this.enumConverter = Enums.stringConverter(t);
    this.values = EnumSet.allOf(t);
  }

  public T convert(String value) {
    try {
      return enumConverter.convert(value);
    } catch (IllegalArgumentException e) {
      return Iterables.getFirst(values, null);
    }
  }

  public String reverseConvert(T t) {
    return enumConverter.reverse().convert(t);
  }
}
