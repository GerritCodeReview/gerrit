// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.metrics;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/** Describes a bucketing field used by a metric. */
public class Field<T> {
  /** Break down metrics by string. */
  public static Field<String> ofString(String name) {
    return new Field<>(name, String.class);
  }

  /** Break down metrics by cases of an enum. */
  public static <E extends Enum<E>> Field<E> ofEnum(Class<E> enumType, String name) {
    return new Field<>(name, enumType);
  }

  private final String name;
  private final Class<T> keyType;

  private Field(String name, Class<T> keyType) {
    this.name = name;
    this.keyType = keyType;
  }

  /** Name of this field within the metric. */
  public String getName() {
    return name;
  }

  /** Type of value used within the field. */
  public Class<T> getType() {
    return keyType;
  }

  @SuppressWarnings("unchecked")
  public Function<T, String> formatter() {
    if (keyType == String.class) {
      return (Function<T, String>) Functions.<String>identity();
    } else if (Enum.class.isAssignableFrom(keyType)) {
      return new Function<T, String>() {
        @Override
        public String apply(T in) {
          return ((Enum<?>)in).name();
        }
      };
    }
    throw new IllegalStateException("unsupported type " + keyType.getName());
  }
}
